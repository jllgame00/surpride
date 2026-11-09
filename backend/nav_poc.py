"""Explicit public-route fetch + stdin provisioning to the isolated debug NavigationTemplate PoC."""
import argparse
import json
import subprocess
import time
import httpx
from backend.dev import SESSION
from backend.smoke import ORIGIN, DESTINATION


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--adb',default='adb')
    parser.add_argument('--request-public-route',action='store_true',required=True)
    args=parser.parse_args()
    try:
        config=json.loads(SESSION.read_text(encoding='utf-8'))
        if config['expires_at_ms']<=int(time.time()*1000):
            raise ValueError()
        with httpx.Client(trust_env=False,timeout=15,follow_redirects=False) as client:
            response=client.post('http://127.0.0.1:8765/v1/routes',headers={'Authorization':'Bearer '+config['token']},
                                 json={'origin':ORIGIN,'destination':DESTINATION,'include_guidance':True})
        if response.status_code!=200:
            print('Guidance fetch failed: '+str(response.status_code)+' / '+response.json().get('error','API'))
            return 1
        data=response.json()
        assert data['source'] in ('KAKAO_DIRECTIONS','MOCK')
        assert data['origin']==ORIGIN and data['destination']==DESTINATION
        data['private_destination_label']='이촌한강공원'
        data.update(proxy_session=config,trip_origin=ORIGIN,trip_destination=DESTINATION,
                    return_to_start=True,available_minutes=120,stay_seconds=1200,safety_buffer_seconds=600,
                    candidate_id='seoul-night-13',development_public_replay=True)
        encoded=json.dumps(data,ensure_ascii=False).encode('utf-8')
        if len(encoded)>2_000_000:
            raise ValueError()
        command=[args.adb,'-s','emulator-5554','exec-in',
                 "run-as com.surpride.navhostpoc --user 10 sh -c 'cat > cache/route-guidance.json'"]
        result=subprocess.run(command,input=encoded,capture_output=True,timeout=10)
        if result.returncode or result.stderr:
            raise ValueError()
        check=subprocess.run([args.adb,'-s','emulator-5554','exec-out','run-as','com.surpride.navhostpoc','--user','10','wc','-c','cache/route-guidance.json'],capture_output=True,timeout=10)
        if check.returncode or int(check.stdout.split()[0])!=len(encoded):
            raise ValueError()
        guidance=data['guidance']
        print(json.dumps({'source':data['source'],'segments':len(guidance['segments']),'maneuvers':len(guidance['maneuvers']),
                          'geometry_points':len(data['geometry']),'distance_meters':data['distance_meters'],
                          'duration_seconds':data['duration_seconds'],'guide_types':sorted(set(x['type'] for x in guidance['maneuvers'])),
                          'private_cache_provisioned':True},indent=2))
        return 0
    except Exception:
        print('Guidance fetch/provision failed. Check server, contract, installed debug PoC and user10. No private data printed.')
        return 1


if __name__=='__main__':
    raise SystemExit(main())
