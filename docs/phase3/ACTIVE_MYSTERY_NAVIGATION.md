# Phase 3B-3 REVISED — 지도 기반 미스터리 내비게이션 통합 검증

검증일: 2026-11-09. **READY_WITH_LIMITATIONS / DESTINATION_PRIVACY = PARTIAL**.
Pleos 공개 지도 재사용은 **NOT_EXPOSED(NaviHelper 3.0.5 공개 범위)**, 별도 OEM·파트너 지도 SDK는 **UNVERIFIED**다. 독립 지도·활성 안내는 정차 Pleos Connect v2.1.0/API 34 에뮬레이터에서 동작했다. 실차·실도로 안전성이나 앱마켓 승인을 의미하지 않는다.

## 1. 중단 작업 복구와 Git

- 브랜치 `main`, 시작 HEAD `ae83519b7a28058f6c9053665e01bf9c4799fdb3`.
- 최초 3B-3 시작은 clean이었다. REVISED 재개 시에는 이전 작업의 미커밋 지도·포커스·도착·전달 코드가 존재했다. 이를 삭제·되돌리지 않고 검사·수정·재사용했다.
- 재사용: `FollowCamera`, `TileStore`, `RoadMapRenderer`, native Focus/OEM monitor, 기존 `NavigationOwnershipAdapter`, `PrivacyTripFactory`, `RouteMatcher`, 위치 구독, FastAPI 및 MVP 추천 로직.
- 기존 MainActivity/Vehicle/NaviHelper 진단 기능, DEMO, 정적·GPS·재생 PoC 및 과거 보고서는 보존했다. 기존 빌드 스크립트와 SDK/Gradle/AGP 버전을 변경하지 않았다.
- 커밋·Push·원격 변경·reset/clean/강제 checkout을 수행하지 않았다. 최종 HEAD도 동일하다.

## 2. Pleos 지도 우선 조사와 공급원 선택

[별도 공개 계약 감사](PLEOS_MAP_REUSE_AUDIT.md)에 공식 문서와 실제 AAR 공개 시그니처, Google/MapLibre/OSM 대안 비교를 기록했다.

NaviHelper는 기본 Navigation 앱에 요청을 전달하고 정보·이벤트를 받는다. 공개 API에는 기본 지도 View/Surface/타일을 반환하는 계약이 없다. `NaviContainer`는 APP_MAPS 앱을 배치하는 시스템 영역이며 지도 SDK가 아니다. `NAVI_CUSTOM_MAP`은 문서상 화면 요청 권한이다. 오버레이 실패를 이 결론의 근거로 사용하지 않았다. [NaviHelper 소개](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/intro), [Pleos Maps/Navigation 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide)

