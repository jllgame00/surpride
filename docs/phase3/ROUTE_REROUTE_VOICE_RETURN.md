# Phase 3B-4 — 재탐색·음성·복귀 검증

검증일: 2026-11-09. **READY_WITH_LIMITATIONS / DESTINATION_PRIVACY = PARTIAL**.

정차한 Pleos Connect v2.1.0 / API 34 개발 에뮬레이터에서 검증했다. 실제 차량·실도로 주행 검증이 아니다. 기존 앱, Vehicle 2.3.2, NaviHelper 3.0.5, FastAPI, DEMO, GPS·지도·호스트 PoC와 과거 증거를 보존했다.

## 1. Git 및 변경 범위

시작: main / HEAD 21854a9aacc9462c06baa82ddbbe61080210b154 / clean.
종료: 같은 브랜치·HEAD, 이번 수정·신규 파일은 미커밋 상태. 커밋·Push·원격 변경·reset·clean을 수행하지 않았다.

기존 RouteMatcher, GuidanceParser, PrivacyTripFactory, NavigationOwnershipAdapter, OSM Surface, 추천·기록 흐름을 재사용했다. 새 지도 SDK나 차량 제어 API는 추가하지 않았다.

## 2. 실제 구현

| 기능 | 구현 및 안전 처리 |
|---|---|
| 자동 재탐색 | 기존 신선도·정확도·연속 3회 이탈 판정 재사용. OFF_ROUTE_CANDIDATE에서는 정확한 TBT를 보류하고, 확인된 OFF_ROUTE에서만 같은 내부 목적지까지 실제 프록시 요청. |
| 세션 유지 | NavigationManager에 목적지·Step 없는 Loading Trip을 전송하고 Template도 loading RoutingInfo로 전환. 포커스와 GPS 구독은 요청 중 유지. |
| 경로 교체 | LIVE 출처, 정확한 요청 종점, schema·geometry·maneuver·TTL 검증. 새 matcher가 최신 위치와 ON_ROUTE인지 확인한 뒤 교체. 실패하면 안전하게 종료. |
| 취소·한도 | 취소·generation으로 늦은 콜백 폐기. connect 3초/read 12초/전체 15초, redirect 금지, 2MB 제한. NETWORK/TIMEOUT만 30초 간격·최대 2회 자동 시도. 다른 오류는 종료·명시적 재시도. |
| 위치 상실 | TBT 보류·Loading Trip·재탐색 요청 취소. 10초 이내 신뢰도 높은 위치가 복구되지 않으면 종료. 신뢰할 수 있는 이탈 위치는 GPS 상실 타이머를 해제. |
| 음성 | 기본 OFF, 스피커 버튼으로 ON/OFF. 내비 소유권과 Audio Focus가 모두 있어야 발화. 유한한 한국어 회전·거리·상태 문구만 허용. |
| 복귀 | 원출발지·왕복 여부·실제 출발 시각·시간 예산·체류·여유를 보존. 도착 공개 후 집 아이콘/정차 메뉴에서 명시적으로 실제 복귀 경로를 준비. 별도 시작 조작과 포커스 확인 전에는 활성화하지 않음. |
| 귀가 권고 | 남은 이동 + 체류 + 복귀 API 추정 + 여유시간. 가용 시간과 귀가 시각 중 이른 deadline 사용. 복귀 추정은 4분 경과 시 갱신, 요청 간격 최소 60초. 5분 경과/없음은 UNKNOWN. AT_RISK 권고만 표시하고 목적지를 자동 변경하지 않음. |
| 도착 | 연속 위치·정확도·진행·종점 접근·저속·정차 확인과 명시적 사용자 확인 재사용. 단일 좌표 일치나 위치 점프로 확정하지 않음. |
| 재시작 | private cache의 원출발지·현재 경로·귀가 예산·실제 시작 시각 보존. 활성 소유권·음성 큐 자동 복원 없음. 새 승인 필요. 도착 후보와 완료 화면 상태 전체 복원은 미완료. |

구형 packet에 trip_origin이 없을 때도 재탐색 전 원출발지를 고정하도록 회귀 수정했다. 재탐색 위치를 복귀 목적지로 잘못 사용하지 않는다.

## 3. TTS 및 Audio Focus

