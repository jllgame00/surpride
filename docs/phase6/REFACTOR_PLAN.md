# SURPRIDE Phase 6B — 최소·완전 개선 계획

> 후속 실행 기록(2026-11-10, 6B-3): [B3 결과](B3_HOST_LIFECYCLE_VOICE.md)에 A04 앱 갱신/Surface 정리, A05 Screen 경합 회귀, A13 발화 watchdog을 기록한다. 실제 Host 공식 종료 도달성과 ANR 원인/플랫폼 계측은 남는다. B4의 독립 코드 작업은 가능하지만 ACTIVE 시연 게이트는 자동으로 열리지 않는다.

> 후속 실행 기록(2026-11-10, 6B-2): B2의 A06/A03 및 실제 Screen stack 회귀 결과를 [별도 문서](B2_PERSISTENCE_RESTORATION.md)에 기록했다. 저장/복원 계약 검증 뒤 B3에 진입 가능하나 Host 활성 전체 흐름은 미완료다. B3에서 A04 Surface/조작·결과 Activity FocusEvent timeout과 남은 A05/A13을 우선한다. 아래는 6A 당시 계획이다.

> 후속 구현 기록(2026-11-10): 아래는 6A 당시 계획이다. B1(A01/A02/관련 A05)의 실행 근거는 [Phase 6B-1 결과](B1_GUIDANCE_ACCURACY.md)에 있다. 거리/무결성 계약 회귀는 완료했고 실제 Host 컨트롤 제한은 남는다. 다음 B2는 A06→A03 및 관련 복원 회귀로 진행 가능하다. B3/나머지 과제를 완료로 간주하지 않는다.

2026-11-10 · Phase 6A 읽기 전용 제안 · **이번에 구현하지 않음**

기준은 `main / 43c659442ea7894f2ff3835eb4c1f3843d5b204f`와 기존 Phase 5C 미커밋 변경이다. 이 문서의 A01–A16은 [감사 보고서](CODEBASE_AUDIT.md)의 동일 ID다. P0 0 / P1 5 / P2 9 / P3 2이며 확인된 코드 동작과 미발생 추정 위험을 구분한다.

반복 요청 재확인 기준은 `main / c0d81ee08d0c49f6bc3bfc583b918362bc3790d6`이다. 최초 미커밋 변경과 감사 문서는 현재 커밋에 포함되어 있고, 기존 667개 파일의 바이트는 동일하다. 재확인 시작 시 미커밋 변경은 없었다. 아래 계획의 구현 범위·우선순위를 유지하며 이번에도 제품 소스·기존 테스트를 수정하지 않는다.

새 아키텍처·의존성·프레임워크를 도입하지 않는다. 잘못된 결과를 재현하는 작은 회귀부터 추가하고 원인을 가진 기존 함수에서 고친다. 플랫폼·하드웨어 확인이 필요한 항목을 코드 간소화로 대신하지 않는다. 테스트 삭제·skip·안전 임계값 완화로 통과 수를 맞추지 않는다.

## 1. 실행 순서와 통과 조건

| 묶음 | 우선 작업 | 통과 조건 |
|---|---|---|
| B1 입력·안내 정확성 | A01 → A02 → 관련 A05 | 구간별 거리·회전 앵커와 단절 geometry 회귀. 불신 위치에서 정상 TBT/회전 발화 없음 |
| B2 복원·내구성 | A06 → A03 → 관련 A05 | packet 쓰기 실패에서 이전 정상 파일 보존. 도착·복귀 완료 뒤 재생성해도 안전한 재진입. 자동 소유권 재획득 없음 |
| B3 조작·플랫폼 경계 | A04 → 나머지 A05 → A13 | 실제 Host에서 종료/조작 접근을 관측. late grant/response, loss/stop 이후 발화·Trip·리스너가 남지 않음 |
| B4 추천·기록 일관성 | A07 → A08 → A14 | 같은 형상의 더 나은 응답 보존. 양 모드의 안전 조건 일치. LIVE 이력 정책을 테스트로 고정 |
| B5 성능·보존 | A09 → A10 → A11 → A12 | 기준 측정 뒤 최소 최적화. 타일·packet 보존 및 cleanup 계약 확인 |
| B6 선택 정리 | A15, A16 | 제품·테스트 경로 유지, 개발 대상이 명시적으로 일치. 필수 수정 완료 전 착수하지 않음 |

묶음마다 앱 196/PoC 196/backend 90의 기존 검사를 보존하고 해당 회귀를 추가한다. 증분 개수는 별도로 기록한다. 계측 10개는 실제 실행한 환경에서 다시 확인하고 실제 Host 시나리오와 구분한다. 성능 최적화 전에 B1/B2가 우선이다.

## 2. 반드시 해결할 P1

### A01 — 전체 비율로 회전 위치를 계산

