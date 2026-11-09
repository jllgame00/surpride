"""Synthetic route-option contract; no provider calls or private locations."""
from copy import deepcopy

import httpx
import pytest
from fastapi.testclient import TestClient

from backend.app import Settings, create_app

TOKEN = "synthetic-route-options-token-0000000000"
HEADERS = {"Authorization": "Bearer " + TOKEN}
ORIGIN = {"longitude": 126.9, "latitude": 37.5}
DESTINATION = {"longitude": 127.1, "latitude": 37.7}
WAYPOINTS = [{"longitude": 126.95, "latitude": 37.55},
             {"longitude": 127.0, "latitude": 37.6}]
REQUEST = {"origin": ORIGIN, "destination": DESTINATION}


def upstream(waypoints=()):
    points = [ORIGIN, *waypoints, DESTINATION]
    sections = []
    for i, (start, end) in enumerate(zip(points, points[1:])):
        sections.append({"distance": 100, "duration": 20, "roads": [{
            "name": "Synthetic road", "distance": 100, "duration": 20,
            "traffic_state": 6 if i == 0 else 4,
            "vertexes": [start["longitude"], start["latitude"], end["longitude"], end["latitude"]]}],
            "guides": [{"type": 100, "distance": 0, "road_index": 0,
                        "name": "private POI", "guidance": "private guidance"},
                       {"type": 101 if i == len(points) - 2 else 1000,
                        "distance": 100, "road_index": -1,
                        "name": "private POI", "guidance": "private guidance"}]})
    return {"routes": [{"result_code": 0, "summary": {
        "distance": 100 * len(sections), "duration": 20 * len(sections),
        "waypoints": [{"x": p["longitude"], "y": p["latitude"], "name": "private POI"}
                      for p in waypoints]}, "sections": sections}]}


def client(handler=None, mode="live"):
    return TestClient(create_app(Settings(api_key="synthetic", token=TOKEN, mode=mode),
        httpx.MockTransport(handler or (lambda _: httpx.Response(200, json=upstream())))))


def test_default_options_preserve_direct_route_and_disable_upstream_alternatives():
    def handler(request):
        assert request.method == "GET"
        assert request.url.path == "/v1/directions"
        assert request.url.params["priority"] == "RECOMMEND"
        assert request.url.params["summary"] == request.url.params["alternatives"] == "false"
        assert "waypoints" not in request.url.params
        return httpx.Response(200, json=upstream())
    with client(handler) as app:
        response = app.post("/v1/routes", json=REQUEST, headers=HEADERS)
    assert response.status_code == 200
    assert response.json()["schema_version"] == 1
    assert response.json()["request_options"] == {"priority": "RECOMMEND", "waypoints": []}
    assert "guidance" not in response.json()
    assert response.headers["Cache-Control"] == "no-store"


@pytest.mark.parametrize("priority", ["RECOMMEND", "TIME", "DISTANCE"])
@pytest.mark.parametrize("count", [0, 1, 2])
def test_priority_and_ordered_waypoints_are_encoded_and_returned(priority, count):
    waypoints = WAYPOINTS[:count]
    def handler(request):
        assert request.url.params["priority"] == priority
        if count:
            assert request.url.params["waypoints"] == ("126.95,37.55" if count == 1 else "126.95,37.55|127.0,37.6")
        else:
            assert "waypoints" not in request.url.params
        return httpx.Response(200, json=upstream(waypoints))
    with client(handler) as app:
        response = app.post("/v1/routes", json={**REQUEST, "priority": priority, "waypoints": waypoints}, headers=HEADERS)
    assert response.status_code == 200
    assert response.json()["request_options"] == {"priority": priority, "waypoints": waypoints}
    assert response.json()["distance_meters"] == 100 * (count + 1)
    assert "private" not in response.text


@pytest.mark.parametrize("change", [
    {"priority": "time"}, {"priority": "UNKNOWN"}, {"priority": None}, {"priority": 1},
    {"priority": True}, {"waypoints": None}, {"waypoints": {}}, {"waypoints": "126.95,37.55"},
    {"waypoints": WAYPOINTS + [{"longitude": 127.02, "latitude": 37.61}]},
    {"waypoints": [WAYPOINTS[0], WAYPOINTS[0]]}, {"waypoints": [ORIGIN]},
    {"waypoints": [DESTINATION]}, {"destination": ORIGIN},
    {"waypoints": [{"longitude": "126.95", "latitude": 37.55}]},
    {"waypoints": [{"longitude": True, "latitude": 37.55}]},
    {"waypoints": [{"longitude": 181, "latitude": 37.55}]},
    {"waypoints": [{"longitude": 126.95, "latitude": -91}]},
    {"waypoints": [{"longitude": 126.95, "latitude": 37.55, "name": "private POI"}]},
    {"waypoints": [{"longitude": 126.95}]}, {"avoid": "toll"},
])
def test_invalid_options_fail_before_upstream_without_echo(change):
    seen = []
    with client(lambda request: seen.append(request)) as app:
        response = app.post("/v1/routes", json={**REQUEST, **change}, headers=HEADERS)
    assert response.status_code == 422
    assert response.json() == {"error": "INVALID_COORDINATE"}
    assert not seen


