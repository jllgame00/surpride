"""Optional structured guidance contract; never forward guide POI names/raw guidance strings."""
from backend.app import integer, Point


def guidance_data(route):
    segments, maneuvers = [], []
    section_offset = 0
    previous_offset = 0
    previous_road = 0
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
            for i in range(0, len(vertices), 2):
                if type(vertices[i]) not in (int, float) or type(vertices[i+1]) not in (int, float):
                    raise ValueError("invalid coordinate")
                p = Point(longitude=vertices[i], latitude=vertices[i+1])
                points.append([p.longitude, p.latitude])
            name = road["name"]
            if not isinstance(name, str) or len(name) > 200 or any(ord(c)<32 for c in name):
                raise ValueError("invalid road name")
            segments.append({"road_name": name, "distance_meters": integer(road["distance"],0,100_000_000),
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
            maneuvers.append({"type":code,"offset_meters":offset,"road_index":global_road})
            previous_offset,previous_road = offset,global_road
        section_offset += integer(section["distance"],0,100_000_000)
    if len(segments)>10000 or len(maneuvers)>10000 or not maneuvers:
        raise ValueError("invalid guidance count")
    return {"segments":segments,"maneuvers":maneuvers}
