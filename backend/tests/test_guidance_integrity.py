"""Reject broken public/synthetic guidance before it reaches an Android navigator."""
import pytest
from backend.guidance import guidance_data
from backend.tests.test_guidance import body, response


def test_disconnected_roads_never_create_a_successful_guidance_response():
    value = body()
    value["routes"][0]["sections"][0]["roads"][1]["vertexes"][0] += 0.01
    result = response(value)
    assert result.status_code == 502
    assert result.json() == {"error": "INVALID_RESPONSE"}


def test_guide_offset_outside_its_road_is_rejected():
    value = body()
    value["routes"][0]["sections"][0]["guides"][1].update(road_index=0, distance=700)
    value["routes"][0]["sections"][0]["guides"][2]["distance"] = 300
    result = response(value)
    assert result.status_code == 502
    assert result.json() == {"error": "INVALID_RESPONSE"}


def test_official_guide_coordinates_are_preserved_as_a_private_anchor():
    value = body()
    value["routes"][0]["sections"][0]["guides"][1].update(x=126.975, y=37.575)
    result = response(value)
    assert result.status_code == 200
    assert result.json()["guidance"]["maneuvers"][1].get("anchor") == [126.975, 37.575]


def test_guide_anchor_outside_its_road_is_rejected():
    value = body()
    value["routes"][0]["sections"][0]["guides"][1].update(x=127.5, y=37.575)
    result = response(value)
    assert result.status_code == 502
    assert result.json() == {"error": "INVALID_RESPONSE"}


@pytest.mark.parametrize("case", ["tiny_gap", "reversed", "single", "zero", "distance", "anchor_conflict", "turn_conflict"])
def test_unproven_geometry_and_guide_contracts_fail_closed(case):
    value = body()
    section = value["routes"][0]["sections"][0]
    if case == "tiny_gap":
        section["roads"][1]["vertexes"][0] += 1e-12
    elif case == "reversed":
        vertices = section["roads"][1]["vertexes"]
        section["roads"][1]["vertexes"] = vertices[2:] + vertices[:2]
    elif case == "single":
        section["roads"][0]["vertexes"] = [126.97, 37.57, 126.97, 37.57]
    elif case == "zero":
        section["roads"][0]["distance"] = 0
    elif case == "distance":
        value["routes"][0]["summary"]["distance"] += 1
    elif case == "anchor_conflict":
        section["guides"][1].update(distance=500, x=126.975, y=37.575)
        section["guides"][2]["distance"] = 500
    else:
        section["guides"].insert(2, {"type": 1, "distance": 0, "road_index": 1})
    result = response(value)
    assert result.status_code == 502
    assert result.json() == {"error": "INVALID_RESPONSE"}


def test_duplicate_points_normalize_but_global_point_limit_is_checked_first():
    value = body()
    road = value["routes"][0]["sections"][0]["roads"][0]
    road["vertexes"] = road["vertexes"][:2] * 2 + road["vertexes"][2:] * 2
    assert len(response(value).json()["guidance"]["segments"][0]["geometry"]) == 2
    for road in value["routes"][0]["sections"][0]["roads"]:
        road["vertexes"] = road["vertexes"][:2] * 50_001 + road["vertexes"][-2:]
    with pytest.raises(ValueError):
        guidance_data(value["routes"][0])
