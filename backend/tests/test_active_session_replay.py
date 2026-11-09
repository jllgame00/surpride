import pytest
from backend.active_replay import validate_session, sample_offsets
from backend.smoke import ORIGIN, DESTINATION

def packet():
    return dict(development_public_replay=True,trip_origin=ORIGIN,trip_destination=DESTINATION,
        origin=ORIGIN,destination=DESTINATION,source='KAKAO_DIRECTIONS',measured_at_epoch_ms=100000,
        guidance={'segments':[{'geometry':[[126.97,37.57],[126.98,37.58]]}]})

def test_only_opted_in_public_development_session_can_be_replayed():
    assert len(validate_session(packet(),100100))==2
    for key,value in [('development_public_replay',False),('trip_origin',DESTINATION),('measured_at_epoch_ms',1)]:
        bad=packet();bad[key]=value
        with pytest.raises(ValueError): validate_session(bad,4_000_000 if key=='measured_at_epoch_ms' else 100100)

def test_return_and_rerouted_origin_keep_original_public_session_scope():
    data=packet();data['origin']={'longitude':126.9665,'latitude':37.5659}
    assert validate_session(data,100100)
    data['returning']=True;data['destination']=ORIGIN
    assert validate_session(data,100100)
    data['destination']={'longitude':0.0,'latitude':0.0}
    with pytest.raises(ValueError): validate_session(data,100100)

def test_finish_approach_does_not_assert_real_road_traversal_or_exact_endpoint():
    offsets=sample_offsets('finish',1000,0,8)
    assert offsets[-4:]==[995]*4 and max(offsets)<1000
    with pytest.raises(ValueError): sample_offsets('hold',1000,2000,8)

def test_arrival_approach_preserves_continuity_before_the_final_repeated_fixes():
    offsets=sample_offsets('arrival-approach',1000,0,40)
    assert offsets[0]==400 and offsets[-4:]==[995]*4
    assert all(0<=b-a<=20 for a,b in zip(offsets,offsets[1:]))

def test_prepared_catalog_scope_requires_explicit_option_and_exact_public_candidate():
    from backend.gps_replay import PUBLIC_CATALOG
    data=packet();data.pop('development_public_replay')
    data['candidate_id']='seoul-night-15'
    lon,lat=PUBLIC_CATALOG[data['candidate_id']]
    data['destination']=data['trip_destination']={'longitude':lon,'latitude':lat}
    with pytest.raises(ValueError): validate_session(data,100100)
    assert validate_session(data,100100,True)
    data['candidate_id']='unknown'
    with pytest.raises(ValueError): validate_session(data,100100,True)
