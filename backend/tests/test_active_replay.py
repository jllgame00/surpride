import pytest
from backend.gps_replay import validate_public_route, replay_distances
from backend.smoke import ORIGIN, DESTINATION

def packet():
    return {'origin':ORIGIN,'destination':DESTINATION,'source':'KAKAO_DIRECTIONS','measured_at_epoch_ms':100000,
            'guidance':{'segments':[{'geometry':[[126.97,37.57],[126.98,37.58]]}]}}

def test_public_replay_refuses_unapproved_or_expired_location_data():
    assert len(validate_public_route(packet(),100001,False))==2
    bad=packet();bad['destination']={'longitude':0,'latitude':0}
    with pytest.raises(ValueError): validate_public_route(bad,100001,True)
    with pytest.raises(ValueError): validate_public_route(packet(),4_000_000,False)

def test_end_approach_is_explicit_separate_reproduction_with_stationary_samples():
    distances=replay_distances('end-approach',1000)
    assert distances[0]==880
    assert distances[-4:]==[995]*4
    assert max(distances)<1000

def test_hold_start_never_claims_route_traversal():
    assert replay_distances('hold-start',1000)==[100]*8

def test_forward_replay_can_continue_without_resetting_an_active_matcher():
    values=replay_distances('prefix',8000,1640)
    assert values[0]==1640 and values[-1]<=3140
    with pytest.raises(ValueError): replay_distances('prefix',1000,2000)