표준 Android TextToSpeech는 이 AVD에서 NO_ENGINE이었다. TTS_SERVICE intent로 CAAS 서비스가 검색된다는 이유로 강제 바인딩하지 않았다.

공식 Pleos TTS SDK **ai.pleos.playground:TextToSpeech:3.3.1**, **Mode.ON_DEVICE**를 Android NO_ENGINE일 때만 선택하는 fallback을 구현했다. 공식 일반 권한 pleos.car.permission.TTS_SERVICE를 선언했다. ON_DEVICE는 앱 등록·Client Secret·네트워크 합성을 사용하지 않는다.

설치 CAAS는 4.2.10-coral-aosp(4002010), 실제 AAR의 최소 CAAS 4.1.29 이상이었다. SDK POM의 전이 의존성 ai.pleos.playground.security:universal-access:0.6.0이 기존 Maven content filter에서 제외돼 최초 resolve가 실패했다. 동일 공식 저장소에 정확한 security group만 추가해 해결했다. 기존 Gradle·AGP·Kotlin·Vehicle·NaviHelper 버전은 유지했다.

- AudioFocusRequest: USAGE_ASSISTANCE_NAVIGATION_GUIDANCE / CONTENT_TYPE_SPEECH / AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK. 실패·상실에는 발화하지 않거나 즉시 중단·OFF. 완료·중단 시 abandon.
- Android 경로는 TTS queue silence 180ms. Pleos SDK 공개 API에는 오디오 패딩/무음 API가 없어 scheduler 200ms 지연만 적용하고 SCHEDULER_200_MS로 표시한다. 오디오 수준 pre-roll과 동일하다고 주장하지 않는다.
- Pleos SDK는 utterance ID가 없어 발화마다 SDK 세션·리스너를 격리한다. token/generation으로 이전 콜백을 무효화하고, 무응답은 15초 watchdog으로 오류 처리·해제한다.
- 같은 안내 지점·접근 단계 중복 발화 제한. 재탐색·경로 교체 시 이전 큐 폐기. 중간에 시작한 거리 안내는 실제 남은 거리의 보수적 10m 단위 사용. 110m에서 500m라고 말하지 않는다.
- Directions와 UI SDK의 안내 코드를 혼동하지 않도록 페리 61/62·원톨링 85·분기 후 합류 86 등 어휘를 공식 Directions 계약과 맞췄다.
- 실제 AVD에서 ON_DEVICE 초기화, Audio Focus 획득, SDK onStart/onDone, 완료 후 포커스 반환을 확인했다. 최종 APK에서도 requested=1/started=1/completed=1/errors=0 확인.
- 발화 요청·SDK 실제 시작/완료·스피커 청취는 다르다. 청취 품질·볼륨·첫 음절 잘림·음악 ducking은 미검증이다.
- SDK 오류/요청 실패에는 controller도 OFF로 전환하며 새 위치만으로 다시 말하지 않는다. 명시적 재활성화 필요. 큐 무효화 후 실제로 말하지 않는데 SPEAKING으로 남는 진단 표시도 수정했다.

## 4. 실제 에뮬레이터 결과

대상: emulator-5554, Android user 10, com.surpride.app / com.surpride.navhostpoc만 접근했다. OEM 활성 경로 없음과 포커스 소유권 없음을 확인한 뒤 화면에서 명시적으로 시작했다.