- **파일·함수:** `nav-host-poc/src/main/java/com/surpride/navhostpoc/tracking/RouteMatcher.kt:78`, `accept`; `guidance/GuidanceModels.kt`의 `RouteGeometry`/`Maneuver`; `guidance/GuidanceParser.kt`; `ActiveMysteryScreen.turn/publish`; `map/FollowCamera.update`. 필요한 경우 `backend/guidance.py:guidance_data` 계약도 함께 수정한다.
- **실제 문제·확인 수준:** 합성 재현. 도로별 API 거리와 geometry 길이의 비율이 다르면 전체 환산 진행거리로 다음 회전을 선택해 도로명·거리·음성이 어긋난다. `Maneuver.roadIndex`가 이 대응에 사용되지 않는다.
- **재현 조건:** geometry 구간 약 100/900m, 도로 거리 500/500m, 다음 도로 시작 회전 offset 500m. 첫 구간 끝 위치에서 다음 회전이 약 399m 남았다고 나온다. 실경로 발생 빈도 미확인이다.
- **영향 범위:** matcher 공유 GPS 화면, 활성 TBT, 전방 카메라의 회전 창, TTS 시점, 진행 기반 도착 후보. ETA는 본래 비율 추정이며 이 문제를 가리려고 ETA 표시만 바꿔서는 안 된다.
- **최소 수정:** 선분과 원래 road index의 대응을 유지하고 도로별 누적 거리·해당 구간 내 비율로 진행을 환산한다. guide 위치가 계약상 검증 가능하면 그 앵커를 보존해 회전 위치를 대조한다. 근거 없는 앵커/geometry는 정상 회전 안내 대신 오류·재준비로 처리한다. 전역 진행률 수치만 보정하는 상수는 추가하지 않는다.
- **필요 테스트:** 서로 다른 구간 비율, 도로 경계의 실제 좌/우회전 geometry, 0거리 road, 동일 offset 안내, 짧은 종점, 겹치는 왕복 구간. matcher 결과와 TTS 거리/다음 maneuver가 함께 일치해야 한다.
- **예상 회귀 위험:** 높음. 진행률·도로 선택·도착/waypoint·카메라를 함께 영향. 기존 ambiguity/jump/heading 검사와 waypoint LIVE CLOSED를 유지한다.
- **수정 우선순위:** **P1 / 1순위**. 물리 보정·실경로 관측은 회귀 후 별도 단계다.

### A02 — 입력에 없는 선분을 경로로 생성

- **파일·함수:** `nav-host-poc/src/main/java/com/surpride/navhostpoc/guidance/GuidanceParser.kt:42`, `parse`; `guidance/GuidanceModels.kt:RouteGeometry`; `backend/guidance.py:guidance_data`; `tracking/RouteMatcher.accept`.
- **실제 문제·확인 수준:** 합성 재현. 인접 road geometry의 끝·시작 연결 검사가 없고 flatMap이 빈 공간을 연결한다. 실제 API의 잘못된 응답 발생은 관측하지 않았다.
- **재현 조건:** 0–100m 및 500–600m 선분을 한 경로의 두 road로 제공하면 파서가 수용하고 300m 위치가 ON_ROUTE가 된다.
- **영향 범위:** 모든 guidance packet 수신·reload·reroute, 지도 경로선 및 잘못된 GPS 안내. HTTP 응답이 인증됐다는 사실로 geometry 무결성이 보장되지는 않는다.
- **최소 수정:** 원 구간 경계를 유지하면서 연결성·순서·전체 점 수를 검증한다. 정확히 같은 경계점은 중복 제거하되, 허용 가능한 작은 오차는 실제 계약/fixture 근거로 정한다. 검증 불가 gap을 임의 선분으로 메우지 않는다. 상류와 Android 계약 검사는 서로 역할이 달라 유지한다.
- **필요 테스트:** 동일 endpoint, 작은 정상 오차, 큰 gap, 역순 road, 구간 내 단일점/중복점, geometry 총점 상한. 거부 시 가상 경로·일반 TBT가 나오지 않아야 한다.
- **예상 회귀 위험:** 중간~높음. 정상 API 경계 오차를 과도하게 차단할 수 있어 공개 개발 fixture로 허용 범위를 먼저 확인해야 한다.
- **수정 우선순위:** **P1 / 2순위**, A01과 하나의 거리 매핑 계약으로 조율한다.

### A03 — 도착·공개·복귀 완료 상태 복원 누락

