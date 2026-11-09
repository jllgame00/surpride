import httpx
from fastapi.testclient import TestClient
from backend.app import Settings, create_app

TOKEN="synthetic-guidance-test-token-0000000000"
HEADERS={"Authorization":"Bearer "+TOKEN}
REQUEST={"origin":{"longitude":126.97,"latitude":37.57},"destination":{"longitude":126.98,"latitude":37.58},"include_guidance":True}

def body():
    return {"routes":[{"result_code":0,"summary":{"duration":120,"distance":1000},"sections":[{
        "distance":1000,"roads":[
            {"name":"공개도로1","distance":400,"duration":50,"traffic_state":4,"vertexes":[126.97,37.57,126.975,37.575]},
            {"name":"공개도로2","distance":600,"duration":70,"traffic_state":2,"vertexes":[126.975,37.575,126.98,37.58]}],
        "guides":[{"type":100,"distance":0,"duration":0,"road_index":0,"name":"private origin","guidance":"private address"},
                  {"type":2,"distance":400,"duration":50,"road_index":1,"name":"private POI","guidance":"private destination"},
                  {"type":101,"distance":600,"duration":70,"road_index":1,"name":"private destination","guidance":"private address"}]}]}]}

def response(value):
    transport=httpx.MockTransport(lambda req:httpx.Response(200,json=value))
    with TestClient(create_app(Settings(api_key="synthetic",token=TOKEN),transport)) as client:
        return client.post('/v1/routes',headers=HEADERS,json=REQUEST)

def test_official_guide_distances_accumulate_and_road_order_is_preserved():
    result=response(body())
    assert result.status_code==200
    data=result.json()['guidance']
    assert [m['offset_meters'] for m in data['maneuvers']]==[0,400,1000]
    assert [r['distance_meters'] for r in data['segments']]==[400,600]
    assert data['maneuvers'][1]['type']==2
    assert data['segments'][1]['geometry'][0]==[126.975,37.575]
    assert 'private' not in result.text

def test_missing_guides_and_bad_indices_fail_closed_only_when_requested():
    for mutate in (lambda v:v['routes'][0]['sections'][0].pop('guides'),
                   lambda v:v['routes'][0]['sections'][0]['guides'][1].update(road_index=8),
                   lambda v:v['routes'][0]['sections'][0]['guides'][1].update(distance=-1),
                   lambda v:v['routes'][0]['sections'][0]['guides'][1].update(road_index=-1)):
        value=body();mutate(value)
        assert response(value).json()=={'error':'INVALID_RESPONSE'}

def test_unknown_maneuver_code_and_traffic_warning_are_retained_without_inventing_turns():
    value=body();value['routes'][0]['sections'][0]['guides'][1]['type']=987
    value['routes'][0]['sections'][0]['roads'][0]['traffic_state']=6
    data=response(value).json()['guidance']
    assert data['maneuvers'][1]['type']==987
    assert data['segments'][0]['traffic_state']==6

def test_section_offsets_and_road_indices_are_global_not_restarted():
    value=body();section=value['routes'][0]['sections'][0]
    value['routes'][0]['sections']=[section,section]
    value['routes'][0]['summary']['distance']=2000
    value['routes'][0]['summary']['duration']=240
    data=response(value).json()['guidance']
    assert data['maneuvers'][-1]['offset_meters']==2000
    assert data['maneuvers'][-1]['road_index']==3
