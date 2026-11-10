"""Synthetic upstream measurements. No TCP/TLS or actual Kakao timing is claimed."""
import asyncio
import json
from pathlib import Path
import time

from fastapi.testclient import TestClient
import httpx
import pytest

from backend.app import Settings, create_app

TOKEN = "synthetic-b5-local-token-0000000000"
HEADERS = {"Authorization": "Bearer " + TOKEN}
POINTS = {"origin": {"longitude": 0.0, "latitude": 0.0},
          "destination": {"longitude": 0.001, "latitude": 0.0}}


def response():
    return httpx.Response(200, json={"routes": [{"result_code": 0,
        "summary": {"duration": 600, "distance": 1000},
        "sections": [{"roads": [{"vertexes": [0.0, 0.0, 0.001, 0.0]}]}]}]})


def test_one_lifespan_owns_one_client_for_eight_sequential_routes(monkeypatch):
    original = httpx.AsyncClient
    created, closed, timings = [], [], []

    class Tracked(original):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, **kwargs)
            created.append(self)

        async def aclose(self):
            if self not in closed:
                closed.append(self)
            await super().aclose()

        async def __aexit__(self, *args):
            if self not in closed:
                closed.append(self)
            await super().__aexit__(*args)

    monkeypatch.setattr(httpx, "AsyncClient", Tracked)
    app = create_app(Settings(api_key="synthetic", token=TOKEN), httpx.MockTransport(lambda _: response()))
    with TestClient(app) as client:
        for _ in range(8):
            start = time.perf_counter_ns()
            assert client.post("/v1/routes", json=POINTS, headers=HEADERS).status_code == 200
            timings.append(time.perf_counter_ns() - start)
        during_close_count = len(closed)
    data = {"transport": "MockTransport; no TCP/TLS", "requests": 8,
            "clients_created": len(created), "closed_during_requests": during_close_count,
            "clients_closed_after_shutdown": len(closed), "request_latency_ns": timings}
    directory = Path(__file__).resolve().parents[2] / ".gradle/phase6-b5"
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "server-current.json").write_text(json.dumps(data, indent=2), encoding="utf-8")
    assert len(created) == 1
    assert during_close_count == 0
    assert len(closed) == 1 and created[0].is_closed
    assert created[0].trust_env is False and created[0].follow_redirects is False
    assert created[0].timeout.connect == 3 and created[0].timeout.read == 8


def test_client_survives_timeout_and_is_closed_at_shutdown():
    calls = 0

    def transport(request):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise httpx.ReadTimeout("synthetic")
        assert request.url.scheme == "https" and request.url.host == "apis-navi.kakaomobility.com"
        return response()

    app = create_app(Settings(api_key="synthetic", token=TOKEN), httpx.MockTransport(transport))
    with TestClient(app) as caller:
        first = caller.post("/v1/routes", json=POINTS, headers=HEADERS)
        assert first.status_code == 504 and first.json() == {"error": "NETWORK"}
        upstream = app.state.upstream_client
        assert not upstream.is_closed
        assert caller.post("/v1/routes", json=POINTS, headers=HEADERS).status_code == 200
        assert app.state.upstream_client is upstream
    assert upstream.is_closed


def test_cancelled_request_retires_stream_but_keeps_shared_client_usable():
    async def scenario():
        entered = asyncio.Event()
        released = asyncio.Event()
        calls = 0

        async def transport(request):
            nonlocal calls
            calls += 1
            if calls == 1:
                entered.set()
                try:
                    await asyncio.sleep(60)
                finally:
                    released.set()
            return response()

        app = create_app(Settings(api_key="synthetic", token=TOKEN), httpx.MockTransport(transport))
        async with app.router.lifespan_context(app):
            upstream = app.state.upstream_client
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app), base_url="http://synthetic") as caller:
                task = asyncio.create_task(caller.post("/v1/routes", json=POINTS, headers=HEADERS))
                await asyncio.wait_for(entered.wait(), timeout=2)
                task.cancel()
                with pytest.raises(asyncio.CancelledError):
                    await task
                await asyncio.wait_for(released.wait(), timeout=2)
                assert not upstream.is_closed
                assert (await caller.post("/v1/routes", json=POINTS, headers=HEADERS)).status_code == 200
        assert upstream.is_closed

    asyncio.run(scenario())


def test_cancellation_after_headers_closes_the_response_body_stream():
    async def scenario():
        entered, closed = asyncio.Event(), asyncio.Event()

        class Body(httpx.AsyncByteStream):
            async def __aiter__(self):
                entered.set()
                yield b'{"routes":'
                await asyncio.sleep(60)

            async def aclose(self):
                closed.set()

        calls = 0

        def transport(request):
            nonlocal calls
            calls += 1
            return httpx.Response(200, stream=Body()) if calls == 1 else response()

        app = create_app(Settings(api_key="synthetic", token=TOKEN), httpx.MockTransport(transport))
        async with app.router.lifespan_context(app):
            upstream = app.state.upstream_client
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app), base_url="http://synthetic") as caller:
                task = asyncio.create_task(caller.post("/v1/routes", json=POINTS, headers=HEADERS))
                await asyncio.wait_for(entered.wait(), 2)
                task.cancel()
                with pytest.raises(asyncio.CancelledError):
                    await task
                await asyncio.wait_for(closed.wait(), 2)
                assert not upstream.is_closed
                assert (await caller.post("/v1/routes", json=POINTS, headers=HEADERS)).status_code == 200
        assert upstream.is_closed

    asyncio.run(scenario())
