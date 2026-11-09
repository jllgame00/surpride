import json
import logging

import httpx
import pytest
from fastapi.testclient import TestClient

from backend.app import Settings, create_app

TOKEN = "synthetic-local-test-token-00000000000"
KEY = "synthetic-provider-test-key"
POINTS = {"origin": {"longitude": 126.976787992565, "latitude": 37.5716228232824},
          "destination": {"longitude": 126.970850893942, "latitude": 37.517820588578}}
HEADERS = {"Authorization": "Bearer " + TOKEN}


def upstream(duration=600, distance=4200, code=0):
    return {"routes": [{"result_code": code, "summary": {"duration": duration, "distance": distance},
                        "sections": [{"roads": [{"vertexes": [126.97, 37.57, 126.98, 37.58]}]}]}]}


def client(handler=None, key=KEY, mode="live", **kwargs):
    settings = Settings(api_key=key, token=TOKEN, mode=mode, **kwargs)
    transport = httpx.MockTransport(handler or (lambda request: httpx.Response(200, json=upstream())))
    return TestClient(create_app(settings, transport=transport))


def test_no_token_fails_closed_before_upstream():
    seen = []
    with client(lambda req: seen.append(req)) as app:
        assert app.post("/v1/routes", json=POINTS).status_code == 401
        assert app.post("/v1/routes", json=POINTS, headers={"Authorization": "Bearer wrong"}).status_code == 401
    assert not seen


def test_missing_provider_key_never_calls_network_or_falls_back():
    seen = []
    with client(lambda req: seen.append(req), key="") as app:
        response = app.post("/v1/routes", json=POINTS, headers=HEADERS)
    assert response.status_code == 503
    assert response.json() == {"error": "NOT_CONFIGURED"}
    assert not seen


def test_official_request_contract_and_minimal_normalized_response():
    def handler(request):
        assert request.url.scheme == "https"
        assert request.url.host == "apis-navi.kakaomobility.com"
        assert request.url.path == "/v1/directions"
        assert request.headers["Authorization"] == "KakaoAK " + KEY
        assert request.url.params["origin"] == "126.976787992565,37.5716228232824"
        assert request.url.params["priority"] == "RECOMMEND"
        return httpx.Response(200, json=upstream())
    with client(handler) as app:
        response = app.post("/v1/routes", json=POINTS, headers=HEADERS)
    data = response.json()
    assert response.status_code == 200
    assert data["source"] == "KAKAO_DIRECTIONS"
    assert data["duration_seconds"] == 600
    assert data["distance_meters"] == 4200
    assert data["geometry"][0] == [126.97, 37.57]
    assert data["origin"] == POINTS["origin"]
    assert KEY not in response.text and TOKEN not in response.text
    assert "routes" not in data and "trans_id" not in data


@pytest.mark.parametrize("status,error", [(401, "AUTH"), (403, "AUTH"), (429, "RATE_LIMIT"), (500, "API")])
def test_upstream_http_errors_are_sanitized(status, error, caplog):
    with caplog.at_level(logging.DEBUG), client(lambda req: httpx.Response(status, text=KEY + TOKEN)) as app:
        response = app.post("/v1/routes", json=POINTS, headers=HEADERS)
    assert response.json() == {"error": error}
    assert KEY not in response.text and TOKEN not in response.text
    assert KEY not in caplog.text and TOKEN not in caplog.text


@pytest.mark.parametrize("code,error", [(1, "NO_ROUTE"), (102, "INVALID_COORDINATE"), (104, "INVALID_COORDINATE"), (105, "NO_ROUTE"), (999, "API")])
def test_official_result_codes(code, error):
    with client(lambda req: httpx.Response(200, json=upstream(code=code))) as app:
        assert app.post("/v1/routes", json=POINTS, headers=HEADERS).json() == {"error": error}


@pytest.mark.parametrize("code,error", [(-10, "RATE_LIMIT"), (-2, "INVALID_COORDINATE"), (-903, "AUTH")])
def test_http_400_official_error_body_is_classified_without_echo(code, error):
    with client(lambda req: httpx.Response(400, json={"code": code, "msg": KEY})) as app:
        response = app.post("/v1/routes", json=POINTS, headers=HEADERS)
    assert response.json() == {"error": error}
    assert KEY not in response.text


