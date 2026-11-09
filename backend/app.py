"""Loopback development gateway. Never log requests, headers, upstream URLs or bodies."""
import asyncio
from collections import deque
from dataclasses import dataclass, field
import json
import logging
import os
import re
import secrets
import threading
import time
from typing import Literal

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
import httpx
from pydantic import BaseModel, ConfigDict, Field, model_validator

UPSTREAM = "https://apis-navi.kakaomobility.com/v1/directions"
MAX_RESPONSE = 2_000_000


@dataclass(frozen=True)
class Settings:
    api_key: str = field(default="", repr=False)
    token: str = field(default="", repr=False)
    mode: str = "live"
    requests_per_minute: int = 30
    max_requests: int = 100
    expires_at_ms: int = field(default_factory=lambda: int(time.time() * 1000) + 3_600_000)

    @classmethod
    def from_env(cls):
        return cls(api_key=os.environ.get("KAKAO_REST_API_KEY", "").strip(),
                   token=os.environ.get("SURPRIDE_PROXY_TOKEN", "").strip(),
                   mode=os.environ.get("SURPRIDE_PROXY_MODE", "live"))


class Point(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    longitude: float = Field(ge=-180, le=180, strict=True)
    latitude: float = Field(ge=-90, le=90, strict=True)


class RouteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    origin: Point
    destination: Point
    include_guidance: bool = Field(default=False, strict=True)
    priority: Literal["RECOMMEND", "TIME", "DISTANCE"] = "RECOMMEND"
    waypoints: list[Point] = Field(default_factory=list, max_length=2, strict=True)

    @model_validator(mode="after")
    def distinct_points(self):
        points = [self.origin, *self.waypoints, self.destination]
        coordinates = {(point.longitude, point.latitude) for point in points}
        if len(coordinates) != len(points):
            raise ValueError("duplicate route point")
        return self


class RouteFailure(Exception):
    def __init__(self, code, status=502):
        self.code, self.status = code, status


class RequestBudget:
    """Single-process shared limit. No per-location/user tracking, no quota retries."""
    def __init__(self, per_minute, maximum):
        self.per_minute, self.maximum = per_minute, maximum
        self.times, self.used = deque(), 0
        self.lock = threading.Lock()

    def accept(self):
        now = time.monotonic()
        with self.lock:
            while self.times and self.times[0] <= now - 60:
                self.times.popleft()
            if len(self.times) >= self.per_minute or self.used >= self.maximum:
                return False
            self.times.append(now)
            self.used += 1
            return True


def integer(value, minimum, maximum):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError("invalid integer")
    return value


def normalized(body, request, source):
    route = body["routes"][0]
    code = integer(route["result_code"], 0, 2_147_483_647)
    if code != 0:
        if code in (1, 105, 106, 107):
            raise RouteFailure("NO_ROUTE", 422)
        if code in (101, 102, 103, 104):
            raise RouteFailure("INVALID_COORDINATE", 422)
        raise RouteFailure("API")
    summary = route["summary"]
    if request.waypoints:
        sections = route["sections"]
        waypoints = summary["waypoints"]
        if not isinstance(sections, list) or len(sections) != len(request.waypoints) + 1:
            raise ValueError("invalid section count")
        if not isinstance(waypoints, list) or len(waypoints) != len(request.waypoints):
            raise ValueError("invalid waypoint summary")
        for actual, expected in zip(waypoints, request.waypoints):
            # Read coordinates only; upstream POI names never enter the contract.
            if Point(longitude=actual["x"], latitude=actual["y"]) != expected:
                raise ValueError("mismatched waypoint summary")
    geometry = []
    for section in route.get("sections", []):
        for road in section.get("roads", []):
            vertices = road["vertexes"]
            if not isinstance(vertices, list) or len(vertices) % 2:
                raise ValueError("invalid geometry")
            for index in range(0, len(vertices), 2):
                x, y = vertices[index:index + 2]
                if type(x) not in (int, float) or type(y) not in (int, float):
                    raise ValueError("invalid geometry")
                point = Point(longitude=x, latitude=y)
                geometry.append([point.longitude, point.latitude])
                if len(geometry) > 100_000:
                    raise ValueError("geometry limit")
    result = {"schema_version": 1, "source": source,
            "origin": request.origin.model_dump(), "destination": request.destination.model_dump(),
            "request_options": {"priority": request.priority,
                                "waypoints": [point.model_dump() for point in request.waypoints]},
            "duration_seconds": integer(summary["duration"], 1, 604800),
            "distance_meters": integer(summary["distance"], 0, 100_000_000),
            "geometry": geometry, "measured_at_epoch_ms": int(time.time() * 1000)}
    if request.include_guidance:
        from backend.guidance import guidance_data
        result["guidance"] = guidance_data(route)
    return result


async def fetch_route(request, settings, transport):
    params = {"origin": f"{request.origin.longitude},{request.origin.latitude}",
              "destination": f"{request.destination.longitude},{request.destination.latitude}",
              "priority": request.priority, "summary": "false", "alternatives": "false"}
    if request.waypoints:
        params["waypoints"] = "|".join(f"{point.longitude},{point.latitude}" for point in request.waypoints)
    try:
        async with asyncio.timeout(10):
            async with httpx.AsyncClient(transport=transport, trust_env=False, follow_redirects=False,
                                        timeout=httpx.Timeout(8, connect=3)) as client:
                async with client.stream("GET", UPSTREAM, params=params,
                                         headers={"Authorization": "KakaoAK " + settings.api_key}) as response:
                    if response.status_code in (401, 403):
                        raise RouteFailure("AUTH", 502)
                    if response.status_code == 429:
                        raise RouteFailure("RATE_LIMIT", 429)
                    if response.status_code not in (200, 400):
                        raise RouteFailure("API")
                    status = response.status_code
                    raw = bytearray()
                    async for chunk in response.aiter_bytes():
                        raw.extend(chunk)
                        if len(raw) > MAX_RESPONSE:
                            raise RouteFailure("INVALID_RESPONSE")
        try:
            body = json.loads(raw)
            if status == 400:
                code = body.get("code") if isinstance(body, dict) else None
                if code == -10:
                    raise RouteFailure("RATE_LIMIT", 429)
                if code in (-401, -903):
                    raise RouteFailure("AUTH", 502)
                if code == -2:
                    raise RouteFailure("INVALID_COORDINATE", 422)
                raise RouteFailure("API")
            return normalized(body, request, "KAKAO_DIRECTIONS")
        except RouteFailure:
            raise
        except (ValueError, TypeError, KeyError, IndexError, OverflowError):
            raise RouteFailure("INVALID_RESPONSE") from None
    except (httpx.HTTPError, TimeoutError):
        raise RouteFailure("NETWORK", 504) from None


def create_app(settings=None, transport=None):
    settings = settings or Settings.from_env()
    if not re.fullmatch(r"[A-Za-z0-9_-]{32,128}", settings.token) or settings.mode not in ("live", "mock"):
        raise ValueError("Configure a strong local proxy token and valid mode before startup")
    if not (1 <= settings.requests_per_minute <= 100 and 1 <= settings.max_requests <= 1000):
        raise ValueError("Invalid development request budget")
    # HTTPX INFO/DEBUG can include upstream URLs (and thus coordinates). Never enable them here.
    logging.getLogger("httpx").disabled = True
    logging.getLogger("httpcore").disabled = True
    budget = RequestBudget(settings.requests_per_minute, settings.max_requests)
    app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None, debug=False)

    @app.middleware("http")
    async def protect(request: Request, call_next):
        supplied = request.headers.get("authorization", "")
        if int(time.time() * 1000) >= settings.expires_at_ms or not secrets.compare_digest(supplied.encode(), ("Bearer " + settings.token).encode()):
            return JSONResponse({"error": "AUTH"}, status_code=401)
        if not budget.accept():
            return JSONResponse({"error": "RATE_LIMIT"}, status_code=429)
        # Bound even chunked bodies before JSON validation. Never echo validation inputs.
        raw = bytearray()
        async for chunk in request.stream():
            raw.extend(chunk)
            if len(raw) > 4096:
                return JSONResponse({"error": "INVALID_RESPONSE"}, status_code=413)
        request._body = bytes(raw)
        try:
            response = await call_next(request)
        except Exception:
            return JSONResponse({"error": "API"}, status_code=500)
        response.headers["Cache-Control"] = "no-store"
        return response

    @app.exception_handler(RequestValidationError)
    async def invalid_request(request, exc):
        return JSONResponse({"error": "INVALID_COORDINATE"}, status_code=422)

    @app.post("/v1/routes")
    async def directions(request: RouteRequest):
        if settings.mode == "live" and not settings.api_key:
            return JSONResponse({"error": "NOT_CONFIGURED"}, status_code=503)
        try:
            if settings.mode == "mock":
                # Pure fixture, deliberately asymmetric. No geography/traffic inference.
                duration = 540 if request.origin.latitude > request.destination.latitude else 780
                fixture = {"routes": [{"result_code": 0, "summary": {"duration": duration, "distance": 4200}}]}
                if request.waypoints:
                    fixture["routes"][0]["summary"]["waypoints"] = [
                        {"x": point.longitude, "y": point.latitude} for point in request.waypoints]
                    fixture["routes"][0]["sections"] = [{} for _ in range(len(request.waypoints) + 1)]
                if request.include_guidance:
                    # Synthetic straight lines only, never a road/traffic inference. Source stays MOCK.
                    points = [request.origin, *request.waypoints, request.destination]
                    count = len(points) - 1
                    section_distance, section_duration = 4200 // count, duration // count
                    sections = []
                    for index, (start, end) in enumerate(zip(points, points[1:])):
                        guides = [{"type": 100, "distance": 0, "road_index": 0}] if index == 0 else []
                        guides.append({"type": 101 if index == count - 1 else 1000,
                                       "distance": section_distance, "road_index": 0})
                        sections.append({"distance": section_distance, "duration": section_duration,
                            "roads": [{"name": "", "distance": section_distance,
                                "duration": section_duration, "traffic_state": 0,
                                "vertexes": [start.longitude, start.latitude, end.longitude, end.latitude]}],
                            "guides": guides})
                    fixture["routes"][0]["sections"] = sections
                return normalized(fixture, request, "MOCK")
            return await fetch_route(request, settings, transport)
        except RouteFailure as exc:
            return JSONResponse({"error": exc.code}, status_code=exc.status)
        except Exception:
            return JSONResponse({"error": "API"}, status_code=502)

    return app
