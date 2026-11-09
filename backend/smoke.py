"""Two explicit public-coordinate requests; save only aggregate evidence, never raw routes/keys."""
import argparse
import json
import time
import httpx

from backend.dev import SESSION

ORIGIN = {"longitude": 126.976787992565, "latitude": 37.5716228232824}
DESTINATION = {"longitude": 126.970850893942, "latitude": 37.517820588578}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--request-public-roundtrip", action="store_true", required=True)
    args = parser.parse_args()
    config = json.loads(SESSION.read_text(encoding="utf-8"))
    if config["expires_at_ms"] <= int(time.time() * 1000):
        print("Session expired; no requests made")
        return 1
    results = []
    with httpx.Client(trust_env=False, timeout=15, follow_redirects=False) as client:
        for origin, destination in ((ORIGIN, DESTINATION), (DESTINATION, ORIGIN)):
            try:
                response = client.post("http://127.0.0.1:8765/v1/routes", json={"origin": origin, "destination": destination},
                                       headers={"Authorization": "Bearer " + config["token"]})
                data = response.json()
                if response.status_code != 200:
                    print(json.dumps({"status": response.status_code, "error": data.get("error", "API")}))
                    return 1
                if data["origin"] != origin or data["destination"] != destination:
                    print("Response endpoints mismatched; no evidence retained")
                    return 1
                results.append({"source": data["source"], "duration_seconds": data["duration_seconds"],
                                "distance_meters": data["distance_meters"], "geometry_points": len(data["geometry"])})
            except Exception:
                print("Local proxy smoke request failed; no secrets printed")
                return 1
    total = sum(item["duration_seconds"] for item in results) + 20 * 60 + 10 * 60
    print(json.dumps({"outbound": results[0], "inbound": results[1], "stay_minutes": 20, "buffer_minutes": 10,
                      "total_seconds": total, "fits_60_minutes": total <= 3600,
                      "vehicle_entry": "UNKNOWN", "observed_at_epoch_ms": int(time.time() * 1000)}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