| 시나리오 | 실제 관찰 |
|---|---|
| 활성 안내 | 실제 Kakao LIVE 응답, OSM 도로·교차로·차량 마커·전방 경로선·다음 회전 표시. 목적지 없는 Trip 호출 정상 반환. |
| 이탈·재탐색 | 공식 emulator geo fix로 공개 개발 위치 재현 → Loading Trip returned=true/owner ACTIVE → 실제 새 경로 교체 → ON_ROUTE·지도·TBT 갱신. 같은 PID·GPS 구독 유지. |
| 서버 중단 | 실제 서버 중단 → NETWORK/WAITING_RETRY → 30초 이후 2차 실패 → 안내 종료. AppFocusService mActiveAppTypes:{} 확인. |
| 복구 | 서버 복구 후 사용자 메뉴에서 재요청 → RETRY_READY/owner STOPPED/autoStart=false. 새 시작 조작으로 활성화. |
| 도착 공개 | 끝 접근 재현 → 후보 → 명시적 정차·도착 확인 → 안내 종료·목적지 공개·포커스 해제. 전체 도로를 주행한 결과가 아님. |
| 복귀 준비·시작 | 집 아이콘 선택 → 실제 복귀 API → RETURN_READY/자동 시작 없음 → 새 사용자 시작 → GPS 진행·TBT·복귀 지도 확인. |
| 최초 복귀 끝 접근 | 큰 위치 이동 뒤 재승인 시 반복/교차 geometry가 AMBIGUOUS로 판정돼 안전하게 시작 거부. 복귀 완료로 간주하지 않음. |
| 최종 복귀 완료 | 최종 APK에서 끝 600m부터 20m 간격의 연속 접근 35회 → ON_ROUTE/후보 → 명시적 정차 확인 → 출발지 복귀 완료·안내 종료·포커스 해제. |
| MVP 연결 | DATA LAB 실제 추천·왕복 계산 → private handoff → 활성 안내/TTS → 도착 공개 → 평가 저장 → 새 복귀 준비 버튼 → 실제 복귀 준비·시작·완료 확인. |
| 안정성 | **632.6초(10분 32초), 공식 위치 입력 500회**, 종료 코드 0, 같은 PID. 마지막 ACTIVE/ON_ROUTE와 포커스 소유 확인. |
| 재시작 | 안내 종료 후 PoC 강제 종료·재실행. 자동 포커스 획득 없음. private cache의 LIVE 경로·복귀 leg·원출발지·출발 시각 보존. 활성 안내 중 강제 종료와 완료 UI 전체 복원은 미검증/미완료. |

개발용 backend.active_replay는 opt-in 공개 세션 또는 명시적인 prepared-public-catalog 옵션으로 기존 서울 공개 후보만 수락한다. 원출발지·정확한 공개 후보·LIVE 출처·TTL·범위를 검증한다. 사용자 위치를 받아 재현하지 않는다. Fake 포트 테스트, 공식 에뮬레이터 위치 콜백, 실제 도로 주행을 구분한다.

실제 host onStopNavigation·강제 오디오 포커스 상실·GPS 서비스 단절은 이번 실험에서 유도하지 않았다. 공식 TestNavigationManager/Fake 포트 단위 테스트와 실제 실행 결과를 구분한다. 사용자 취소·앱 배경 전환·정상 종료와 서버 중단은 실제 확인했다.

## 5. 지도 및 OSM 정책

기존 RoadMapRenderer / FollowCamera / TileStore 유지. 공급원은 https://tile.openstreetmap.org/{z}/{x}/{y}.png 공용 raster tile이며 새 지도 SDK는 없다.