따라서 중단 작업의 **OSM 표준 raster + Canvas**를 재사용했다. AndroidX Surface는 그리기 표면이며 지도 데이터를 공급하지 않는다. 새 상용 지도 SDK나 키를 도입하지 않았다. [Android 지도 Surface 계약](https://developer.android.com/training/cars/apps/library/draw-maps)

- OSM HTTPS 공식 endpoint, 식별 가능한 SURPRIDE User-Agent, 현재 보이는 viewport만 요청, 네트워크 작업자 2개.
- HTTP freshness/조건부 요청과 헤더 부재 시 최소 7일의 앱 내부 캐시. 전체 경로·다중 축척 선다운로드, 오프라인 팩·타일 재배포 없음.
- 지도 안에 `© OpenStreetMap contributors`, `openstreetmap.org/copyright · ODbL` 표시. 일반 도로·지명·POI 라벨은 유지한다.
- 타일 미수신·부분 실패·오래된 캐시를 별도 표시한다. 서버는 SLA가 없으며 운영 규모·오프라인 용도에는 다른 공급 계약이 필요하다. [OSMF 타일 정책](https://operations.osmfoundation.org/policies/tiles/), [OSM 저작권](https://www.openstreetmap.org/copyright)
- 카카오 경로 사용과 OSM 지도 사용은 별도다. REST 경로 계약은 도로 좌표·안내 정보를 제공하며 별도 카카오 지도 SDK의 사용권을 취득한 것으로 보지 않는다. 상용 차량 배포·혼합 사용·데이터 보관 조건은 출시 전에 추가 확인한다. [자동차 길찾기 응답 계약](https://developers.kakaomobility.com/guide/navi-api/directions)

## 3. 구현 구조와 사용자 흐름

기존 MVP 조건 → Data Lab의 기존 DestinationEngine/RouteRecommendationEngine → 기존 FastAPI 실제 경로 평가 → 선정 코스의 full guidance 재조회 → 비공개 준비 packet → 별도 PoC → 명시적 실험 시작 → 공식 Focus 획득 → NavigationManager → DEVICE_API 위치/경로 매칭 → 도착 후보·정차 확인 → 안내 종료·공개 → 메인 평가·기록 순서다.

`RouteProvider` 인터페이스는 유지했다. 추가 `postGuidance`만 `include_guidance=true`를 보낸다. 기존 일반 POST/DEMO 경로는 그대로다. 출발지는 아직 **고정된 공개 개발 좌표**이며, 실제 GPS는 PoC 추적에 사용한다. GPS 출발지 기반 추천으로 완성됐다고 표시하지 않는다.

전달 파일은 main 앱 cache의 제한된 FileProvider 경로에 저장한다. exported=false, 읽기 권한만 특정 PoC에 부여하고 Intent에는 임의 URI만 넣는다. 목적지·좌표·경로·키를 extras에 넣지 않는다. 1시간 TTL·최대 크기·출처·좌표 일치·guidance 존재를 검증하며 PoC는 허용된 authority만 가져온다.

결과는 일회성 pending nonce/candidate/source/공개·중지 플래그를 검증한다. 정차 상태에서 직접 평가를 골라 저장하며 nonce를 한 번 소비한다. 기록은 공개 장소 ID·평가·시각뿐이다. API 키·GPS 궤적·경로 JSON을 기록하거나 백업하지 않는다. DEMO 기록과 **LIVE AVD 검증 기록**을 분리하고 기존 기분 지도에도 별도 카드로 연결했다. AVD 기록을 실제 방문 또는 실제 기분 변화로 해석하지 않는다.

## 4. 지도·Follow Camera·UI

| 항목 | 구현·실제 확인 |
| --- | --- |
| 도로 배경 | OSM 도로·교차로·도로명·주변 지형이 Surface에 표시됨. 단색/geometry만의 화면이 아님 |
| 현재 위치 | 공식 LocationManager의 DEVICE_API 위치, 오차·신선도 검증. AVD console 재현임을 구분 |
| 방향·카메라 | 차량 아래쪽 중심, heading-up, 방향/축척 완화. GPS 방향이 없으면 경로 방향 **추정**으로 명시 |
| 경로선 | 속도·축척·다음 안내를 고려한 국소 전방 구간. 종점 핀/전체 overview 없음, 말단 표현은 점차 소거 |
| 안전 정보 | 다음 회전·거리, 도로명, 남은 거리·시간, GPS 상태, 중지 제공. 불확실 위치에는 정확한 TBT를 만들지 않음 |
| ETA | API 기록 시간에 남은 거리 비율을 적용한 **추정**. 실시간 교통 재조회 또는 host 최종 ETA가 아님 |
| 카드 잘림 | 잘리던 host 소유 최종 ETA 카드는 추가하지 않고 stable/visible Surface 안에 출처 표시한 자체 ETA 배치 |
| 야간 | host dark-mode 값에 따른 raster 색상 처리 구현. 실제 야간 전환 육안 검증은 미완료 |

실제 스크린샷에서 지도와 다음 안내가 동시에 읽히고 footer가 화면 안에 들어오는 것을 검토했다. raster를 회전하므로 일부 지도 라벨도 회전한다. 목적지 인근에서는 현재 위치·일반 라벨로 장소를 추측할 수 있다. 차선·도로 위험 데이터, 교통 overlay, 교차로 확대도는 구현하지 않았다.

## 5. NavigationManager와 경로 소유권

AndroidX Car App Library **1.7.0**, 실제 호스트 `com.android.car.templates.host`, 협상 API **4**. 기존 NAVIGATION/APP_MAPS/Surface 선언을 유지했고, PoC에 INTERNET와 조회 전용 `NAVI_ROUTE`, 기존과 동일한 NaviHelper 3.0.5 의존성을 추가했다.

공식 `CarAppFocusManager` callback별 lease와 `isOwningFocus` 확인을 사용한다. 초기 타 앱 소유권을 조회하는 공개 API가 없어 초기 값은 UNKNOWN이다. 실험에서는 읽기 전용 AppFocusService dump가 비활성임을 확인하고, 신선한 NaviHelper OEM 경로 상태=false, 물리 display의 정차 UX 확인, 다른 안내·Phone Projection 종료를 확인하는 사용자의 별도 시작 행위를 요구했다. Phone Projection의 완전한 프로그램적 탐지는 미검증이다. [공식 Focus 계약](https://developer.android.com/reference/android/car/CarAppFocusManager)

기존 기본 CLOSED gate는 유지한다. 새 실험 화면만 이번 사용자의 정차 AVD 활성 실험 승인과 **공모전 허용이라는 개발 가정**을 명시적으로 사용한다. 이 가정은 공식 앱마켓 승인이나 시스템 권한을 대체하지 않는다. 권한 오류·OEM 경로 활성/불명·비정차에는 시작하지 않는다.

실제 `getCarService(NavigationManager)`, callback 등록/해제, `navigationStarted`, `updateTrip`, `navigationEnded`를 연결했다. 늦은 Focus grant에서도 GPS·정차·OEM·경로 신선도를 다시 검사한다. 소유권 없는 Trip 갱신, 자동 시작, 자동 경로 전환, 다른 내비 취소, 음성 안내는 없다. [Android NavigationManager](https://developer.android.com/reference/androidx/car/app/navigation/NavigationManager)

| 검증 | 결과 |
| --- | --- |
| 최초 실행·준비 | 자동 활성화 없음, Focus 활성 집합 `{}` |
| 사용자 시작 | request 반환 true → grant 확인 → ACTIVE |
| 실제 소유권 | `mActiveAppTypes:{1}`, 해당 PoC client owned `{1}` 확인 |
| 목적지 없는 Trip | Destination 수 0, 반복 `updateTrip` 정상 반환. 예외 없이 active 유지 |
| Trip 내부 소비 확인 | 별도 수신 ACK/host 내부 Trip 표시 증거는 없음(**UNVERIFIED**). 자체 NavigationTemplate TBT 표시와 Trip 하위 소비는 별도 계약 |
| 실제 화면 | native 다음 안내·거리 표시, GPS 이동 후 안내 전환·거리 갱신 확인 |
| 사용자 즉시 공개 | 요청 후 Destination 수 1의 정상 데이터로 전환, ACTIVE 유지 |
| 사용자 중지·도착 확인 | navigationEnded·lease 정리, Focus `{}`/owned `{}`로 복귀 |
| host가 보낸 onStopNavigation | 공식 callback·단위 테스트 연결 완료. 실제 host의 중지 요청 발생 자체는 **미검증** |
| 최종 ETA의 시스템 전파 | destination 없는 Trip에 최종 destination estimate를 넣지 않음. cluster/HUD 최종 ETA는 **미검증** |

API 반환은 host가 예외 없이 처리했다는 증거이며, 모든 시스템 채널에 Trip을 표시·보관했는지까지 증명하지 않는다.

## 6. 실제 경로·GPS·도착 검증

- 실제 서버 키는 기존 환경변수/서버에만 있다. 값은 출력하지 않았다. 실경로 API full-guidance 요청 성공: 한 공개 검증 snapshot에서 **7,437m / 1,537초 / 도로 구간 8 / 안내 9 / geometry 122점**. 이후 MVP 재조회 시간은 교통·조회 시점에 따라 달랐다.
- main 실제 장소 후보 평가 결과의 LIVE_API 준비 화면, 왕복·체류·여유 시간이 포함된 추천, 선택 코스 private 전달을 확인했다.
- 공식 `adb emu geo fix`로 공개 경로의 일부 구간을 재현했다. geometry 진행 약 **1,628m → 3,116m**, 다음 거리 약 **237m → 다음 안내 수 km**로 전환, 남은 거리·시간과 카메라 갱신을 확인했다. 이는 에뮬레이터 DEVICE_API 수신이며 실도로 주행 결과가 아니다.
- 종점 접근은 **별도 실험 세션**으로 120m 전부터의 위치와 반복 정지 샘플을 재현했다. 전체 경로를 실제 주행한 것으로 계산하지 않는다.
- 도착은 ON_ROUTE·신선한 여러 샘플(3개/최소 2초)·남은 경로·종점 근접·정확도·가능한 속도 정보와 정차 UX를 조합한다. 자동 확정하지 않고 정차 확인 버튼을 요구한다. 이는 개발 임계값이며 주차/차량 진입 인증이 아니다.
- 목적지를 숨긴 도착 후보 → 정차 확인 → STOPPED/공개 → main의 ‘도착 후 공개·중지 결과’를 실제 확인했다. 도착 후 GPS 재구독을 막았다.
- 경로 이탈·낮은 정확도·오래된 위치·손실·늦은 grant·만료 데이터는 단위 테스트로 검증했다. 실제 전파 장애·실차 역주행/교차로/터널·OEM 활성 중 강제 충돌 실험은 하지 않았다.
- AVD에 공개 경로 밖 위치를 재현했을 때 **OFF_ROUTE**, 진행·다음 거리 없음, native TBT 제거와 포커스 `{}`를 실제 확인했다. 위치 복구 후에도 자동 활성화하지 않으며 새 명시적 시작에서 추적기를 초기화한다. 이는 실제 GPS 장애·자동 재탐색 검증과는 다르다.

## 7. 목적지 정보 노출

| 채널 | 판정 | 범위 |
| --- | --- | --- |
| SURPRIDE NavigationTemplate | PASS | 공개 전 이름·주소·절대 좌표 없음. 회전/도로 정보 유지 |
| 지도 Surface | PASS(관찰 화면 한정) | 자체 목적지 핀·카드·종점·overview 없음. 일반 지도 라벨/위치 추론은 제한하지 않음 |
| AndroidX Trip | PASS(전송 payload) | 공개 전 Destination 0, Step/Maneuver/step estimate만. 최종 좌표 필드 없음 |
| Pleos 홈 | UNVERIFIED | 시스템 전체 홈 노출·보관 동작을 확인하지 않음 |
| 계기판/HUD | UNVERIFIED | 지원/표시·메타데이터 전달을 실제 확인하지 못함 |
| 시스템 알림 | UNVERIFIED | 모든 host/시스템 알림 채널을 확인하지 못함 |
| 음성 | UNVERIFIED | 이번 PoC는 음성을 시작하지 않음. 무음이 시스템 비공개 증거는 아님 |
| 최근 목적지/기록 | UNVERIFIED | 자체 journal 외 host/OEM 이력 저장 여부 미확인 |
| 앱 복귀 | PASS(비자동 재시작) | 중지·Foreground 전환 후 새 사용자 시작 필요. 활성 세션 자동 복원 없음 |

즉시 공개를 요청한 이후의 정상 이름 표시는 LEAK로 집계하지 않는다. 일반 OSM 라벨에 목적지 이름이 존재할 가능성은 남아 있다. 따라서 **PARTIAL**, 시스템 전체 CONFIRMED가 아니다. 보호된 OEM 화면 캡처·내부 파일 조회·수정이나 숨김 권한 우회를 사용하지 않았다.

## 8. 테스트·실패 수정·실행 증거

| 대상 | 기존 | 최종 | 결과 |
| --- | ---: | ---: | --- |
| app | 89 | 95 | PASS |
| nav-host-poc | 60 | 91 | PASS |
| FastAPI/검증 도구 | 33 | 37 | PASS |
| 합계 | 182 | **223** | 실패/삭제/무력화 없음 |

표준 실행:

```powershell
.\scripts\build-codex.ps1 -Test
.\scripts\build-nav-host-poc.ps1 -Test
.\backend\.venv\Scripts\python.exe -m pytest backend/tests -q -p no:cacheprovider
```

신규 검증은 route packet/TTL·nonce 재사용, 카메라 좌표/방향 획득/viewport·지도 캐시 실패, lease 중복/늦은 승인/정리, OEM UNKNOWN, 도착 샘플, 지도 버튼 계약, 공개 경로 재현 범위를 포함한다. 네트워크/지도 공급자 전체를 Mock 테스트 성공으로 대체하지 않았다.

실패를 발견·수정했다: 경도 역변환의 180도 오류, 최초 UNKNOWN 뒤 방향 획득, 지도 action strip의 금지된 텍스트 제목, Surface 교체 시 기존 정적 화면 lockCanvas 경쟁, API nullable 반환/Android JSON 테스트 계약, 초기 GPS와 시작 승인 순서. 초기 APK에서 실제 크래시 2건이 있었으며 최종 안정 구간과 구분한다. 최종 크래시/ANR 검사·artifact와 metadata 요약은 [검증 증거](evidence/active-navigation/)에 둔다. JVM native-access/기존 deprecated API와 FastAPI 테스트 라이브러리 경고는 기능 실패로 집계하지 않았다.

에뮬레이터는 `emulator-5554`, Android 사용자 10, API 34, P/0km/h. 두 debug APK 설치·메인 실행·CarAppService 실행·원래 SDK 진단 기능을 확인했다. 아래 화면은 모두 공개 장소·개발 좌표 기반이며 개인 주행 위치를 담지 않는다.

| 증거 | 내용 |
| --- | --- |
| [01](evidence/active-navigation/01-prepared-live.png) | 실제 경로 준비·목적지 비공개 |
| [02](evidence/active-navigation/02-active-secret-map.png) | 실제 도로 지도+native 안내+비공개 |
| [03](evidence/active-navigation/03-turn-follow-camera.png) | GPS 이동·다음 안내 전환·Follow Camera |
| [04](evidence/active-navigation/04-explicit-reveal.png) | 사용자 즉시 공개 중 안내 유지 |
| [05](evidence/active-navigation/05-rating-recorded.png) | 별도 LIVE AVD 평가 저장 |
| [06](evidence/active-navigation/06-arrival-candidate.png) | 목적지 비공개 도착 후보 |
| [07](evidence/active-navigation/07-arrived-revealed.png) | 정차 확인·도착 후 공개·종료 |
| [08](evidence/active-navigation/08-mood-atlas-live-records.png) | 기존 기분 지도와 별도 AVD 기록 |
| [09](evidence/active-navigation/09-final-apk-guidance.png) | 수정 APK의 지도·현재 도로·거리·ETA |
| [10](evidence/active-navigation/10-final-turn-road.png) | 회전 화살표·km 단위·도로명과 배경지도 동시 표시 |
| [11](evidence/active-navigation/11-unreliable-position-stopped.png) | OFF_ROUTE에서 안내·소유권 정리 |
| [12](evidence/active-navigation/12-final-build-active.png) | 최종 APK 재설치 후 실제 소유권·목적지 없는 Trip·지도 안내 |
| [13](evidence/active-navigation/13-explicit-recovery.png) | 경로 이탈/재진입 뒤 새 사용자 요청으로 정상 재개 |

일부 증거는 검증 중간 APK로 찍혔다. 최종 APK 지도·안내 재실행 증거 및 SHA256을 별도로 남겨 변경 후 실행과 구분한다.

[최종 요약 JSON](evidence/active-navigation/validation-summary.json)에 APK SHA256·테스트 수·검사 범위를 기록했다. 최종 실행 검사 구간(에뮬레이터 시각 15:54 이후)에서 대상 앱/host Fatal 및 ANR 로그는 **0건 발견**했다. 초기 실패 기록을 삭제하거나 최종 0건에 합치지 않았다. [최종 metadata 로그](evidence/active-navigation/final-runtime-metadata.txt)는 수신 수·진행 거리·상태 등만 포함한다. [전체 변경 경로](evidence/active-navigation/changed-files.txt)도 확인할 수 있다.

종료 시 안내와 Focus를 해제하고 메인 기분 지도 화면으로 복귀했다. 개발 프록시는 정상 종료했으며 host의 단기 session 파일이 제거되고 8765 포트가 더 이상 listen하지 않는 것을 확인했다. 사용자가 제공한 `.env`와 지도 캐시는 삭제하지 않았다.

재현할 때는 먼저 두 APK를 빌드/설치하고, 별도 터미널에서 `python -m backend.dev serve --mode live`를 실행한다. 기존 절차대로 `backend.dev provision-android --adb <adb 경로>`로 앱에 단기 설정을 전달한다. 메인 앱 → DEMO 시작 → 실제 장소·경로 검증에서 조건을 맞춰 LIVE 코스를 준비한다. PoC에서는 OEM/Phone Projection 종료·정차 확인 후 **직접 시작**한다. 경로 재현은 `backend.gps_replay --prepared-public-catalog --scenario prefix --adb <adb 경로>`를 사용한다. 공개 후보·고정 출발지 검사를 통과하는 경우만 재현할 수 있다. 서버가 없거나 packet이 만료되면 새로 준비해야 하며 임의 DEMO 전환이나 자동 재시작은 없다.

## 9. 변경 파일과 남은 작업

- main 변경: Manifest/backup 규칙, `LiveRouteProvider`, `RealRouteActivity`, 기분 지도에 분리된 AVD 카드.
- main 추가: `PreparedRouteContract`, `PreparedNavigationHandoff`, `NavigationFileProvider`, `LiveNavigationStore`, `LiveNavigationResultActivity`, provider paths, 계약 테스트.
- PoC 추가: `ActiveMysteryScreen`, `ActiveRoutePacket`, `map/` 3개, native Focus/OEM 경계 4개와 `ReadinessFocusPort`, `ArrivalGate`, 공개/회전 아이콘과 신규 테스트.
- PoC 변경: 기존 Service에 명시적 실험/URI 진입 추가·Surface 경쟁 방어, GuidanceRoute의 내부 종점 거리 계산, Manifest/동일 NaviHelper 의존성.
- backend: 공개 경로만 허용하는 연속·정지·종점 접근 재현과 4개 테스트. 비밀 키·server 인증 구조는 유지.
- 문서/증거 추가, pytest 임시 폴더 ignore 추가. 상세 최종 파일 목록은 evidence의 Git 파일 목록에 남긴다.

Phase 3B-4 우선순위:

1. Pleos 공식 지도 임베드 파트너 경로·독립 안내 심사·목적지 없는 Trip의 시스템 전파 계약 확인. Phone Projection/OEM 사전 상태 탐지의 공식 보완 경로 확보.
2. 운영용 지도 공급 계약·오프라인 실패 전략과 vector 지도 검토. 회전 라벨 가독성·정식 dark-mode 육안 검사, 지도/경로 로딩 실패 시 안내 시작 차단 정책을 강화한다.
3. 실제 GPS 출발지, 안전한 차량 진입점/주차 검증, 경로 진행 도메인과 main 상태 복원 통합. 활성 안내는 재시작 때 자동 복원하지 않는다.
4. host의 onStopNavigation 실발생·소유권 상실·오류 재연결·통신 장애를 정차 AVD에서 추가 검증하고 홈/알림/cluster/HUD/이력을 채널별로 검사한다.
5. 재탐색·교통 ETA 재조회·차선/위험 정보·승인된 음성·명시적 복귀 경로 준비를 추가한다. 이번에는 구현하지 않았다.

현재는 정차 AVD에서 지도·활성 안내·공개·종료·평가를 시연할 수 있는 통합 개발 PoC다. 실차 사용을 보장하는 내비게이션 제품으로 배포하지 않는다.
