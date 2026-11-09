# Phase 4B — 경로·목적지 통합 추천

검증일 2026-11-09. Pleos Connect v2.1.0/API 34, 정차 P/0km/h 에뮬레이터 개발 검증이다. 실차 주행 검증이 아니다.

## 판정

- **READY_WITH_LIMITATIONS**: 실제 도로 복수 경로의 시간·거리·형상 비교와 기존 추천/내비 연결은 구현했다. 실제 경관 경유 안내는 검증하지 못했다.
- **ROUTE_QUALITY_EVIDENCE = PARTIAL**: 실제 도로·시간·거리 근거는 확보했으나 도로에서 보이는 경관·차량 진입·운영/이용권 근거가 부족하다.
- **STT = PARTIAL**: READY/RMS callback은 수신했지만 비어 있지 않은 completed=true는 0건.
- **DESTINATION_PRIVACY = PARTIAL**: 자체 비공개 화면과 Trip을 검증하며 기존 시스템 전 채널 미검증 상태를 승계한다.
- Phase 5: 조건부 진입 가능. 시각 디자인 작업은 가능하지만 경관 경유·STT·실차 안전성·운영 이용권이 완성됐다는 전제로 출시할 수 없다.

## 1. 시작 상태와 기존 구조

시작 Git은 `main`, `5782ce8e4c48a086e20fca1ced7f398b9ea38c3b`, clean이었다. 사용자가 Phase 4A를 커밋한 상태를 확인하고 시작했다. 종료 시 HEAD/브랜치 그대로, 이번 변경은 미커밋 상태다. 커밋·Push·reset·clean·강제 checkout·전역 설정 변경 없음.

기존 DestinationEngine은 기분/장소/동행/최근 방문과 시간·검증된 차량 range를 필터링/점수화한다. 기존 RouteRecommendationEngine은 목적지별 기본 경로와 별도 복귀편을 조회한다. 이번에는 이를 재작성하지 않고 `DriveExperienceEngine`이 기존 목적지 점수를 재사용하면서 실제 경로 선택지를 평가한다. 기존 선택·자연어·DEMO·MOCK·Vehicle/NaviHelper·지도·GPS·재탐색·TTS·복귀·기록·검증 문서는 보존했다.

## 2. 경관 데이터와 신뢰도