- **파일·함수:** `nav-host-poc/src/main/java/com/surpride/navhostpoc/ActiveMysteryScreen.kt:43`의 `arrival/revealed/returnCompleted/arrivedAt`, `confirmArrival/controls/returnResult`; `ActiveRoutePacket`; `MockNavigationService.onCreateScreen/onNewIntent`; `app/src/main/java/com/surpride/app/realdata/LiveNavigationResultActivity.kt`의 복귀 버튼.
- **실제 문제·확인 수준:** 정적 확인. route/startedAt/returning은 packet에 있지만 명시적으로 확인한 terminal 상태와 공개 상태는 저장하지 않는다. 새 Screen에서는 `ArrivalGate()`와 revealed=false로 시작해 복귀·평가 메뉴 조건이 사라진다. 실제 process-death E2E는 이번에 수행하지 않았다.
- **재현 조건:** outbound 도착 확인 또는 return 완료 → PoC 프로세스 재생성 → cached packet 재진입. 결과 화면의 복귀 버튼도 data 없는 Activity launch여서 새 세션에서는 기본 정적 화면으로 들어갈 수 있다.
- **영향 범위:** 도착 후 평가 재진입, 평가 이후 출발지 복귀, 복귀 완료 표시. 과거 GPS candidate나 소유권을 자동 복원해서 해결해서는 안 된다.
- **최소 수정:** private packet에 명시적 도착 확인/완료와 공개 여부의 최소 terminal metadata만 원자적으로 보존한다. nonce·만료·현재 packet과의 일치를 검증한다. data 없는 복귀 요청의 진입점은 기존 명시적 경로로 연결하되 **준비만** 수행한다. 과거 GPS 신뢰도·candidate·Focus·ACTIVE는 복원하지 않는다. 만료 시 새 준비 절차를 명확히 표시한다.
- **필요 테스트:** 도착 전/조기 공개/도착 확인/평가 저장/복귀 준비/복귀 완료 각각에서 process recreation, 만료·다른 nonce·기록 중복 차단. 어떤 복원에서도 navigationStarted가 자동 호출되지 않아야 한다.
- **예상 회귀 위험:** 높음. 공개 전 정보 노출, 오래된 도착 인정, 중복 평점 및 자동 안내 위험이 있어 제한된 metadata만 다룬다.
- **수정 우선순위:** **P1 / 3순위**, 파일 내구성 A06을 먼저 준비한다.

### A04 — Host 전환 시 컨트롤 접근 지연

- **파일·함수:** `nav-host-poc/src/main/java/com/surpride/navhostpoc/ActiveMysteryScreen.kt:379`, `onGetTemplate/refresh/closeControls`; `NavigationControlMenu`; `SurfaceFooterLayout`. 실행 근거는 `docs/phase5/UI_UX_FINAL_VALIDATION.md` §6–7/10 및 final 화면이다.
- **실제 문제·확인 수준:** 기존 실행 관측. Surface만 보이고 컨트롤이 늦게/안 나타나는 현상·잔상이 남는다. legacy native ETA 잘림도 관측됐지만 활성 footer와 별개다. Host 내부 원인은 미확정이다.
- **재현 조건:** API4 Host의 활성 지도↔공식 메뉴 및 앱 전환. 기존 재현 자료가 있으며 이번 감사에서 화면 재실행하지 않았다. 앱에서는 이미 예약된 1초 갱신을 immediate가 앞당기지 못한다.
- **영향 범위:** 안내 종료·공개·음성 OFF·도착 확인의 접근성, 시연 안정성. 빠른 조작이 필요할 때 영향이 크다.
- **최소 수정:** 입력→invalidate→Host template 수신→실제 컨트롤 표시 시점을 동일 기준으로 측정한다. 앱 측 예약 지연이 확인되면 기존 Runnable의 immediate 재예약만 고친다. Host 문제는 공식 지원 환경/호환성 결과로 관리하고 우회 overlay나 Surface 가짜 버튼을 만들지 않는다. native ETA 카드를 재도입해 해결했다고 하지 않는다.
- **필요 테스트:** 예약 refresh 중 즉시 메뉴 전환, Back/종료 연속 입력, font 1/1.3/2, supported 창 크기, Host 재연결, 버튼 표시 전 stop callback. 실화면 종료 도달 시간과 실패율을 기록한다.
- **예상 회귀 위험:** 중간. invalidate 과다 호출·화면 순서·메뉴 lifecycle을 바꿀 수 있다. 목표 응답 시간은 측정·지원 환경 확정 후 명시한다.
- **수정 우선순위:** **P1 / 4순위**. Host 문제를 원인 미확정인 채 대규모 앱 재작성으로 처리하지 않는다.

### A05 — 안전 상태 전이의 Screen 통합 테스트 부재