@pytest.mark.parametrize("value", [-1, 0, 1.5, True, 9999999999999999, "600"])
def test_bad_duration_never_becomes_route(value):
    with client(lambda req: httpx.Response(200, json=upstream(duration=value))) as app:
        assert app.post("/v1/routes", json=POINTS, headers=HEADERS).json() == {"error": "INVALID_RESPONSE"}


def test_invalid_request_does_not_echo_coordinate_or_call_provider():
    seen = []
    with client(lambda req: seen.append(req)) as app:
        response = app.post("/v1/routes", json={"origin": {"longitude": 126, "latitude": "private-invalid-value"},
                                               "destination": POINTS["destination"]}, headers=HEADERS)
    assert response.status_code == 422
    assert "private-invalid-value" not in response.text
    assert not seen


def test_invalid_geometry_and_non_json_response():
    body = upstream()
    body["routes"][0]["sections"][0]["roads"][0]["vertexes"] = [126.97, 137.0, 127.0]
    for response in (httpx.Response(200, json=body), httpx.Response(200, text="bad response")):
        with client(lambda req: response) as app:
            assert app.post("/v1/routes", json=POINTS, headers=HEADERS).json() == {"error": "INVALID_RESPONSE"}


def test_timeout_and_private_exception_are_network_or_sanitized():
    def timeout(request):
        raise httpx.ReadTimeout(KEY, request=request)
    with client(timeout) as app:
        assert app.post("/v1/routes", json=POINTS, headers=HEADERS).json() == {"error": "NETWORK"}
    def exception(request):
        raise ValueError(KEY)
    with client(exception) as app:
        response = app.post("/v1/routes", json=POINTS, headers=HEADERS)
        assert response.json() == {"error": "API"}
        assert KEY not in response.text


def test_authenticated_request_limit_and_lifetime_budget():
    with client(mode="mock", requests_per_minute=1) as app:
        assert app.post("/v1/routes", json=POINTS, headers=HEADERS).status_code == 200
        assert app.post("/v1/routes", json=POINTS, headers=HEADERS).status_code == 429
    with client(mode="mock", max_requests=1) as app:
        assert app.post("/v1/routes", json=POINTS, headers=HEADERS).status_code == 200
        assert app.post("/v1/routes", json=POINTS, headers=HEADERS).json() == {"error": "RATE_LIMIT"}


def test_explicit_mock_network_response_stays_mock_and_reverse_is_separate():
    with client(mode="mock", key="") as app:
        out = app.post("/v1/routes", json=POINTS, headers=HEADERS).json()
        back = app.post("/v1/routes", json={"origin": POINTS["destination"], "destination": POINTS["origin"]}, headers=HEADERS).json()
    assert out["source"] == back["source"] == "MOCK"
    assert out["duration_seconds"] != back["duration_seconds"]
    assert out["destination"] == back["origin"]
    assert out["duration_seconds"] + back["duration_seconds"] + 20 * 60 + 10 * 60 == 3120


def test_settings_and_response_never_expose_secrets():
    assert KEY not in repr(Settings(api_key=KEY, token=TOKEN))
    assert TOKEN not in repr(Settings(api_key=KEY, token=TOKEN))
    with pytest.raises(ValueError):
        create_app(Settings(api_key=KEY, token=""))
    with client(expires_at_ms=1) as app:
        assert app.post("/v1/routes", json=POINTS, headers=HEADERS).status_code == 401


def test_response_size_and_request_size_are_bounded():
    with client(lambda req: httpx.Response(200, content=b"x" * 2_000_001)) as app:
        assert app.post("/v1/routes", json=POINTS, headers=HEADERS).json() == {"error": "INVALID_RESPONSE"}
    with client() as app:
        assert app.post("/v1/routes", content=json.dumps({"padding": "x" * 5000}), headers=HEADERS).status_code == 413