기존 서울시 공개 장소 5곳을 유지한다. [서울시 야경명소 정보 OA-22579](https://data.seoul.go.kr/dataList/OA-22579/S/1/datasetView.do)의 이름·대표 좌표·유형과 출처를 사용하며 공공누리 1유형 출처 표시를 유지한다. 기분 태그는 SURPRIDE의 편집 가설이며 사실로 검증된 심리 효과가 아니다. 대표점이 차량 진입점이라고 가정하지 않는다.

`ScenicWaypoint`는 ID/비공개 이름/대표점/진입점/태그/경관 근거/접근 상태/진입 근거/이용권 확인/출처/확인 날짜를 분리한다. VERIFIED/INFERRED/UNKNOWN은 독립된 사실의 확인 수준이다. 자료 확인 자체가 운전 중 조망을 검증한 것은 아니다.

| 연구 후보 | 확인한 사실 | 미확인·제한 | 실제 경유 |
|---|---|---|---|
| 북악스카이웨이·북악팔각정 | 운영 주체가 전망·드라이브 코스·시설 주차장을 안내 | 2026-06-08부터 공사 주차 제한 공지, 재개일/차량 진입 좌표/재사용 조건 UNKNOWN | 차단 |
| 하늘공원·난지천공원 주차 접근 | 서울시가 공원 전망과 주차장 자동차 접근을 안내 | 공원 대표점은 진입점 아님. 정확한 진입 좌표/도로상 조망/현재 운영·이용 조건 UNKNOWN | 차단 |

근거: [종로구시설관리공단 시설 안내](https://www.ijongno.co.kr/www/125), [공사·주차 제한 공지](https://www.ijongno.co.kr/www/139?action=read&action-value=03c466b9a93a003efb3a8145c4ff83cf), [서울시 하늘공원 안내](https://parks.seoul.go.kr/park/detailView.do?pIdx=6), [공식 월드컵공원 지도](https://parks.seoul.go.kr/maps/worldcup/worldcuppark_map_KR.pdf).

`PendingScenicCatalog`는 이 두 후보의 연구용 사실·출처·checkedDate=2026-11-09만 기록한다. 기사/사진/지도 이미지를 앱 자산으로 수입하지 않았다. `entryPoint=null`, 이용권 확인 false여서 `routingCandidates`는 **0곳**이다. 주차 시설이 존재한다는 사실과 현재 주차 가능·차량 접근 가능을 구분한다. 운영 출처가 All Rights Reserved이거나 개별 자료 이용권이 미확인인 항목을 운영용 경관 데이터로 승격하지 않았다. 공식 도로상의 안전한 경유 좌표도 추가로 확보하지 못했다.

## 3. 실제 경로 후보 생성 계약

[Kakao 자동차 길찾기](https://developers.kakaomobility.com/guide/navi-api/directions)의 `GET /v1/directions`, `priority=RECOMMEND/TIME/DISTANCE`, `waypoints`만 사용한다. 공식 최대 경유지는 5개지만 이번 프록시는 **최대 2**, 엔진은 **최대 1개**로 더 작게 제한한다. 경유 좌표는 경도·위도 순서, 여러 개는 `|`로 연결한다. `summary=false`, `alternatives=false`, 도로 통제 기본값을 유지하며 통제 완화 옵션을 추가하지 않았다.

기존 RouteProvider의 2점 호출은 유지한다. 새 옵션 overload는 legacy 구현이 옵션을 조용히 무시할 수 없도록 미지원 오류를 반환한다. LiveRouteProvider는 옵션을 반향한 `request_options`를 확인하고 기존 파서의 단위·신선도 검증을 재사용한다. 실패/취소/미설정/인증/쿼터 오류는 구분하며 인증·쿼터·설정 오류 및 인터럽트 발생 시 나머지 조회를 중단한다.

프록시는 기본 호환 schema_version=1에 request_options를 추가했다. 알 수 없는 옵션/타입·중복 좌표·endpoint와 같은 경유·3개 이상 경유를 거부한다. 경유 요청이면 section 수와 summary waypoint 좌표 순서를 확인한다. guidance는 기존 전역 road index/누적 거리·도로명·traffic_state를 유지하고 POI 이름/raw guidance 문장은 전달하지 않는다.

MOCK 서버에서도 옵션·guidance 계약을 제공한다. source=MOCK, 합성 직선 geometry, 무명 도로, 총 거리 4200m와 기존 비대칭 시간 540/780초다. 이는 도로/교통 응답이 아니며 LIVE handoff 대상이 아니다.

## 4. 통합 평가·제약

`Hard Constraints → Shortlist → Route Requests → Route Evidence → Final Selection` 순서다.

- 선호/제외 충돌, 필수 장소 조건, 최근 방문, 동행 검증, 출발지와 동일 목적지를 먼저 필터링한다. 실제 카탈로그의 동행 검증은 기존 SOLO 제한을 유지한다.
- 기존 기분 적합성과 ID로 최대 2개 목적지를 선별한다. 목적지당 3우선순위 + 검증 경유 1종 + 독립 복귀편 1회로 최대 10회(절대 가드 12회)다. 5~10곳 전체에 조합을 늘리지 않았다.
- LIVE는 실제 geometry 최소 2점과 요청 옵션 일치를 요구한다. 왕복이면 별도 복귀 응답을 필수로 받으며 출발편의 2배로 추정하지 않는다.
- **출발 초 + 복귀 초 + 체류 초 + 여유 초**를 합산한다. 총 사용 시간과 귀가 시각 중 더 짧은 예산을 초 단위로 적용한다. 마지막 조회 후 신선도/현재 deadline을 재검사한다.
- 준비 시작·worker 종료·실제 handoff 직전에도 캐시된 계획의 양방향 신선도와 현재 귀가 제한을 재확인한다. 느린 응답/사용자 대기 후 예산을 초과한 코스를 보내지 않는다.
- 검증된 Vehicle range가 있으면 기존 DestinationEngine이 제한한다. SOC/연료/거리 UNKNOWN에서 임의 range를 만들지 않는다.
- geometry가 같은 우선순위 응답은 중복 처리한다. 다른 priority 이름만으로 다른 코스라고 주장하지 않는다. 경유의 시간/거리 우회가 기본 비교 최솟값의 1.6배를 넘으면 제외한다. 이 값은 초기 제품 한도이지 안전·재미의 과학적 기준이 아니다.

알려진 접근 제한·진입 근거·이용권이 없는 경관 후보는 API 호출 전 제외한다. 기존 장소 대표점은 개발용 연결 가능성 조회만 유지하며 안전한 도착지·주차장이라고 인증하지 않는다.

## 5. 점수와 기분 가중치

DestinationScore는 기존 기분·장소·동행·계절/날씨(데이터가 있을 때)·랜덤 점수를 재사용한다. RouteExperienceAssessment는 다음 관측량을 별도로 둔다.

- timeScore: 예산 대비 여유. 최단 시간이 곧 최고의 경험이라고 단정하는 값이 아니다.
- repeatScore: 같은 geometry edge의 반복 비율 기반. 원래 도로의 정당한 반복/진입 제약은 해석할 수 없고 공간 분할 방식의 한계가 있다.
- detourScore: 실제 비교 경로의 거리 비율. 곡률·속도·교차로 수를 재미로 보상하지 않는다.
- sceneryScore: **검증된 경유 관련 근거가 있을 때만** 유한 값. 운전 중 보이는 풍경의 실측 품질 점수가 아니다. 태그는 water/nature/night/camping만 기존 분류에 매핑하고 선호 불일치/제외 태그/알 수 없는 태그는 가산하지 않는다.

UNKNOWN/INFERRED 경관은 null이며 0점 최저 품질이 아니다. 관측된 항목의 가중치만 다시 정규화한다. 교통 속도/상태는 경로 데이터에 보존하지만 정체가 즐거움을 얼마나 바꾸는지 검증하지 않아 별도 재미/혼잡 점수를 만들지 않았다. 양방향 전체의 중복 다양성이나 경로 주변 조망을 정밀 평가한 것도 아니다.

초기 가설(목적지/경로 비중; 경로 내부 시간/반복/우회/검증 경유 관련성):

| 기분 | 목적지 / 경로 | 시간 / 반복 / 우회 / 경관 근거 |
|---|---|---|
| 힐링 | .35 / .65 | .20 / .15 / .10 / .55 |
| 생각정리 | .35 / .65 | .20 / .20 / .10 / .50 |
| 설렘·모험 | .40 / .60 | .25 / .15 / .15 / .45 |
| 신남 | .40 / .60 | .30 / .20 / .20 / .30 |
| 위로 | .50 / .50 | .60 / .15 / .20 / .05 |

이 값은 사용자 연구로 검증된 감정 효과가 아니다. 기존 자연어 분류·선택 기분을 사용하며 별도의 자유 대화로 목적지나 도로 특징을 생성하지 않는다. UI는 내부 점수/품질 순위 대신 경로 유형·시간·비교 상태·불확실성을 설명한다. 안전 TBT에 끼어드는 감성 음성 힌트는 추가하지 않았다.

## 6. 실제 API 비교와 합성 사례

별도 공개 좌표 집계 실험에서 실제 외부 호출 **4회**, 경유 호출 **0회**. 앱의 뒤이은 한 번 평가도 출발 3우선순위+복귀편 1회였으며, 활성 중 기존 복귀 ETA 갱신 호출은 별도다. 전체 실행의 정확한 누적 상위 호출 수를 수집했다고 주장하지 않는다. 응답 전문/키/좌표는 증거에 저장하지 않고 집계와 geometry hash만 보관했다. [live-comparison.json](evidence/drive-experience/live-comparison.json):

| 종류 | 편도 초 | 편도 m | geometry 점 | 결과 |
|---|---:|---:|---:|---|
| RECOMMEND | 1335 | 7910 | 133 | TIME과 동일 |
| TIME | 1335 | 7910 | 133 | 중복 |
| DISTANCE | 1337 | 7437 | 122 | 다른 실제 도로 경로 |
| 별도 복귀 RECOMMEND | 1639 | 11149 | 215 | 반대 방향 독립 조회 |

출발편 unique geometry는 **2개**다. 체류 20분+여유 10분을 더한 왕복은 4774초/4776초로 당시 90분 예산에 들어왔다. 시점이 달라지면 ETA/경로가 바뀐다. 이 수치는 왕복 완료 시간이나 귀가 보장이 아니다. 복귀는 현재 교통 조회이며 목적지 체류 후 출발할 미래 교통 예측이 아니다.

필수 비교 A/B는 **합성 단위 테스트**로 별도 검증했다([synthetic-scenic-cases.json](evidence/drive-experience/synthetic-scenic-cases.json)). 실제 경관 경유 API 성공으로 해석하면 안 된다.

- A: 100분 예산, 기본 4200초(70분), 검증된 것으로 설정한 fixture 경유 5160초(86분) → 기분/경로 가중치에 의해 경유 선정.
- B: 같은 응답, 80분 예산 → 86분 경유 TIME_EXCEEDED, 70분 기본 선정.
- 이 두 fixture는 체류/여유 0으로 비교를 단순화했다. 별도 테스트는 599초+601초+체류20분+여유10분=3000초의 정확한 경계를 검증한다. 사용자 명세의 예시 ETA를 실제 API 관측값으로 사용하지 않았다.

## 7. 내비게이션 연결과 경유 제한

선택한 응답의 geometry/guides/옵션을 private packet으로 전달한다. 비교 후 기본 경로로 재조회해 선택을 무효화하지 않는다. nonce/목적지/private reason/왕복 조건·짧은 proxy 세션은 기존 비공개 cache/FileProvider 경로만 사용한다. 새 키/공개 무인증 서버/SDK를 추가하지 않았다.

[공식 가이드 코드](https://developers.kakaomobility.com/guide/navi-api/reference)의 1000=경유, 101=최종 도착을 구분한다. `WaypointProgress`가 수용된 ON_ROUTE 진행률에서 경유 통과/잔여 개수를 계산하고, 최종 ArrivalGate와 결합한다. 경유는 카메라의 회전 확대 대상으로 오인하지 않는다. 공식 운전 도로명/회전·위험 정보는 가리지 않는다.

**실제 경유 활성화는 CLOSED**다(`WAYPOINT_LIVE_NOT_VERIFIED`). 실제 안전한 경유 데이터가 없고 연속 경유 안내를 검증하지 못했기 때문이다. 남은 경유가 있는 자동 재탐색·수동 retry는 2점 경로로 조용히 대체하지 않고 `WAYPOINT_REFRESH_NOT_SUPPORTED`로 중단한다. 해당 경유 진행/차단/목적지·nonce 보존은 합성 계약 테스트만 통과했다. 실제 경유 통과 재현, 남은 경유를 보존하는 LIVE 재탐색은 미완료다.

경유 없는 실제 복수 경로의 선정·지도/GPS/TBT/NavigationManager 연결은 기존 활성 안내를 재사용한다. OEM route inactive/다른 focus 없음과 사용자의 명시적 일회 시작 동작이 필요하다. TTS·복귀·기록은 기존 구현을 보존하며 이번에는 새 경관 음성을 발화하지 않았다.

## 8. STT 제한 진단

[Pleos listener 계약](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners), [시작/중지](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop), 실제 AAR를 비교했다. initialize 성공 후 listener 등록 → locale → request 순서가 공식 계약과 같아 추측으로 순서를 변경하지 않았다. ON_DEVICE는 유지하며 HYBRID/SERVER 인증을 임의 추가하지 않았다. STT_SERVICE는 일반 설치 후 granted=true.

새 진단은 시도당 capped 999의 이벤트 개수, 마지막 유한 이벤트/오류만 집계한다. partial/final 원문, RMS 값, EPD byte offset, 오디오를 기록하지 않는다. stop/remove/release 전에 집계를 동결하여 종료 callback이 입력 증거가 되지 않게 한다. 실제 성공은 nonempty completed=true에만 해당한다.

실제 정차 AVD 15초 시도: INITIALIZED=1, REQUEST_RETURNED=1, READY=1, **RMS=145**, STARTED=0, EPD=0, ENDED=0, COMPLETED_NONEMPTY=0, ERROR=0, 마지막 RMS, recognitionSucceeded=false → UI NO_SPEECH, 텍스트 대체 정상. [진단 로그](evidence/drive-experience/stt-diagnostic.txt).

RMS callback은 오디오 관련 callback이 도착했다는 증거이며, 값은 버렸으므로 비무음 발화/정상 마이크 입력을 입증하지 않는다. 권한 거부나 SDK 초기화 실패는 관찰하지 않았다. 마이크 입력·발화 구간 감지·인식 엔진 중 어느 것이 원인인지 확정하지 못했다.

[공식 Pleos 에뮬레이터 설정](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)과 [Android 확장 제어](https://developer.android.com/studio/run/emulator-extended-controls)는 Extended Controls → Microphone의 Host Microphone Access 경로를 안내한다. computer-use로 확장 제어 창을 찾았지만 **창 접근 승인 시간이 초과되어 마이크 옵션을 관찰/변경하지 못했다**. 보안·OEM 설정을 우회하지 않았다. 사용자는 해당 옵션과 OS 마이크 입력을 확인한 뒤 정차 상태에서 짧은 한국어를 발화하고 completed=true/nonempty를 재검증할 수 있다. 전면 cold boot·실차 시험·반복 STT 재시도는 수행하지 않았다.

## 9. 자동 검증

| 대상 | 기존 | 최종 | 결과 |
|---|---:|---:|---|
| Android app | 155 | 187 | PASS |
| nav-host-poc | 166 | 175 | PASS |
| FastAPI | 42 | 90 | PASS |
| 합계 | 363 | **452** | 실패·오류 0 |

```powershell
.\scripts\build-codex.ps1 -Test
.\scripts\build-nav-host-poc.ps1 -Test
& backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
git diff --check
```

신규 계약/경유/기분 가중치·경관 null/시간 경계/오류 중단·취소/옵션 echo/공개 전 정보·선택 geometry 유지/준비 직전 deadline/STT 집계와 기존 회귀를 포함한다. 기존 테스트를 삭제·비활성화하지 않았다. 독립 리뷰에서 확인한 MOCK guidance 누락과 준비 직전 deadline 오류는 각각 실패 테스트 후 수정하고 GREEN을 확인했다.

기존 deprecated Car App API 및 JDK native-access 경고, FastAPI의 Starlette/httpx deprecation 경고는 남는다. 버전 업그레이드는 하지 않았다. 프로젝트 밖/불필요한 루트 전체 pytest 탐색은 기존 읽기 불가 cache 디렉터리 때문에 실패했으나 표준 `backend/tests` 90개는 통과했다. 캐시 삭제·권한 변경은 하지 않았다.

## 10. Pleos 실행 증거

최종 main 및 PoC APK의 `adb install -r` Success. 실제 자연어 텍스트 예시 → 90분/SOLO/왕복/WATER+NATURE 기존 조건 확인 → DATA LAB 통합 비교 ON → 실제 API 평가를 확인했다. 당시 앱 재조회에서는 세 우선순위 geometry가 같아 1개 ELIGIBLE/2개 DUPLICATE였고 총 81분/18.6km를 표시했다. 앞선 4회 비교의 2개 형상과 다른 시점 결과이며 혼합하지 않는다.

`01-natural-input.png`, `02-live-comparison-ready.png`는 정차 입력/비공개 결과다. 기존 공개 AVD 평가 이력이 화면에 보일 수 있으며 새 목적지를 공개한 데이터가 아니다. 실제 개인 대화·음성·GPS 원본은 포함하지 않는다.

실제 사용 흐름의 후속 결과:

1. 경유 없는 선정 코스 packet을 받아 기존 CarAppService/NavigationTemplate 실행. OEM 활성 경로 없음과 focus 없음 확인 후 명시적 시작 행을 눌렀다. NavigationManager ACTIVE, focus owned={1}, `TRIP returned=true destinations=0` 확인.
2. 공개 개발 경로의 100~280m를 공식 emulator geo 재현으로 입력. DEVICE_API 위치→진행거리/다음 안내 거리 갱신, OSM/Follow Camera/경로선/회전 안내를 함께 표시했다. [03-active-map.png](evidence/drive-experience/03-active-map.png).
3. 안내를 정상 중지해 focus 해제를 확인한 뒤 마지막 600m 근처로 옮겼다. 다시 명시적으로 시작하고 마지막 접근 구간의 35개 위치 입력을 재현했다. 연속 전체 주행이나 경유 통과 시험이 아니다.
4. 도착 후보 후 정차·도착 확인으로 STOPPED/revealed=true, focus owned={} 확인. [04-arrival-disclosed.png](evidence/drive-experience/04-arrival-disclosed.png). 최종 공개 후 선정 이유도 기존 결과 화면에 전달됐다. 긴 설명은 Surface 한 줄에서 말줄임되어 Phase 5에서 상세 배치 개선이 필요하다.
5. 기존 평가·기록 행에서 3/5 선택·저장하여 `LIVE 에뮬레이터 평가를 저장했습니다` 표시 확인. [05-result-recorded.png](evidence/drive-experience/05-result-recorded.png). LIVE AVD 기록은 DEMO 기분 지도 기록과 분리된 기존 구조이며 실도로 방문 인증이 아니다.

이번 관측 중 실행한 main/PoC PID의 FATAL EXCEPTION 및 보존 event buffer의 해당 PID Crash/ANR은 0건([stability.json](evidence/drive-experience/stability.json)). 전역 과거 로그 전체가 무오류였다는 뜻은 아니다. [navigation-events.txt](evidence/drive-experience/navigation-events.txt)는 유한 상태/Trip 반환만 보관한다. TTS는 OFF로 두었고 자동 복귀/새 음성 검증은 하지 않았다. 종료 시 focus를 해제하고 직접 띄운 개발 proxy를 중지했다.

## 11. 데이터·지도·보안

[Kakao 가격/쿼터](https://developers.kakaomobility.com/price)는 확인 시 자동차 길찾기 일10,000건, 무료 한도 초과 구간 8원/건 등을 안내하며 변경 가능하다. 실제 계정 과금·승인 범위를 조회하거나 청구하지 않았다. 기존 loopback 단일 worker auth/분당30·세션100 요청 상한, timeout, no-store, redirect 차단을 유지한다. 비교마다 앱 상한을 둬 조합 폭발을 방지한다.

[Kakao 운영정책](https://developers.kakao.com/terms/ko/site-policies)의 목적/최신성/권리 제한을 유지한다. private cache는 짧은 개발 세션 목적이며 geometry 전문을 Git 증거로 저장/재배포하지 않는다. 자동차 내장 상용 독립 내비게이션·OSM 위 geometry 사용·목적지 비공개 UX의 공급자 제휴/심사 승인 여부는 미확정이다.

기존 OSM 렌더러는 [OSMF tile 정책](https://operations.osmfoundation.org/policies/tiles/)의 출처·식별 User-Agent·HTTP freshness/최소7일 cache·대량 선다운로드 금지 정책을 유지하며 새 tile 공급/요청 경로를 추가하지 않았다. OSM 일반 도로 라벨과 주변 풍경으로 장소를 추측할 가능성은 남는다. 공용 타일은 SLA나 대규모 상용 이용을 보장하지 않는다.

외부에 기분 대화를 보내지 않는다. API 키는 기존 서버 환경변수/ignored .env에만 있고 APK·소스에 하드코딩하지 않는다. 공유 로그에는 API URL/헤더/body/좌표/토큰을 남기지 않는다. 개인정보/출발지/귀가 지점은 새 영구 저장소에 기록하지 않았다.

사용자 환경에 구성된 Kakao 키를 출력하지 않고 Git 대상 텍스트와 두 debug APK의 압축 해제 entry에서 UTF-8/UTF-16LE literal만 검사했다. 일치 0건이며 [known-key-scan.json](evidence/drive-experience/known-key-scan.json)에 결과만 저장했다. 모든 유형의 비밀·난독화된 값을 포괄 검증한 것은 아니다.

## 12. 목적지 비공개

| 채널 | 결과 |
|---|---|
| 자연어/준비 비교 모델 | PASS: generic 경로 유형·시간·상태만, 이름/주소/좌표 없음 |
| 선정 경로 private handoff | 명시적으로 private identity 포함, FileProvider/cache 경로만 사용. 공개 Intent extra나 로그 없음 |
| Template/Surface/Trip | PASS(이번 비공개 구간): 실제 화면과 destinations=0. 정차 확인 후 정상 공개. 도로 라벨/현재 위치로 추측할 가능성은 남음 |
| Pleos 홈·알림·클러스터/HUD·최근 목적지 | UNVERIFIED: 이번에 전 채널 감사를 반복하지 않음 |
| 음성 | 목적지 미발화 기존 계약 보존; STT 수신 성공/새 감성 음성 검증 없음 |

미지원/미확인 채널을 PASS로 바꾸지 않았다. 실제 목적지를 일반 도로명/위험 정보보다 우선해 숨기지 않는다.

## 13. 변경 파일

- app realdata: `RouteModels.kt`, `LiveRouteProvider.kt`, `PreparedRouteContract.kt`, `RealRouteActivity.kt`.
- 신규 app realdata/experience: `ExperienceModels.kt`, `DriveExperienceEngine.kt`, `PendingScenicCatalog.kt`.
- STT: `PleosSpeechInputSource.kt`, 신규 `SpeechInputDiagnostics.kt`.
- backend: `app.py`, 신규 `tests/test_route_options.py`.
- PoC: `ActiveRoutePacket.kt`, `ActiveMysteryScreen.kt`, `map/FollowCamera.kt`, 신규 `guidance/WaypointProgress.kt`.
- 신규/확장 테스트: RouteOptions, PreparedFeasibility, ScenicCatalog, DriveExperienceEngine, SpeechInputDiagnostics, WaypointProgress, WaypointPacketContract, FollowCamera.
- 이 보고서·구현 계획·공개 개발 집계/화면 증거. 기존 문서와 사용자 자료는 삭제/덮어쓰기하지 않았다.

## 14. Phase 5 이전 남은 작업

1. 도로 경관·진입 좌표·접근 방향·시설 운영·재사용권을 각각 검증한 실제 경유 데이터 확보. 후보를 운영 가능으로 자동 승격하지 않는다.
2. 최소 1~2개 실경유 요청의 공식1000/section geometry 검증, 정차 AVD 통과/재탐색 시험 후 CLOSED gate의 별도 해제 판단.
3. 남은 경유를 보존하는 재탐색과 실제 교통 변동·귀가 시각 검증. 현재 복귀는 같은 조회 시점의 근사다.
4. 경관 관련 점수/가중치의 사용자 평가. 도로상 시야·경관 연속성·주행 가능한 낮/밤 조건 등 추가 근거가 필요하다.
5. STT의 공식 마이크 설정/짧은 실제 발화 final 검증. 실패 시 선택·텍스트 입력 유지.
6. 시스템 전 채널 목적지 노출·정식 Pleos/지도·경로 제공업체 승인과 실차 안전성 검증은 별도 단계다.