현재 viewport만 요청하며 대량 다운로드·사전 로딩은 없다. User-Agent는 SURPRIDE/0.1 (+https://github.com/jllgame00/surpride). HTTP cache headers·ETag·Last-Modified를 처리하고 헤더 해석 불가 시 최소 7일 캐시한다. 기본 no-cache나 앱 위장은 없다. 실제 MAP_TILES loaded=20/requested=20/failed=0/stale=0 표시를 확인했다.

© OpenStreetMap contributors와 copyright/ODbL 출처를 계속 표시한다. UI/ETA footer는 host stable Surface 안에 그려 하단 잘림을 피한다. 호스트 자체 카드를 변조·덮지 않는다. raster 라벨이 카메라와 함께 회전하는 가독성 한계, resize·야간 전수 검증은 남아 있다.

공용 tile은 SLA·운영 용량을 보장하지 않는다. 상용화 전 지도 제공업체·요청량·사용 조건을 확정해야 한다. Kakao 경로 이용권과 OSM 배경지도 이용권은 별도다. API 응답·개인 위치·토큰은 Git에 포함하지 않았다. 테스트 좌표는 기존 공개 서울 시범 장소·개발 출발지 또는 합성 단위 테스트 데이터다.

## 6. 목적지 비공개

| 채널 | 판정 | 범위 |
|---|---|---|
| SURPRIDE Template | PASS | 공개 전 이름·주소 없음. 회전·거리·도로 안내 유지. |
| 지도 Surface | PASS | 앱의 목적지 핀·명시적 종점·overview 없음. 일반 OSM 라벨·현재 위치로 추측할 가능성은 남음. |
| AndroidX Trip | PASS(전송 객체) | 비공개 Destination 0·최종 절대 좌표 없음. Loading Trip도 Destination 0. 공개 요청 후만 실제 이름 사용. 복귀 원출발지는 Destination으로 보내지 않음. |
| Pleos 홈 | UNVERIFIED | 전체 노출 감사 미완료. |
| 시스템 알림 | UNVERIFIED | 표시·기록 전수 검증 미완료. |
| 계기판/HUD | UNVERIFIED | 지원·표시 경로 미검증. |
| TTS | UNVERIFIED(음성 채널) | 발화 입력 계약은 PASS. 목적지/주소 없는 유한 어휘와 SDK 시작·완료 확인. 청취·녹음 기반 채널 감사 미완료. |
| 최근 목적지·이력 | UNVERIFIED | OEM 내부 파일에 접근하지 않음. 모든 기록 채널 검증 아님. |

updateTrip 정상 반환은 모든 호스트 채널의 표시·기록 수용을 보장하지 않는다. **DESTINATION_PRIVACY = PARTIAL** 유지. 공모전 허용 개발 가정은 Pleos 앱마켓 승인과 다르다. Phone Projection 충돌·실차 오디오·운행 안전성은 미검증이다.

## 7. 테스트 및 빌드

**Android 98 + PoC 166 + FastAPI 42 = 306개 PASS. 실패·오류·skip 0.**
기존 223개 테스트를 삭제·무력화하지 않았다. 두 표준 Gradle assembleDebug/testDebugUnitTest 및 FastAPI pytest 종료 코드 0, git diff --check 통과.

신규 테스트: 재탐색 한도·중복·취소·지연 콜백·오류·LIVE 계약, Loading Trip, 음성 포커스·중복·어휘·거리·fallback·watchdog·오류 후 OFF, 귀가 시간·원출발지·private metadata, 공개 개발 replay. Mock transport/SDK 테스트를 실제 API 성공으로 세지 않았다.

표준 명령:

~~~powershell
.\scripts\build-codex.ps1 -Test
.\scripts\build-nav-host-poc.ps1 -Test
.\backend\.venv\Scripts\python.exe -m pytest backend/tests -q -p no:cacheprovider
git diff --check
~~~

LIVE 서버는 python -m backend.dev serve --mode live. 독립 공개 경로는 python -m backend.nav_poc --request-public-route --adb <SDK의 adb>. 메인 연계는 기존 private URI 준비 흐름을 사용한다. loopback 바인딩·만료 토큰·요청 제한을 유지했다. 공개 무인증 배포는 없다. .env·proxy session·SDK 검사 산출물의 Git ignore 및 변경 텍스트 내 실제 provider key 0건 확인.

FastAPI/Starlette testclient deprecation, 기존 AndroidX ListTemplate deprecation 및 JVM native-access 경고가 남는다. 테스트 실패로 숨기지 않았으며 버전 업그레이드는 이번에 하지 않았다.

## 8. 변경 파일

- 메인: realdata/PreparedRouteContract.kt, RealRouteActivity.kt, LiveNavigationResultActivity.kt 및 계약 테스트. private trip/proxy snapshot과 결과 화면의 복귀 준비 진입 버튼.
- PoC: ActiveMysteryScreen.kt, ActiveRoutePacket.kt, ownership/NavigationOwnershipAdapter.kt, routing/*, voice/*, returntrip/* 및 관련 테스트/LoadingTripTest/RoutePacketReplacementTest.
- 설정: PoC Gradle TTS 의존성, Manifest 일반 TTS 권한/Android query, debug 전용 loopback network security XML, 공식 Maven security group, 음성·복귀 icon.
- Backend: nav_poc.py, active_replay.py, 공개 replay 테스트. 기존 dev.py 서버·인증 구성을 보존.
- 문서: 본 보고서, REROUTE_VOICE_RETURN_PLAN.md, evidence/reroute-voice-return/의 공개 개발 화면·민감정보 없는 metadata.
- 전체 경로 목록: [changed-files.txt](evidence/reroute-voice-return/changed-files.txt).

## 9. 증거와 한계

[실제 재탐색](evidence/reroute-voice-return/02-reroute-real-kakao.png) /
[서버 오류 Loading](evidence/reroute-voice-return/03-server-unavailable-loading.png) /
[실패 중단](evidence/reroute-voice-return/04-server-failure-stopped.png) /
[도착 공개](evidence/reroute-voice-return/05-arrived-revealed.png) /
[최종 비공개 준비](evidence/reroute-voice-return/13-mvp-prepared-final-apk.png) /
[최종 활성·TTS](evidence/reroute-voice-return/14-final-apk-active-voice.png) /
[도착 결과](evidence/reroute-voice-return/15-final-mvp-arrival-result.png) /
[평가 저장](evidence/reroute-voice-return/16-final-mvp-record-saved.png) /
[복귀 준비](evidence/reroute-voice-return/17-final-mvp-return-ready.png) /
[복귀 활성](evidence/reroute-voice-return/18-final-return-active.png) /
[복귀 완료](evidence/reroute-voice-return/19-final-return-completed.png).

08-return-end-replay-unconfirmed.png는 실패/미확정 증거이며 완료 증거가 아니다. 원본 경로 응답·좌표 로그·인증정보·개인 방문 기록을 내보내지 않았다. 평가 저장 화면은 이번 공개 개발 장소 테스트의 확인 메시지다.

10분 soak는 기능 통합 APK(ff7885fd…)에서 수행했다. 이후 음성 idle 표시·오류 후 OFF 게이트·구형 packet 원출발지를 회귀 수정한 최종 APK를 재설치해 메인 연계·TTS·도착·복귀 완료를 다시 확인했다. 최종 수정 APK 전체 soak 재수행은 아니다. 해시: apk-revisions.json, 최종 설치/빌드 일치 확인.

초반 PSS 79,463KiB → 후반 86,153KiB의 두 샘플만 확보했다. 메모리 누수 없음으로 단정하지 않는다. 가용 events buffer에는 이전 다른 PID의 PoC 크래시 2건이 있었으나 이번 검증 PID의 새 Crash/ANR은 0건이었다. 과거 버퍼를 삭제하지 않았다.

AOSP는 abandon 뒤 owned={}인 client 기록을 보존할 수 있다. 이런 행만으로 활성 포커스/리스너 누수를 주장하지 않는다. 현재 lifecycle마다 포트를 재생성하고 한 활성 세션에 한 lease를 사용한다. 같은 포트의 무제한 재사용·다회 시작/종료의 heap/리스너 감시는 Phase 4 과제다.

검증 종료 시 안내 포커스 없음, PoC 종료, 메인 앱 복귀, 로컬 서버 종료와 일회성 재시작용 토큰 파일 제거를 확인했다. 사용자 .env와 기존 캐시는 보존했다.

## 10. 공식 근거 및 Phase 4

- [Android navigation 계약](https://developer.android.com/training/cars/apps/navigation): Loading Trip·중지 계약.
- [Audio Focus](https://developer.android.com/media/optimize/audio-focus), [AAOS Audio Focus](https://source.android.com/docs/automotive/audio/audio-focus).
- [Pleos TTS 개요](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro), [의존성](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/importing-sdk), [권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions).
- [Pleos pre-roll](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide): 150~200ms, scheduler와 오디오 패딩 구분.
- [Kakao Directions 코드](https://developers.kakaomobility.com/guide/navi-api/reference.html).
- [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/), [저작권](https://www.openstreetmap.org/copyright).
- [AOSP focus manager](https://android.googlesource.com/platform/packages/services/Car/+/refs/heads/main/car-lib/src/android/car/CarAppFocusManager.java), [focus service](https://android.googlesource.com/platform/packages/services/Car/+/refs/heads/main/service/src/com/android/car/AppFocusService.java).

Phase 4는 **정차 개발 기능 범위에서 조건부 진행 가능**하다. 우선순위는 다음과 같다.

1. 여러 반복/교차 종점·GPS 단절/복구·포커스 상실·host stop 실기기/호스트 시험.
2. TTS 청취·음악 ducking·오디오 수준 pre-roll과 오류 복구.
3. 수 시간 부하·반복 시작/종료·메모리, resize·야간·회전 raster 라벨 가독성.
4. 완료 화면 복원과 위치 신뢰도 재확인 UX, 시스템 채널 목적지 노출 감사.
5. 운영 지도 공급자·서버 인증·Pleos 심사 승인, 장소별 주차·차량 진입점 확인.

시간 내 귀가, 주차/진입 안전성, 실차 운행 적합성은 보장하지 않는다.