- **파일·함수:** `nav-host-poc/src/main/java/com/surpride/navhostpoc/ActiveMysteryScreen.kt` 전체 orchestration; `ownership/NavigationOwnershipAdapter.kt`, `routing/RerouteCoordinator.kt`, `tracking/PositionProvider.kt`, `voice/NavigationVoiceController.kt`. 기존 검사는 `src/test`의 각 controller/adapter suite다.
- **실제 문제·확인 수준:** 전체 테스트 참조 검색으로 확인. 실제 Active Screen을 생성하고 lifecycle·GPS·Focus·HTTP·voice를 한 흐름으로 검증하는 자동 테스트가 없다. 개별 adapter/controller 테스트를 실제 Screen 통합 성공으로 간주할 수 없다.
- **재현 조건:** onStop와 grant/HTTP 성공이 근접, loss 직후 GPS 도착, 메뉴 전환 중 UX UNKNOWN, 재진입 때 새 adapter와 기존 matcher가 조합되는 상황. 이 조합의 실제 결함은 아직 미확인이다.
- **영향 범위:** 소유권 이중 획득·상실 후 안내·늦은 응답·중복 GPS·자원 정리·복원 등 안전 핵심 연결부.
- **최소 수정:** 기존 port와 공식 TestCarContext를 재사용해 재현 가능한 최소 주입 지점만 연다. 화면 전체를 새 controller 프레임워크로 옮기지 않는다. 실제 Car/Binder 검증은 별도 정차 계측으로 남긴다.
- **필요 테스트:** pending grant→stop→late grant, ACTIVE→loss→queued GPS, reroute→onStop→late success, UX 변경→TTS 취소, process recreation→자동 시작 없음, 메뉴 왕복 구독 1개, 도착 후 복귀 실패·재시도. Trip·speak·subscription의 잔여 횟수를 단언한다.
- **예상 회귀 위험:** 중간. 테스트를 위한 API 확대·production 분기 남발을 피하고 기존 generation/cleanup debt를 보존한다.
- **수정 우선순위:** **P1 / B1–B3 각 수정에 동반**. 단순 테스트 개수 증가가 목표가 아니다.

## 3. 개선할 P2

### A06 — private packet 비원자적 덮어쓰기

- **파일·함수:** `nav-host-poc/src/main/java/com/surpride/navhostpoc/ActiveRoutePacket.kt:39`, `rememberStart`, `replace:57`, `load:80`.
- **실제 문제·확인 수준:** `writeText` 직접 덮어쓰기는 정적 확인. process death·디스크 실패에서 잘린 JSON을 남길 위험은 추정이며 실제 손상은 관측하지 않았다. `rememberStart`는 쓰기 전 envelope도 바꾼다.
- **재현 조건:** 기존 정상 packet이 있을 때 새 metadata/경로 기록 도중 쓰기 실패 또는 프로세스 종료.
- **영향 범위:** 캐시 재진입·원출발지·복귀 metadata. 사용자 평점 DB의 손상이라고 확대하지 않는다.
- **최소 수정:** 메인 `DemoStateStore`가 이미 사용하는 Android `AtomicFile` 패턴을 재사용한다. 저장 성공 후 in-memory packet을 교체하고 실패 시 이전 파일·상태를 보존한다. 민감 body/예외 원문은 로그에 넣지 않는다.
- **필요 테스트:** 쓰기 시작/일부 쓰기/finish 단계 실패, 이전 정상 bytes 복원, 새 packet parse 실패, 재탐색 callback 중단. 직접 `src` 파일을 바꾸지 않고 injectable file boundary로 검증한다.
- **예상 회귀 위험:** 중간. 파일 보조본/만료 처리·route 교체 순서에 영향.
- **수정 우선순위:** **P2 / B2에서 A03보다 먼저**.

### A07 — 더 빠른 동일 경로를 중복으로 버림

- **파일·함수:** `app/src/main/java/com/surpride/app/realdata/experience/DriveExperienceEngine.kt:86`, `recommend`의 `seen`, `measured`, winner 선택.
- **실제 문제·확인 수준:** 합성 재현. 입력 순서대로 geometry를 seen에 넣어 같은 길의 더 빠르거나 더 최신 응답을 평가 전에 제외한다.
- **재현 조건:** 동일 geometry, RECOMMEND 1,200초/TIME 600초/DISTANCE 900초, 모두 신선·유효, 편도 60분 예산. 현재 1,200초가 선택된다.
- **영향 범위:** 통합 추천의 시간/경로 점수, 선택 priority·이유·handoff packet. 실제 호출에서 시간 차이 빈도는 미측정이다.
- **최소 수정:** 유효·신선·시간 조건을 통과한 동일 geometry 응답끼리 결과 기준으로 대표를 고른다. 기존 점수 정책에 따라 더 나은 응답을 남기고 동점만 deterministic tie-break한다. 선택 payload와 options는 같은 응답에서 유지한다. 요청 전 geometry가 같다고 추정해 3 priority 조회를 없애지 않는다.
- **필요 테스트:** 동일 geometry·시간 차이, freshness 차이, 옵션 순서 반전, 예산 경계, 선정 응답의 raw guidance/options 보존. 완전 동일 응답은 DUPLICATE로 남긴다.
- **예상 회귀 위험:** 중간. 기존 winner와 추천 설명이 바뀔 수 있으나 변경 이유를 fixture로 명확히 한다.
- **수정 우선순위:** **P2 / B4 첫 작업**.

