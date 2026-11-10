"""Opt-in replay of the public development session, including rerouted/return legs.

Only official emulator-console location input is used. No raw locations are logged.
"""
import argparse
import json
import math
import subprocess
import time

from backend.gps_replay import distance, point_at, PUBLIC_CATALOG
from backend.smoke import ORIGIN, DESTINATION
from backend.dev import DEFAULT_SERIAL, adb_serial


def validate_session(data, now_ms, allow_catalog=False):
    outbound=data.get('trip_destination') or (data.get('destination') if not data.get('returning') else None)
    approved=data.get('development_public_replay') is True and outbound==DESTINATION
    if allow_catalog:
        expected=PUBLIC_CATALOG.get(data.get('candidate_id'))
        approved=expected is not None and outbound=={'longitude':expected[0],'latitude':expected[1]}
    if (not approved or data.get('trip_origin') != ORIGIN or
            data.get('source') != 'KAKAO_DIRECTIONS' or
            data.get('destination') != (ORIGIN if data.get('returning') else outbound) or
            not 0 <= now_ms-data.get('measured_at_epoch_ms', 0) <= 3_600_000):
        raise ValueError('not a fresh public development session')
    origin=data['origin']
    if not (126.8 <= origin['longitude'] <= 127.1 and 37.4 <= origin['latitude'] <= 37.7):
        raise ValueError('outside public pilot area')
    points=[p for segment in data['guidance']['segments'] for p in segment['geometry']]
    if not 2 <= len(points) <= 100_000 or any(len(p)!=2 or
            any(isinstance(v, bool) or not isinstance(v, (float,int)) or not math.isfinite(v) for v in p) or
            not 126.7 <= p[0] <= 127.2 or not 37.3 <= p[1] <= 37.8 for p in points):
        raise ValueError('invalid public geometry')
    return points


def sample_offsets(scenario, length, start, count):
    if length < 150 or not 1 <= count <= 600 or not 0 <= start < length:
        raise ValueError('invalid replay bounds')
    if scenario == 'finish':
        return [max(0,length-x) for x in (120,80,40,20)]+[length-5]*max(4,count-4)
    if scenario == 'arrival-approach':
        if length<650: raise ValueError('route too short for approach')
        return [length-x for x in range(600,0,-20)]+[length-10]+[length-5]*4
    if scenario == 'hold':
        return [start]*count
    if scenario == 'traverse':
        return [min(start+20*i,length-150) for i in range(count)]
    raise ValueError('unknown scenario')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',default='adb')
    parser.add_argument('--serial',type=adb_serial,default=DEFAULT_SERIAL)
    scope=parser.add_mutually_exclusive_group(required=True)
    scope.add_argument('--public-session',action='store_true')
    scope.add_argument('--prepared-public-catalog',action='store_true')
    parser.add_argument('--scenario',choices=('hold','traverse','finish','arrival-approach','off-route'),default='hold')
    parser.add_argument('--meters',type=float,default=100)
    parser.add_argument('--count',type=int,default=8)
    args=parser.parse_args()
    def adb(*parts):
        result=subprocess.run([args.adb,'-s',args.serial,*parts],capture_output=True,timeout=10)
        if result.returncode: raise ValueError('emulator command failed')
        return result.stdout
    try:
        if adb('shell','getprop','ro.product.model').strip()!=b'Connect on x86_64 emulator':
            raise ValueError('not the intended emulator')
        data=json.loads(adb('exec-out','run-as','com.surpride.navhostpoc','--user','10','cat','cache/route-guidance.json'))
        points=validate_session(data,int(time.time()*1000),args.prepared_public_catalog)
        length=sum(distance(a,b) for a,b in zip(points,points[1:]))
        if args.scenario=='off-route':
            if not 3 <= args.count <= 10: raise ValueError('invalid off-route count')
            # Two fixed public development intersections; choose the one farther from this route.
            # Never accepts a user-entered coordinate or changes the destination.
            test_point=max(([126.9665,37.5659],[126.9835,37.5670]),
                           key=lambda p:min(distance(p,q) for q in points))
            updates=[test_point]*args.count
        else:
            updates=[point_at(points,x) for x in sample_offsets(args.scenario,length,args.meters,args.count)]
        for i,point in enumerate(updates,1):
            if args.scenario not in ('finish','arrival-approach','off-route') and distance(point,points[-1])<100:
                raise ValueError('destination exclusion zone')
            if b'OK' not in adb('emu','geo','fix',f'{point[0]:.9f}',f'{point[1]:.9f}'):
                raise ValueError('console rejected input')
            if i==1 or i==len(updates):
                print(f'{args.scenario}: public emulator input {i}/{len(updates)}; serial={args.serial}',flush=True)
            time.sleep(1.2)
        return 0
    except Exception:
        print('Public session replay failed; no coordinates or raw output retained.')
        return 1


if __name__=='__main__':
    raise SystemExit(main())
