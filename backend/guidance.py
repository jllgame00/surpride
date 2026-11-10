"""Optional structured guidance contract; never forward guide POI names/raw guidance strings."""
from backend.app import integer, Point


def guidance_data(route):
    segments, maneuvers = [], []
    road_starts, anchors = [], []
    road_distance = 0
    coordinate_count = 0
    section_offset = 0
    previous_offset = 0
    previous_road = 0
    turns = {}
    for section in route["sections"]:
        roads = section["roads"]
        guides = section["guides"]
        if not roads or not guides:
            raise ValueError("missing guidance data")
        road_base = len(segments)
        for road in roads:
            vertices = road["vertexes"]
            if not isinstance(vertices, list) or len(vertices) < 4 or len(vertices) % 2:
                raise ValueError("invalid road geometry")
            points = []
            coordinate_count += len(vertices) // 2
            if coordinate_count > 100_000:
                raise ValueError("geometry limit")
            for i in range(0, len(vertices), 2):
                if type(vertices[i]) not in (int, float) or type(vertices[i+1]) not in (int, float):
                    raise ValueError("invalid coordinate")
                p = Point(longitude=vertices[i], latitude=vertices[i+1])
                point = [p.longitude, p.latitude]
                if not points or points[-1] != point:
                    points.append(point)
            if len(points) < 2 or segments and segments[-1]["geometry"][-1] != points[0]:
                raise ValueError("disconnected or degenerate road")
            name = road["name"]
            if not isinstance(name, str) or len(name) > 200 or any(ord(c)<32 for c in name):
                raise ValueError("invalid road name")
            length = integer(road["distance"],1,100_000_000)
            road_starts.append(road_distance)
            anchors.append({0: road_distance, len(points)-1: road_distance+length})
            road_distance += length
            segments.append({"road_name": name, "distance_meters": length,
                             "duration_seconds": integer(road["duration"],0,604800),
                             "traffic_state": integer(road["traffic_state"],0,999), "geometry":points})
        distance = 0
        for guide in guides:
            distance += integer(guide["distance"],0,100_000_000)
            code = integer(guide["type"],0,10000)
            road_index = integer(guide["road_index"],-1,len(roads)-1)
            if road_index < 0:
                if code not in (101,1000):
                    raise ValueError("missing road index")
                road_index = len(roads)-1
            offset = section_offset+distance
            global_road = road_base+road_index
            if offset < previous_offset or global_road < previous_road or offset > route["summary"]["distance"]:
                raise ValueError("out of order guidance")
            if not road_starts[global_road] <= offset <= road_starts[global_road]+segments[global_road]["distance_meters"]:
                raise ValueError("guide outside indexed road")
            maneuver = {"type":code,"offset_meters":offset,"road_index":global_road}
            if code not in (100, 101, 1000):
                if offset in turns and turns[offset] != code:
                    raise ValueError("conflicting turns")
                turns[offset] = code
            if ("x" in guide) != ("y" in guide):
                raise ValueError("incomplete guide anchor")
            if "x" in guide:
                point = Point(longitude=guide["x"], latitude=guide["y"])
                coordinate = [point.longitude, point.latitude]
                geometry = segments[global_road]["geometry"]
                indices = [i for i, value in enumerate(geometry) if value == coordinate]
                if len(indices) != 1:
                    raise ValueError("unverified guide anchor")
                index = indices[0]
                if index in anchors[global_road] and anchors[global_road][index] != offset:
                    raise ValueError("conflicting guide anchor")
                anchors[global_road][index] = offset
                maneuver["anchor"] = coordinate
            maneuvers.append(maneuver)
            previous_offset,previous_road = offset,global_road
        section_offset += integer(section["distance"],0,100_000_000)
        if section_offset != road_distance:
            raise ValueError("section distance mismatch")
    if len(segments)>10000 or len(maneuvers)>10000 or not maneuvers:
        raise ValueError("invalid guidance count")
    if road_distance != route["summary"]["distance"]:
        raise ValueError("route distance mismatch")
    for road in anchors:
        ordered = [offset for _, offset in sorted(road.items())]
        if any(a >= b for a, b in zip(ordered, ordered[1:])):
            raise ValueError("non-monotonic guide anchors")
    return {"segments":segments,"maneuvers":maneuvers}
