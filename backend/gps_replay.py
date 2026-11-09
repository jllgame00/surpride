"""Explicit emulator-only public-route GPS replay. Coordinates stay in process memory.

Uses the official emulator console, never Android mock-provider or privileged APIs.
Requires the public route provisioned by backend.nav_poc; refuses arbitrary routes.
"""
import argparse
import json
import math
import subprocess
import time

from backend.smoke import ORIGIN, DESTINATION

# Same explicitly public Seoul pilot catalog as the app; never accepts an arbitrary user destination.
PUBLIC_CATALOG = {
    'seoul-night-3': (126.958588093172,37.5118003827004),
    'seoul-night-15': (126.949411135441,37.5134579901091),
    'seoul-night-13': (126.970850893942,37.517820588578),
    'seoul-night-21': (126.976787992565,37.5716228232824),
    'seoul-night-22': (126.886318397942,37.5690794796181),
}

def validate_public_route(data, now_ms, allow_catalog):
    approved=data['destination']==DESTINATION
    if allow_catalog:
        expected=PUBLIC_CATALOG.get(data.get('candidate_id'))
        approved=approved or (expected is not None and
            abs(data['destination']['longitude']-expected[0])<1e-8 and abs(data['destination']['latitude']-expected[1])<1e-8)
    if data['origin']!=ORIGIN or not approved or data['source']!='KAKAO_DIRECTIONS':
        raise ValueError('not an approved public test route')
    if not 0<=now_ms-data['measured_at_epoch_ms']<=3_600_000:
        raise ValueError('expired route')
    points=[p for road in data['guidance']['segments'] for p in road['geometry']]
    if not 2<=len(points)<=100_000 or any(len(p)!=2 or not all(math.isfinite(v) for v in p)
            or not -180<=p[0]<=180 or not -90<=p[1]<=90 for p in points):
        raise ValueError('invalid geometry')
    return points

def replay_distances(scenario, length, start=100):
    if length<200: raise ValueError('route too short')
    if scenario=='hold-start': return [100]*8
    if scenario=='end-approach': return [length-x for x in (120,80,40,20,10,5,5,5,5,5)]
    if not 0<=start<=length-500: raise ValueError('invalid public replay range')
    return list(range(start,int(min(start+1500,length-500))+1,40))


def distance(a, b):
    lat1, lat2 = map(math.radians, (a[1], b[1]))
    h = math.sin((lat2-lat1)/2)**2 + math.cos(lat1)*math.cos(lat2)*math.sin(math.radians(b[0]-a[0])/2)**2
    return 6371000*2*math.asin(math.sqrt(min(1, max(0, h))))


def point_at(points, target):
    for a, b in zip(points, points[1:]):
        size = distance(a, b)
        if size and target <= size:
            f = target/size
            return [a[0]+(b[0]-a[0])*f, a[1]+(b[1]-a[1])*f]
        target -= size
    return points[-1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    scope=parser.add_mutually_exclusive_group(required=True)
    scope.add_argument('--public-route-prefix', action='store_true')
    scope.add_argument('--prepared-public-catalog', action='store_true')
    parser.add_argument('--scenario', choices=('prefix','off-route','rejoin','hold-start','end-approach'), default='prefix')
    parser.add_argument('--from-meter',type=int,default=100)
    args = parser.parse_args()

    def adb(*parts):
        result = subprocess.run([args.adb, '-s', 'emulator-5554', *parts], capture_output=True, timeout=10)
        if result.returncode:
            raise ValueError('emulator command failed')
        return result.stdout

    try:
        if adb('shell', 'getprop', 'ro.product.model').strip() != b'Connect on x86_64 emulator':
            raise ValueError('not the intended emulator')
        data = json.loads(adb('exec-out', 'run-as', 'com.surpride.navhostpoc', '--user', '10', 'cat', 'cache/route-guidance.json'))
        points=validate_public_route(data,int(time.time()*1000),args.prepared_public_catalog)
        length = sum(distance(a, b) for a, b in zip(points, points[1:]))
        end = min(1600, length-500)
        if end < 200:
            raise ValueError('route too short')
        if args.scenario in ('prefix','hold-start','end-approach'):
            updates = [point_at(points,x) for x in replay_distances(args.scenario,length,args.from_meter)]
        elif args.scenario == 'rejoin':
            updates = [point_at(points, end+x) for x in (0, 10, 20, 30)]
        else:
            base = point_at(points, end)
            # Deliberately far from the prefix. No rerouting or OEM route modifications.
            updates = [[base[0], base[1]+0.01]]*5
        for index, point in enumerate(updates, 1):
            if args.scenario!='end-approach' and distance(point, points[-1]) < 200:
                raise ValueError('destination exclusion zone')
            output = adb('emu', 'geo', 'fix', f'{point[0]:.9f}', f'{point[1]:.9f}')
            if b'OK' not in output:
                raise ValueError('console rejected input')
            if index == 1 or index == len(updates):
                print(f'{args.scenario}: official emulator input {index}/{len(updates)}', flush=True)
            time.sleep(1.2)
        return 0
    except Exception:
        print('Public GPS replay failed; no coordinates or raw command output retained.')
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