### A08 — 기존·통합 추천 모드의 정책 중복과 실패 처리 차이

- **파일·함수:** `app/src/main/java/com/surpride/app/realdata/RouteRecommendationEngine.kt:recommend`, `realdata/experience/DriveExperienceEngine.kt:recommend/destinationScore/budgetSeconds`, `RealRouteActivity.calculate/renderDiagnostics`.
- **실제 문제·확인 수준:** 정적 확인. 후보 필터·시간/점수 계산이 양쪽에 있고 기존 모드는 AUTH/RATE_LIMIT/NOT_CONFIGURED에서 다음 후보로 계속한다. 기존 모드는 공개 후보 5개까지, 통합은 2개로 제한되어 결과도 다를 수 있다.
- **재현 조건:** 개발 진단에서 기존 평가 선택 → 모든 요청이 AUTH 실패. 기존은 후보마다 요청하고 통합은 첫 치명 오류에 중단한다. 이미 실패한 왕복을 합성 성공으로 바꾸지는 않는다.
- **영향 범위:** 개발 quota·실패 대기·추천 정책 유지보수. 기존 모드는 호출부·테스트가 있어 미사용 코드가 아니다.
- **최소 수정:** 우선 기존 모드에도 동일 치명 오류 중단과 취소 검사를 적용한다. 그 뒤 두 모드의 유지 목적을 결정한다. 비교 기능을 유지한다면 시간·선호의 중복된 순수 계산만 기존 위치에서 공유하고 일반 provider/strategy 계층을 추가하지 않는다. 모드 제거 시 호출 UI·fixtures·문서의 대체 경로를 함께 정리하고 테스트의 의미를 보존한다.
- **필요 테스트:** 같은 preferences에 exclusion/deadline/range/source 규칙 일치, 첫 fatal 오류 이후 호출 0, 취소 이후 추가 HTTP 0, 차이가 의도된 shortlist는 명시.
- **예상 회귀 위험:** 중간. 실측·Mock/DEMO 출처, 초/분 올림, 마지막 deadline 재검사를 잃기 쉽다.
- **수정 우선순위:** **P2 / B4 두 번째**. 엔진 전체 합치기보다 정책 차이를 먼저 고친다.

### A09 — 긴 geometry의 main-thread 반복 계산

- **파일·함수:** `nav-host-poc/src/main/java/com/surpride/navhostpoc/tracking/RouteMatcher.kt:44`, `accept`; `guidance/GuidanceModels.kt:RouteGeometry.atFraction`; `map/FollowCamera.kt:update`; `ActiveMysteryScreen` GPS callback/paint.
- **실제 문제·확인 수준:** 전체 탐색·리스트 생성·후보 정렬은 정적 확인. 프레임 정지/ANR은 추정이며 이번에 측정하지 않았다.
- **재현 조건:** 1k/10k/50k/100k 점 경로, 중첩 구간의 후보 다수, GPS 1Hz와 tile callback/Host 메뉴 조작 동시 실행.
- **영향 범위:** 회전 표시·종료 조작 반응·GC·전력. 현재 짧은 정차 시연의 성공으로 장거리 안정성을 보장할 수 없다.
- **최소 수정:** 측정 후 먼저 `atFraction`을 기존 누적 배열의 이진 탐색으로 바꾸고 고정 선분 정보의 반복 생성을 줄인다. 필요할 때만 공간/진행 범위 검색을 추가하며 재획득·모호성에 전체 fallback을 유지한다. async로 옮길 경우 route/position generation을 함께 검증한다.
- **필요 테스트·측정:** 동일 입력 전후 matching 결과 동일, 교차/역방향/재획득 회귀, 처리 시간 p50/p95/p99·최악 시간·할당·frame 지연. 사용자 위치 대신 합성·공개 fixture만 사용.
- **예상 회귀 위험:** 높음. 속도를 위해 잘못된 traversal을 고르는 변화 금지.
- **수정 우선순위:** **P2 / B5**, 정확성 수정 이후.

### A10 — 타일 freshness와 장기 cache 관리 누락