@pytest.mark.parametrize("bad_waypoints", [None, [], [{"x": 126.95, "y": 38.0}],
    [{"x": "126.95", "y": 37.55}], [{"x": 126.95}], [{"x": True, "y": 37.55}]])
def test_missing_or_mismatched_summary_waypoints_fail_closed(bad_waypoints):
    value = upstream(WAYPOINTS[:1])
    if bad_waypoints is None:
        value["routes"][0]["summary"].pop("waypoints")
    else:
        value["routes"][0]["summary"]["waypoints"] = bad_waypoints
    with client(lambda _: httpx.Response(200, json=value)) as app:
        response = app.post("/v1/routes", json={**REQUEST, "waypoints": WAYPOINTS[:1]}, headers=HEADERS)
    assert response.status_code == 502
    assert response.json() == {"error": "INVALID_RESPONSE"}


def test_wrong_section_count_and_reordered_waypoints_fail_closed():
    value = upstream(WAYPOINTS)
    missing_section = deepcopy(value)
    missing_section["routes"][0]["sections"].pop()
    reordered = deepcopy(value)
    reordered["routes"][0]["summary"]["waypoints"].reverse()
    for bad in (missing_section, reordered):
        with client(lambda _: httpx.Response(200, json=bad)) as app:
            response = app.post("/v1/routes", json={**REQUEST, "waypoints": WAYPOINTS}, headers=HEADERS)
        assert response.status_code == 502
        assert response.json() == {"error": "INVALID_RESPONSE"}


def test_waypoint_guidance_keeps_official_type_global_offsets_indices_and_traffic():
    with client(lambda _: httpx.Response(200, json=upstream(WAYPOINTS))) as app:
        response = app.post("/v1/routes", json={**REQUEST, "waypoints": WAYPOINTS,
            "include_guidance": True}, headers=HEADERS)
    assert response.status_code == 200
    guidance = response.json()["guidance"]
    assert [m["type"] for m in guidance["maneuvers"]] == [100, 1000, 100, 1000, 100, 101]
    assert [m["offset_meters"] for m in guidance["maneuvers"]] == [0, 100, 100, 200, 200, 300]
    assert [m["road_index"] for m in guidance["maneuvers"]] == [0, 0, 1, 1, 2, 2]
    assert [r["traffic_state"] for r in guidance["segments"]] == [6, 4, 4]
    assert "private" not in response.text


def test_explicit_mock_options_remain_mock_and_do_not_use_upstream():
    seen = []
    with client(lambda request: seen.append(request), mode="mock") as app:
        response = app.post("/v1/routes", json={**REQUEST, "priority": "TIME", "waypoints": WAYPOINTS}, headers=HEADERS)
    assert response.status_code == 200
    assert response.json()["source"] == "MOCK"
    assert response.json()["request_options"] == {"priority": "TIME", "waypoints": WAYPOINTS}
    assert not seen


@pytest.mark.parametrize("priority", ["RECOMMEND", "TIME", "DISTANCE"])
@pytest.mark.parametrize("count", [0, 1, 2])
def test_mock_guidance_options_return_explicit_synthetic_sections_without_upstream(priority, count):
    seen = []
    with client(lambda request: seen.append(request), mode="mock") as app:
        response = app.post("/v1/routes", json={**REQUEST, "priority": priority,
            "waypoints": WAYPOINTS[:count], "include_guidance": True}, headers=HEADERS)
        reverse = app.post("/v1/routes", json={"origin": DESTINATION, "destination": ORIGIN,
            "priority": priority, "waypoints": WAYPOINTS[:count], "include_guidance": True}, headers=HEADERS)
    assert response.status_code == reverse.status_code == 200
    data = response.json()
    assert data["source"] == reverse.json()["source"] == "MOCK"
    assert data["request_options"] == {"priority": priority, "waypoints": WAYPOINTS[:count]}
    assert data["duration_seconds"] == 780
    assert reverse.json()["duration_seconds"] == 540
    assert data["distance_meters"] == 4200
    guidance = data["guidance"]
    assert len(guidance["segments"]) == count + 1
    assert sum(s["duration_seconds"] for s in guidance["segments"]) == 780
    assert sum(s["distance_meters"] for s in guidance["segments"]) == data["distance_meters"]
    assert all(s["road_name"] == "" and s["traffic_state"] == 0 for s in guidance["segments"])
    assert guidance["segments"][0]["geometry"][0] == [126.9, 37.5]
    assert guidance["segments"][-1]["geometry"][-1] == [127.1, 37.7]
    assert [m["type"] for m in guidance["maneuvers"]] == [100] + [1000] * count + [101]
    assert [m["offset_meters"] for m in guidance["maneuvers"]] == (
        [0, 4200] if count == 0 else [0, 2100, 4200] if count == 1 else [0, 1400, 2800, 4200])
    assert guidance["maneuvers"][-1]["road_index"] == count
    assert response.headers["Cache-Control"] == "no-store"
    assert not seen