- **파일·함수:** `nav-host-poc/src/main/java/com/surpride/navhostpoc/map/RoadMapRenderer.kt:209`, `ViewportTiles.want`; `map/TileStore.kt:load/read/write/expiry`.
- **실제 문제·확인 수준:** 정적 확인. 성공한 in-memory 타일은 `!existing.failed`로 계속 재사용되어 HTTP 만료 시점이 renderer에 전달되지 않는다. 파일 cache는 삭제·상한이 없다. 공간 고갈이나 오래된 타일 화면 발생은 미측정이다.
- **재현 조건:** 같은 Screen에서 만료를 지나 계속 같은 viewport 표시, 또는 여러 지역을 반복 조회해 파일 증가.
- **영향 범위:** 지도 최신성·디스크·방문 지역 추론. 현재는 공개 개발 위치만 사용한 시연이다.
- **최소 수정:** `TileStore`의 freshness를 decode cache에서도 존중하고 만료 시 조건부 재검증한다. 서버 cache directive와 기본 보존 정책을 지키며 만료 파일 위주 정리를 설계한다. OSM 정책을 확인하지 않은 채 신선한 타일을 공격적으로 지워 재요청량을 늘리지 않는다. 저장/로딩 실패 시 기존 stale 표시를 유지한다.
- **필요 테스트:** renderer 수준 만료/304/오류/stale 복귀, viewport 이탈 중 queued 요청 취소, decode 실패, 저장 공간 실패, cache 정리와 요청량 회귀. cache 시간은 주입해 기다리지 않고 검사한다.
- **예상 회귀 위험:** 중간. 재검증 폭증·지도 공백·출처 표시 누락 가능.
- **수정 우선순위:** **P2 / B5**.

### A11 — 만료·종료 후 private packet 바이트 잔존

- **파일·함수:** `nav-host-poc/src/main/java/com/surpride/navhostpoc/ActiveRoutePacket.kt:load/isFresh`, `ActiveMysteryScreen` terminal/stop 처리; `app/src/main/java/com/surpride/app/realdata/PreparedNavigationHandoff.kt:launch`, `NavigationFileProvider.openFile`; debug `ProxyConfiguration.load`; `backend/dev.py:serve`.
- **실제 문제·확인 수준:** 정적 확인. TTL은 사용/인증을 제한할 뿐 파일 삭제를 보장하지 않는다. prepared 파일은 다음 launch 또는 expired open에서 정리하고 PoC route 파일은 종료 시 남는다. 위치정보 유출·토큰 악용은 관측하지 않았다.
- **재현 조건:** packet 준비/사용 → 종료·만료 → 앱 재진입 없이 방치. private cache에 목적지·geometry·원출발지·이미 만료된 단기 토큰이 남을 수 있다.
- **영향 범위:** 저장소 접근 권한이 있는 진단·백업 외부 추출·공유 과정의 개인정보 잔존. 일반 앱이 sandbox를 통과해 읽을 수 있다고 주장하지 않는다.
- **최소 수정:** 명시적 완료/취소·만료에서 필요 없는 credential과 packet을 정리한다. 복귀 준비/기록에 필요한 최소 metadata의 보존 기간은 A03과 함께 정한다. 단순 onStop 삭제는 복귀를 깨므로 금지한다. URI grant도 세션 종료 조건에 맞게 해제한다. 상태 로그는 원문을 추가하지 않고 debug 빈도·보존을 제한한다.
- **필요 테스트:** 만료 load 후 파일/credential 정리, 완료 후 복귀 metadata 유지 범위, 실패 재시도, URI 재열기 거부, 정상 기록 보존, 개인정보가 오류/로그에 나오지 않음.
- **예상 회귀 위험:** 높음. 과도한 cleanup은 복귀·평가 복구를 파괴한다.
- **수정 우선순위:** **P2 / B2 계약 결정 후 B5 구현**, 공개 자료 제출 전 최소 보존 설명 확정.

### A12 — 서버 upstream 연결을 요청마다 폐기

- **파일·함수:** `backend/app.py:149`, `fetch_route`; `create_app` lifecycle; `backend/tests/test_proxy.py`/`test_route_options.py` transport fixture.
- **실제 문제·확인 수준:** per-request `AsyncClient` 생성·close는 정적 확인. latency·TLS CPU·동시성 한계는 미측정이다.
- **재현 조건:** 현재 추천 1회에 왕복 최대 8회 직렬 upstream 요청. client 수명 밖으로 연결이 이어지지 않는다.
- **영향 범위:** 경로 준비 지연·연결 비용. 현재 loopback 단일 worker 개발 부하에서 서비스 장애라고 단정하지 않는다.
- **최소 수정:** 측정상 효과가 있으면 FastAPI lifespan 내 client 1개를 만들고 종료 시 close한다. trust_env/redirect/timeout/응답 상한·mock transport injection은 그대로 유지한다. 분산 cache·queue·Redis는 추가하지 않는다.
- **필요 테스트:** client 재사용/종료 1회, cancellation·timeout 뒤 회수, 연결 오류 분류, 기존 인증·budget·no-store·source·입력 회귀. synthetic transport 부하와 실제 네트워크 측정을 구분한다.
- **예상 회귀 위험:** 중간. app 생성/종료·테스트 loop에 연결이 잘못 묶일 수 있다.
- **수정 우선순위:** **P2 / B5 후반**, 정확성·보존보다 뒤.

### A13 — Android TTS 완료 유실 시 Focus 종료 기한 없음

- **파일·함수:** `nav-host-poc/src/main/java/com/surpride/navhostpoc/voice/NavigationVoiceController.kt:emit/releaseOutput/cancelOutput`, `AndroidNavigationVoice.kt:AndroidVoiceSpeech`, 비교 대상 `PleosOnDeviceVoiceSpeech.kt:armDeadline`.
- **실제 문제·확인 수준:** 정적 확인. Pleos backend에는 15초 callback deadline이 있으나 Android speech 경로에는 없다. 실제 무콜백·무한 duck은 관측하지 않았다.
- **재현 조건:** Android TTS가 queue SUCCESS만 반환하고 start/done/error/stop 콜백을 보내지 않음. 같은 maneuver stage가 이어져 다음 발화도 없을 때 Focus가 유지될 수 있다.
- **영향 범위:** 미완료 음성 상태·다른 오디오 duck·엔진 자원. ownership loss와 lifecycle stop은 별도 cancel로 정리된다.
- **최소 수정:** 기존 utterance id/session에 묶인 최대 대기 종료를 추가한다. timeout 시 발화 취소·Focus 해제·명시적 오류/OFF, 늦은 콜백 무시. 무조건 재발화나 자동 fallback은 하지 않는다.
- **필요 테스트:** queue 성공 후 모든 콜백 없음, timeout 직전 done, timeout 후 late done, 다음 발화 시작 후 이전 timeout, ownership/audio focus loss와 경합.
- **예상 회귀 위험:** 중간. 정상 긴 발화 중단·새 발화 Focus 해제 위험. 제한은 짧은 유한 안내 문구와 실제 backend 관측으로 정한다.
- **수정 우선순위:** **P2 / B3**, 실음향 관측은 별도 게이트.

### A14 — LIVE 방문 이력이 추천 제외에 연결되지 않음

- **파일·함수:** `app/src/main/java/com/surpride/app/experience/ExperienceActivity.kt:196`의 실제 경로 진입; `realdata/RealRouteActivity.kt:71/calculate`; `LiveNavigationStore.read`; `PreparedRouteContract.kt:LiveNavigationVisit`.
- **실제 문제·확인 수준:** 정적 확인. `recentIds`는 DEMO history를 Intent로 받지만 LIVE journal은 화면 표시에서만 읽는다. LIVE 평가를 남긴 후보도 다음 실제 추천에서 제외되지 않는다. 이 기록은 에뮬레이터 평가이며 실제 방문 인증은 아니다. 평점 학습 모델은 원래 구현되지 않았다.
- **재현 조건:** LIVE 공개·평가 저장 후 같은 조건으로 실제 추천. LIVE candidate ID가 context.recentIds에 들어가지 않는다. DEMO ID는 별도 카탈로그이므로 같은 실제 후보를 막지 못한다.
- **영향 범위:** 반복 목적지·개인화 기대 불일치. 안전 경로 계산 자체에는 영향 없음.
- **최소 수정:** 최근 LIVE 방문 제외가 제품 의도인지 고정하고, 그 정책이라면 기존 journal의 최근 N개 ID를 실제 추천 context에 전달한다. DEMO/LIVE 데이터의 의미와 보관 분리는 유지한다. 새 추천 학습 모델·기분 필드 수집을 추가하지 않는다.
- **필요 테스트:** LIVE 완료 후 같은 후보 제외, 오래된 항목 정책, 빈 journal, DEMO와 ID 충돌, 후보 소진 시 정직한 NO_CANDIDATE. 선호/시간 조건을 임의 완화하지 않는다.
- **예상 회귀 위험:** 낮음~중간. 실제 후보가 5곳뿐이므로 빠르게 후보가 없어질 수 있다. 최근 개수·재방문 정책을 명시해야 한다.
- **수정 우선순위:** **P2 / B4 마지막**, 기록 의미 확정 후.

## 4. 선택적 P3

### A15 — 제품 호출 없는 테스트 대역의 main 배치

- **파일·함수:** `nav-host-poc/src/main/java/com/surpride/navhostpoc/tracking/PositionProvider.kt:FakeLocationProvider`, `app/src/main/java/com/surpride/app/realdata/RouteModels.kt:UnconfiguredRouteProvider`.
- **실제 문제·확인 수준:** 전체 코드·테스트·설정·문서 참조 검색상 제품 호출 없음, 테스트 사용은 있음. 런타임 결함이 아니라 코드 소유 위치의 선택적 정리다.
- **재현 조건:** main artifact에 테스트 대역이 포함되는 현재 구성. Fake 자동 fallback은 발견하지 않았다.
- **영향 범위:** 패키지 표면·개발 가독성. 줄 수 감소나 APK 크기 개선은 미측정이다.
- **최소 수정:** 같은 package의 test source로 이동할 수 있으면 이동한다. provider 인터페이스와 실제 어댑터는 유지한다. `PreferencePatchJson` 같은 검증 경계까지 함께 삭제하지 않는다.
- **필요 테스트:** 기존 LocationSession/RealRoute tests 그대로 실행, main source에서 참조 없음과 assemble 확인. 새로운 대체 동작 테스트는 불필요하다.
- **예상 회귀 위험:** 낮음. test visibility·다른 variant의 참조 누락만 확인한다.
- **수정 우선순위:** **P3 / B6**, 안전 수정 완료 뒤 선택.

### A16 — 표준 provision 명령이 기존 serial에 고정

- **파일·함수:** `backend/dev.py:71/76`, `provision/main`; 같은 serial을 쓰는 `backend/nav_poc.py`, `backend/gps_replay.py`, `backend/active_replay.py`; `backend/tests/test_dev.py`와 replay 도구 테스트; `backend/README.md`; 5C 실행 환경 문서.
- **실제 문제·확인 수준:** 정적 확인. helper는 `emulator-5554`, user 10 고정이고 최신 검증은 5556이었다. 기존 README의 명시적 고정 대상 정책 자체는 의도된 안전 제한이다.
- **재현 조건:** 5556만 실행한 환경에서 표준 provision 명령 사용 → 대상 없음. 두 AVD가 있으면 의도와 다른 5554에 설정 전달 가능성은 있으나 이번에 실행·관측하지 않았다.
- **영향 범위:** 개발 재현성·credential 전달 대상. 임의 탐색 후 첫 기기에 자동 설치/전달하면 안 된다.
- **최소 수정:** 명시적 serial 옵션을 추가하거나 승인된 단일 시험 대상을 문서와 코드에 일치시킨다. 입력은 인자 배열로 전달하고 user/package 고정 및 run-as stdin·출력 억제를 유지한다. 자동 device discovery/선택은 추가하지 않는다.
- **필요 테스트:** 명시 대상 인자가 두 adb 호출에 동일 전달, 대상 없을 때 실패, 임의 fallback 없음, 토큰 argv/stdout/stderr 미출력.
- **예상 회귀 위험:** 낮음. 기본값 변경이 기존 5554 개발 흐름을 깨지 않도록 명시한다.
- **수정 우선순위:** **P3 / B6**, 다음 정차 AVD 검증 전 재현 절차 확정.

## 5. 범위에서 제외할 작업

- 단순 LOC 감축, 모든 상태기계 통합, 기존 native UI의 Compose/웹 재작성, 새 지도 SDK/LLM/경관 데이터 생성, DI 프레임워크, 분산 프록시 운영 체계.
- 인터페이스·세대 token·stale lease·cleanup debt·strict parser·출처 분리·오류 후 OFF·접근성·정차 UX를 줄이는 작업.
- 사용 중인 legacy 실험 전체 삭제, 테스트의 중복 추정만으로 삭제/skip, 실차 ambiguity 기준 완화.
- requirements의 freeze 형태만 보고 transitive library를 삭제하거나 최신 버전으로 일괄 업그레이드. 예제 테스트 삭제도 우선 작업이 아니다.

필요한 검증 코드가 늘어 순 LOC가 증가해도 안전성과 정확성이 개선되면 허용한다. 현재 확정된 불필요 라이브러리 제거 수는 **0**이다. 예상 LOC 순감소는 산정하지 않는다.

## 6. Phase 6B 진입·완료 조건

**진입:** 기존 667개 파일의 해시·현재 Git 상태를 다시 비교한다. 최초 Phase 5C 변경은 현재 `c0d81ee`에 포함되어 있으므로 그 내용을 보존하고, 이후 생긴 미커밋 변경도 보존한다. 이 ID별 범위와 재현 fixture를 먼저 고정한다. 소스 수정은 별도 Phase 6B 작업 요청에서 시작한다. 첫 작업은 B1이며 실차·실제 경로 자동 시작·서버 배포·커밋/Push는 이 계획의 일부가 아니다.

**통제된 정차 데모 제출 전:** P1 5건의 수정/검증 또는 플랫폼 원인의 명시된 차단·지원 범위 결정을 완료한다. A06과 A07은 함께 해결하고, A11 보존 정책을 명시한다. 482개 기존 자동 검사·새 회귀와 계측 10개를 해당 환경에서 확인한다. Host 종료/도착/복귀/중단 콜백·네트워크 단절을 실제 정차 시험에서 추가 확인하고 결과의 한계를 남긴다.

**실차·상용 배포 전:** [위험 등록부](RELEASE_RISK_REGISTER.md)의 별도 안전·비공개·인증·운영·지도/경로 승인·장시간 시험 게이트를 통과해야 한다. 앱의 emulator/parked 제한을 단순 제거하는 것은 완료 조건이 아니다.

미실행: 이 문서의 수정·신규 회귀·실기 검증 전부. 남은 위험: 제안한 최소 수정도 거리 매핑·복원·소유권 경계에 회귀를 만들 수 있어 묶음별 증거가 필요하다.
