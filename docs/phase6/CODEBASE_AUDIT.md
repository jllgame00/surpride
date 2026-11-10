# SURPRIDE Phase 6A — 전체 코드베이스 품질·안전 감사

2026-11-10 · **AUDIT_WITH_LIMITATIONS** · 제품 소스·기존 테스트 변경 없음

이 저장소는 기분·시간·취향에 맞는 목적지를 준비하는 Android 앱, 별도 Navigation Host PoC, 카카오 경로 인증 프록시로 구성된다. 현재 실행 범위는 **개발자 1명, 정차 Pleos AVD 1대, loopback 프록시 1개 프로세스**다. 공용 다중 사용자 서버나 실차 내비게이션으로 평가하지 않았다.

Ponytail의 정확성 → 안전성 → 부하 안정성 → 테스트 → 성능 → 단순화 순서를 적용했다. 안전 검증·소유권 세대 검사·권한 검사·오류 후 정리·접근성·하드웨어 보정은 축소 대상이 아니다. 코드와 테스트는 읽기 전용으로 감사했고 요청받은 문서 3개를 작성했다. 격리된 `.gradle/phase6-audit/`에 해시 기준과 합성 재현용 임시 파일만 추가했다.

## 1. 판정과 발견 수

| 등급 | 개수 | 의미 |
|---|---:|---|
| P0 | **0** | 현재 정차 개발 범위에서 P0로 분류한 결함 없음. 위험 부재·안전 인증을 의미하지 않음 |
| P1 | **5** | 안내 정확성, 복원, 기존 Host 장애, 안전 상태 전이 검증 공백 |
| P2 | **9** | 복구 내구성, 추천 오류, 성능, 개인정보 잔존, 운영·자원 위험 |
| P3 | **2** | 테스트 전용 코드 배치, 개발 도구 대상 선택 |
| 합계 | **16** | A01–A16. 같은 문제를 문서 간 중복 집계하지 않음 |

확인 수준은 **합성 재현**, **정적 확인**, **기존 실행 관측**, **추정 위험**으로 구분한다. 합성 재현은 실제 운영 입력에서의 발생률을 증명하지 않는다. P1 5건을 실제 차량 사고 5건으로 해석해서는 안 된다. 시스템 전체 비공개·STT·상용 배포 제한은 별도 [위험 등록부](RELEASE_RISK_REGISTER.md)에 기록하며 발견 수에 다시 더하지 않았다.

가장 먼저 처리할 문제는 A01 구간별 거리와 회전 위치 불일치, A02 단절된 geometry의 가상 연결, A03 도착·공개·복귀 완료 상태 소실, A04 Host 조작 지연, A05 실제 Screen 생명주기 통합 검증 부재다. 모든 항목의 재현·수정·테스트·회귀 위험은 [개선 계획](REFACTOR_PLAN.md)에 있다.

## 2. Git 기준과 보존

| 항목 | 감사 기준 |
|---|---|
| branch | `main` |
| HEAD | `43c659442ea7894f2ff3835eb4c1f3843d5b204f` |
| 기준 코드 | HEAD와 아래 기존 미커밋 변경을 합친 작업 트리 |
| 전체 참조 이력 | `git rev-list --all` 기준 21 commits |
| 기존 추적 파일 변경 | 5개, 19줄 추가 / 3줄 삭제 |
| Git 작업 | stage·commit·push·reset·clean·checkout·이력 변경 없음 |

기존 수정 파일:

- `app/src/main/java/com/surpride/app/experience/ExperienceActivity.kt`
- `app/src/main/java/com/surpride/app/moodinput/MoodInputActivity.kt`
- `app/src/main/java/com/surpride/app/ui/SurprideUi.kt`
- `nav-host-poc/src/main/java/com/surpride/navhostpoc/NavigationFooter.kt`
- `nav-host-poc/src/test/java/com/surpride/navhostpoc/NavigationFooterTest.kt`

기존 미추적 항목은 `app/src/androidTest/java/com/surpride/app/AutomotiveUiValidationTest.kt`, `docs/phase5/FINAL_VALIDATION_PLAN.md`, `docs/phase5/UI_UX_FINAL_VALIDATION.md`, `docs/phase5/evidence/final/`이다. 미추적 계측과 증거도 보존 대상에 포함했다. 감사 전 확보한 667개 기존 파일의 바이트 해시를 문서 작성 후 다시 비교했다. `.gitignore` 대상 캐시·빌드 결과·감사 임시 파일은 제품 변경으로 집계하지 않았다.

## 3. 전체 아키텍처와 데이터 흐름

| 영역 | 진입점·책임 | 입력 → 출력·저장 |
|---|---|---|
| 앱 시작·진단 | `MainActivity` | 시작 화면, debug Vehicle/NaviHelper 진단. SDK 원본 목적지 대신 유한 상태 표시 |
| DEMO | `ExperienceActivity`, `TripController`, `DestinationEngine` | `DrivePreferences` → 샘플 추천 → 명시적 모의 진행/도착/평가. 최대 30건 기록 |
| DEMO 복원 | `DemoStateStore`, `TripSnapshotCodec` | Keystore AES/GCM, `AtomicFile`, `noBackupFilesDir`, 버전·크기 제한 |
| 자연어 | `MoodInputActivity`, `RuleBasedMoodInterpreter`, `PreferencePatch` | 로컬 문장 → 유한 enum·시간 patch → 확인 후 기존 조건 수정. 외부 LLM 없음 |
| 음성 입력 | `SpeechInputController`, `PleosSpeechInputSource` | 명시적 ON_DEVICE STT → 화면 내 텍스트. 15초 대기, 중단 시 정리 |
| 실제 경로 준비 | `RealRouteActivity`, `DriveExperienceEngine` | 개발용 고정 공개 출발지, 장소 5개, 현재는 후보 최대 2개 × 우선순위 3개 + 후보별 복귀편 |
| 경로 계약 | `RouteProvider`, `LiveRouteProvider`, `ProxyRouteParser` | source·옵션 echo·좌표·신선도 검사. Mock을 LIVE로 승격하지 않음 |
| 앱 간 전달 | `PreparedRouteContract`, `PreparedNavigationHandoff`, `NavigationFileProvider` | private cache JSON, 1시간 읽기 창, 대상 패키지 URI grant. 좌표/토큰을 공개 Intent extra로 보내지 않음 |
| PoC 진입 | `MockNavigationService.onCreateSession/onNewIntent` | 기본 정적 화면 또는 private packet을 읽은 `ActiveMysteryScreen`. import 자체는 활성화하지 않음 |
| 소유권 | `AndroidXNavigationOwnershipAdapter`, `OwnershipController`, `FocusLeaseSession` | 명시적 승인 → OEM/UX/GPS 재확인 → lease별 Focus → AndroidX `NavigationManager` |
| 위치·진행 | `AndroidPositionProvider`, `PositionSession`, `RouteMatcher`, `ArrivalGate` | GPS 약 1초 → 신뢰도/방향/연속성 검사 → TBT·도착 후보. 정차 확인은 별도 조작 |
| 재탐색·복귀 | `RerouteCoordinator`, `ProxyRouteRefreshClient`, `ReturnBudget` | 확인된 이탈만 재요청, 세대 취소, 최신 위치로 새 경로 검증. 복귀 준비와 시작 분리 |
| 지도 | `RoadMapRenderer`, `FollowCamera`, `TileStore` | OSM 현재 viewport 타일, 전방 국소 경로, 출처 표시, app-private 디스크/메모리 cache |
| 음성 안내 | `NavigationVoiceController`, Android TTS, Pleos fallback | 유한 회전 어휘만, 목적지/주소 입력 없음. 소유권·Audio Focus·단계별 중복 방지 |
| LIVE 평가 | `LiveNavigationResultActivity`, `LiveNavigationStore` | nonce·candidate·source·공개·중단·만료 검사 후 수동 평점. 최대 30건, backup 제외 |
| 서버 | `backend.app.create_app`, `backend.dev` | Bearer → bounded JSON → 고정 Kakao HTTPS → 최소 응답. loopback, worker 1, access log OFF |
| 개발 검증 | `backend.nav_poc`, `active_replay`, `gps_replay`, `smoke`, PowerShell scripts | 공개 개발 카탈로그·명시적 옵션으로만 replay/호출. 이번 감사에서는 실행하지 않음 |

`NavigationManager`는 이 저장소가 구현한 엔진 클래스가 아니라 AndroidX 1.7.0 SDK다. 실제 조사 대상은 해당 SDK를 호출하는 adapter/controller와 `ActiveMysteryScreen`이다. 메인 앱의 `PleosNavigationDataSource`는 OEM 진단·공개 시험 경로용이며 독립 PoC 소유권과 다른 경로다.

### 모델·의존성

- `DrivePreferences`, `RecommendationContext`, `JourneyEstimate`가 DEMO·실경로 선호 점수의 공통 입력이다. `GeoPoint`/`RouteCoordinate`가 APK 경계를 기준으로 나뉘어 있다. 단순 타입 중복만으로 공유 모듈 도입을 권하지 않는다.
- `RouteLeg`, `RouteOptions`, `PreparedRouteContract`, PoC `GuidanceRoute`/`RouteRequestOptions`가 HTTP·앱 간 계약을 이룬다. 공개 투영과 private geometry의 분리는 유지해야 한다.
- Android AGP 9.5.1, compile SDK 37. 메인 min 28/target 37, PoC min 29/target 34. PoC는 `-PnavHostPoc=true` opt-in 별도 APK다.
- Pleos Vehicle 2.3.2, NaviHelper 3.0.5, STT/TTS 3.3.1, Car App 1.7.0. Robolectric·app-testing·org.json JVM 구현은 테스트용이다.
- Python은 `requirements.txt` 전체 버전 고정이다. FastAPI/httpx/uvicorn과 pytest 등 개발 도구가 한 목록에 있다. dev/runtime 목록 분리는 선택 사항이며 의존성 삭제·일괄 업그레이드는 제안하지 않는다. 알려진 취약점 데이터베이스와의 최신 대조는 하지 않았다.

제품 Kotlin main은 앱 43파일/4,252줄, PoC 39파일/3,889줄이다. 테스트는 앱 JVM 소스 23파일, PoC JVM 29파일, 앱 계측 2파일이다. 파일 수·LOC는 품질 점수로 사용하지 않았다. 직접 경로·신뢰 경계는 자세히 읽었고, 자산 바이너리·생성 코드·SDK 내부 구현 전체는 줄 단위 감사하지 않았다.

## 4. 발견 목록

| ID | 등급 | 확인 수준 | 문제와 영향 |
|---|---|---|---|
| A01 | P1 | 합성 재현 | `RouteMatcher.accept:78`이 전체 geometry 길이 비율을 전체 도로 거리에 곱한다. 구간별 비율이 다르면 다음 회전·도로명·TTS 시점이 실제 구간 위치와 어긋남 |
| A02 | P1 | 합성 재현 | `GuidanceParser.parse:42`의 `segments.flatMap`이 단절 구간을 새 선분으로 연결한다. 입력에 없는 공간에서 ON_ROUTE 판정 가능 |
| A03 | P1 | 정적 확인 | `ActiveMysteryScreen`의 공개/도착/복귀 완료는 메모리뿐이다. 프로세스 재생성 후 평가·복귀 진입 조건을 잃음. 자동 재시작 차단은 올바르게 유지됨 |
| A04 | P1 | 기존 실행 관측 | Phase 5C Host 컨트롤 지연/미표시·전환 잔상. 활성 안내 중 종료 조작의 즉시 접근을 보장하지 못함. Host 원인은 미확정 |
| A05 | P1 | 정적 확인 | controller 단위 계약은 많지만 `ActiveMysteryScreen`을 생성해 start/stop/late callback을 함께 실행하는 테스트가 없음 |
| A06 | P2 | 정적 확인 + 실패 추정 | `ActiveRoutePacket.rememberStart/replace/load`가 `writeText`로 기존 cache를 직접 덮음. 중간 종료·쓰기 실패 시 유일한 복구 packet 손상 가능 |
| A07 | P2 | 합성 재현 | `DriveExperienceEngine.recommend:86`이 동일 geometry의 첫 옵션만 유지. 1,200초 기본 경로를 남기고 600초 시간 우선 경로를 DUPLICATE로 버림 |
| A08 | P2 | 정적 확인 | 기존/통합 추천기가 시간·출처·선호 계산을 별도로 유지. 기존 모드는 AUTH/RATE_LIMIT 실패에도 다음 후보를 요청하며 통합 모드와 정책이 다름 |
| A09 | P2 | 정적 확인, 지연은 추정 | main thread에서 매 GPS마다 전체 선분 탐색·후보 정렬·리스트 할당. FollowCamera의 반복 `atFraction`도 선형 탐색. 최대 100,000점에서 지연 위험 |
| A10 | P2 | 정적 확인 | 성공적으로 decode한 타일은 Screen 수명 동안 만료 재검사를 하지 않음. 파일 cache의 크기·기간 정리도 없어 공간·이동 지역 잔존 위험 |
| A11 | P2 | 정적 확인 | route/proxy packet은 유효기간 검사와 실제 파일 제거가 별개. 종료·만료 뒤 이름·좌표·단기 토큰 바이트가 cache에 남을 수 있음 |
| A12 | P2 | 정적 확인, 성능 영향 추정 | 서버가 경로 요청마다 `AsyncClient`를 생성·종료. 한 번 추천의 직렬 8회 요청 사이 upstream 연결을 재사용하지 못함 |
| A13 | P2 | 정적 확인, 장애는 추정 | Pleos TTS에는 15초 watchdog이 있지만 Android TTS 발화 완료 콜백 유실에는 종료 기한이 없음. 다음 발화/중단 전 Audio Focus 잔류 가능 |
| A14 | P2 | 정적 확인 | LIVE 평가 기록을 읽어도 실제 추천의 `recentIds`에는 DEMO 이력만 전달. LIVE 평가를 남긴 같은 후보를 다시 추천할 수 있음 |
| A15 | P3 | 전체 참조 검색 확인 | `FakeLocationProvider`, `UnconfiguredRouteProvider`의 제품 호출부가 없고 테스트에서만 사용. main 배치의 선택적 정리 후보 |
| A16 | P3 | 정적 확인 + 기존 환경 대조 | `backend.dev.provision`이 serial 5554/user 10/package를 고정. 5C의 5556 환경을 표준 명령으로 재현할 수 없음. 이번에 provision하지 않음 |

### 합성 재현 근거

저장소 main 소스를 그대로 입력해 로컬 Kotlin 2.2.10 compiler로 격리된 실행 파일을 만들었다. 외부 API·Android 앱·내비게이션 시작 없이 assert 3개가 통과했다. 기존 테스트에 추가하거나 기존 테스트를 바꾸지 않았다.

1. **A01:** geometry 길이가 각각 약 100m/900m인 두 도로에 API 거리를 500m/500m, 두 번째 도로 시작 회전 offset을 500m로 둔다. 첫 도로 끝 위치를 넣으면 ON_ROUTE지만 회전까지 약 **399m**로 계산된다. 원인은 `road_index`와 도로별 거리 대응 대신 전체 비율을 사용하는 것이다. 실제 카카오 응답에서 이러한 비율 차이의 빈도·오차는 미측정이다.
2. **A02:** 첫 선분 0–100m, 다음 선분 500–600m인 정규 계약 JSON을 `GuidanceParser`에 전달한다. 파서가 수용하고 300m 지점이 **ON_ROUTE**가 된다. 실제 상류에서 단절 응답이 발생했다는 증거는 없으며, 신뢰 경계가 이 입력을 거부하지 않는다는 재현이다.
3. **A07:** 세 옵션 geometry가 같고 RECOMMEND/TIME/DISTANCE가 각각 1,200/600/900초, 예산 60분, 편도, stay/buffer 0인 fixture를 넣는다. **1,200초 선택 / 600초 DUPLICATE**를 확인했다. 옵션 순서가 결과를 결정한다.

이 재현의 의미는 “기존 회귀가 모두 통과해도 빠진 입력 조합에서 잘못된 결과를 낸다”는 것이다. 실차 안전 판정이나 실제 API 데이터 품질 측정으로 확대하지 않는다.

## 5. 내비게이션 안전성 상세

| 검사 요청 | 확인한 방어 | 잔여 위험·결론 |
|---|---|---|
| 소유권 이중 획득 | REQUESTED/ACTIVE 중 재요청 거부, lease별 callback, native owns 확인, grant 직전 readiness 재확인 | 코드에서 이중 획득 결함을 확인하지 못함. 초기 타 앱 Focus는 UNKNOWN이고 Phone Projection 완전 탐지는 미검증 |
| 상실 후 안내 지속 | loss/환경 변경/Host stop → generation 증가 → voice·request·GPS 정리, publish 차단 | 모델·adapter 회귀 있음. 실제 Host/Binder/Screen 조합은 A05 및 실기 검증 필요 |
| 잘못된 GPS 회전 | accuracy/age/order/방향/급점프/역행/모호성 검사, 불신 시 loading, 약 10초 지속 시 stop | A01/A02 때문에 ON_ROUTE 자체가 회전 정확성을 충분히 증명하지 못함 |
| 오래된 재탐색 응답 | coordinator generation, cancellation, ACTIVE/allowed, 최신 fix로 새 matcher 검사, 만료 검사 | 단위 회귀 있음. 실화면 stop와 응답 동시 도착은 A05의 공백 |
| 중첩 구간 진행 | 획득·재획득 시 복수 traversal 모호성 차단, 연속성·heading·jump 검사 | 5C의 heading 없는 복귀 재생이 REVERSED/AMBIGUOUS로 중단됨. 방어를 완화할 근거가 아님. 정차 재생 성공을 실차 복잡 루프 성공으로 확대 금지 |
| 복귀 실패·종료 | 원출발지 유지, 별도 준비/시작, 실패는 오류로 남김, guide 재탐색 실패 시 stop | A03/A06 복원·내구성 보완 필요. 복귀 준비 중 서버 단절의 실제 UI 조합은 미검증 |
| ETA 카드 잘림 | 활성 화면은 native destination estimate 대신 추정 footer, visible/stable 교집합과 글자 크기 검사 | legacy 정적 MOCK native ETA 잘림은 기존 실행으로 재현됨. 호스트 전체 해결로 보고하지 않음 |
| 컨트롤 지연 | 공식 action/menu, 활성 메뉴는 같은 Screen. Surface 임의 hit target 없음 | A04. `refresh(true)`도 이미 예약된 갱신을 앞당기지는 않아 앱 측 최대 약 1초 추가 대기 가능. Host 지연 전체 원인이라고 단정 불가 |
| TTS 중복·오발화 | route/maneuver/stage·utterance 세대, 유한 어휘, focus loss 시 OFF, 목적지/도로명 미입력 | A01 시점 오류는 TTS에도 전파. A13 완료 유실 위험, 실제 음향·pre-roll 실효·앰프 동작 미검증 |
| 종료 뒤 자원 | lifecycle onStop에서 adapter·voice·GPS·ux·OEM·focus·renderer close, late callback generation | 해제 오류를 삼키는 GPS 경계와 native 해제 성공은 실기 관측 필요. 누수를 실제 확인한 것은 아님 |

`OwnershipController`의 세대·stale lease·cleanup debt, `ReadinessFocusPort`, `PrivacyTripFactory`의 식별정보 거부, `ArrivalGate`의 다중 fix와 명시적 확인, waypoint LIVE 차단은 필요한 안전 코드다. 인터페이스가 작거나 한 플랫폼 구현만 있다는 이유로 제거하지 않는다. GPS 재획득을 성공시키려고 ambiguity·jump·반대 방향 임계값을 느슨하게 만드는 수정도 권하지 않는다.

## 6. 추천·자연어·데이터 품질

- DEMO `DestinationEngine`은 필터 후 점수·seeded randomness를 적용한다. 최근 방문, 제외/필수 장소 충돌, 동행, 시간, 알려진 환경·실측 range를 우선 검사한다. SOC/연료로 range를 만들어내지 않는다.
- `DriveExperienceEngine`은 최대 2개 후보와 우선순위를 제한하고, 복귀편을 별도 요청하며 초 단위 체류·buffer·최종 deadline을 재검사한다. 현재 경관 경유가 없으므로 왕복 최대 **8회** 요청이다. 검증 경유가 추가되어도 코드 상한 12가 있으며 현재 구조상 최대 10회다. 느린 요청 시간의 실측은 하지 않았다.
- 목적지/경로 점수의 기분별 가중치는 초기 가설이다. 측정된 즐거움·경관 품질로 취급하지 않는다. 반복 점수는 같은 좌표쌍의 정확 일치 비율이므로 샘플링이 다른 동일 도로를 잘 찾는다는 증거가 없다.
- 실제 공개 장소 5곳, 동행 적합성은 SOLO에 제한된다. 주차·운영·차량 진입은 UNKNOWN이다. **운영 가능한 실제 경관 검증 후보 0곳**, 연구 후보 2곳은 `PendingScenicCatalog`에서 배제된다. 경유 LIVE gate는 CLOSED다.
- `PreferencePatch`는 언급하지 않은 조건을 보존하고 긍정/제외/비교 제거를 구분한다. `PreferencePatchJson`의 strict reader는 아직 외부 provider와 연결되지 않은 검증 계약이며 제품 호출이 없다고 보안 검증과 테스트를 즉시 삭제하지 않는다.
- 규칙 해석은 부분 인식·OR·상충·모호한 시간을 적용 불가로 남긴다. 자유로운 한국어 전체의 의미 보장은 없다. STT final 실수신은 PARTIAL이다.
- A07은 경로 중복 제거와 점수 비교의 실제 논리 오류다. A08은 두 추천 모드의 정책 차이, A14는 LIVE 이력 연결 누락이다. 현재 평점으로 학습하는 개인화 모델은 없으며 이번 감사에서 새 기능으로 요구하지 않는다.

## 7. 보안·개인정보 결과

### 키·이력·APK 검사

`git rev-list --objects --all`의 **1,173 objects / 665 blobs / 172,994,368 bytes**를 읽었다. text로 분류한 454 blobs에 private-key header, AWS access-key 형태, GitHub token 형태, Kakao/프록시 변수의 긴 literal 할당 패턴을 검사했다. 로컬에 구성된 알려진 비밀값 1개는 UTF-8/UTF-16LE 바이트로 모든 reachable blobs와 APK 압축 해제 entry에 대조했다. **알려진 값 일치 0 / 대표 패턴 일치 0**. 원문·일부 문자·해시를 보고서에 남기지 않았다.

대조한 APK는 로컬 `app/build/intermediates/apk/debug/app-debug.apk`, `nav-host-poc/build/outputs/apk/debug/nav-host-poc-debug.apk`와 5C 보존본 `final-app.apk`/`final-poc.apk`다. unit test 재실행을 새 APK assemble·release 검사로 표현하지 않는다. 기존 비제외 파일 667개와 신규 감사 문서에도 알려진 값 UTF-8/UTF-16LE 일치가 없었다. Git 추적 파일 중 `.env`, `local.properties`, 서명키 및 APK/AAB 파일명은 발견되지 않았다. `.env.example`은 설정 틀이다.

이는 **알려진 값·대표 패턴 검사**다. 알 수 없는 비밀, 분할/난독화 값, 이미지 OCR, unreachable/reflog/삭제 객체, 원격 PR·fork·캐시·사용자의 외부 저장본은 검사하지 않았다. 공급자 계정의 키 유효성·권한·회전 여부를 조회하지 않았다. 공개 저장소 전환 전 별도 검토가 남는다.

### 신뢰 경계

| 항목 | 확인 결과 |
|---|---|
| API 키 | 서버 환경변수/ignored `.env`. 앱은 공급자 키 대신 단기 로컬 토큰을 사후 전달받음 |
| release 설정 | 메인 release `ProxyConfiguration.load()`는 null. debug의 두 loopback 주소만 HTTP 허용. 상용 서버 인증이 구현됐다는 뜻은 아님 |
| 프록시 인증 | 충분한 길이 토큰 형식, constant-time 비교, 1시간 만료, 인증 전 upstream 호출 차단 |
| 요청 제한 | 단일 프로세스 분당 30/세션 100 기본 상한, lock 사용. 분산·사용자별 제한 아님 |
| 입력 | strict 숫자·유한 좌표·범위·중복 point·최대 2 waypoint·extra 금지, 요청 4KB 제한 |
| 네트워크 | 고정 HTTPS upstream, redirect OFF, trust_env OFF, connect 3초/HTTP 동작 8초/전체 네트워크 10초, 응답 2MB |
| body 대기 | middleware의 요청 stream 수신 자체에는 앱 수준 전체 기한이 없음. 현재 loopback 범위에서는 배포 차단 결함으로 집계하지 않았으나 공개 서버로 전환하면 별도 통제가 필수 |
| 오류 | 허용 error enum만 응답. validation 입력·상류 body/URL/헤더·예외 원문 미출력. 이른 인증/제한 응답은 공통 no-store 후처리 밖이나 민감 body는 없음 |
| 결과 Activity | exported이지만 pending nonce·candidate·source·만료·공개·중단 검사 후 사용자가 평점 선택. nonce를 가진 대상 앱의 완전성이 신뢰 가정이며 package signature 검증은 없음 |
| 파일 전달 | 제한된 FileProvider 경로·읽기 mode·유효기간, 명시 대상 URI grant. cache 유효기간과 삭제는 달라 A11에 해당 |
| 위치 보관 | 연속 GPS를 DB로 저장하지 않음. private 경로 packet에는 원출발지/목적지/geometry가 있고 OSM tile cache로 이동 지역이 추론될 수 있어 “위치정보 저장 0”이라고 표현하면 부정확 |
| 기분·음성 | 규칙 해석은 외부 전송 없음. Pleos STT ON_DEVICE, 앱 코드의 오디오 upload 없음. SDK 내부 전송을 패킷 측정으로 검증한 것은 아님 |
| 로그 | 좌표·목적지·음성 원문 대신 유한 상태. 활성 화면의 FIX/progress·Trip·surface·voice 진단은 debug flag 밖에서도 호출되어 빈도·보존 최소화 필요 |
| 저장·backup | DEMO 암호화/backup 제외, LIVE prefs 명시 제외, PoC allowBackup=false. LIVE 방문 목록은 평점·공개 후보 ID·시간이고 새 실제 사용자 데이터는 읽거나 출력하지 않음 |

확인된 키 유출이나 인증 우회는 없다. A11의 잔존 바이트와 시스템 채널 목적지 비공개 PARTIAL은 별개 위험이다. 운영 사용자 인증·HTTPS 원격 서비스·전체 비용 한도·보존/동의 절차 없이 loopback 개발 프록시를 인터넷에 공개해서는 안 된다. 이번에 외부 서비스 배포·서버 시작·실제 API 요청은 하지 않았다.

## 8. 성능·자원: 측정과 추정 분리

| 항목 | 근거 | 판정 |
|---|---|---|
| 기존 soak | 3B-4 `soak.json`: 공개 위치 고정 500 updates / 632.6초 / same_process / real_road_drive=false | 약 10.5분 정차 관측. 장거리·수시간 주행 증거 아님 |
| 기존 메모리 | early/late TOTAL PSS 79,463 → 86,153, 증가 6,690 KiB(약 6.53 MiB) | 단 두 표본의 증가. 누수·정상 cache 증가 구분 불가 |
| GPS | requestLocationUpdates 1,000ms / 0m, 전경 구독, 캐시 fix도 신선도 검사 | 일정한 처리량. 실제 전력/GNSS 빈도 미측정 |
| matcher | 전체 선분 O(N), 근접 후보 정렬 최악 O(N log N), 매 fix 임시 객체 | A09. CPU 시간·frame 지연 실측 없음 |
| camera | 반복 표본마다 `RouteGeometry.atFraction` 선형 탐색, 최대 약 1.5km 전방 창 | N×표본 수 비용. 이진 탐색 등 작은 수정 후보 |
| 지도 타일 | 현재 viewport만, 2 workers, pending 중복 차단·시작 전 viewport 재확인, decode cache 기본 96개 또는 wanted×2 | 대량 선다운로드 없음. 실제 요청량·장시간 cache 크기는 미측정, A10 |
| 타일 취소 | shutdownNow가 진행 중 HttpURLConnection을 직접 disconnect하지는 않음, connect/read 5초 | 자원 종료 지연 가능. 전체 수신 시간 기한·느린 stream 시험 없음. 실제 누수 확인 아님 |
| 템플릿 | 기본 약 1초 refresh coalescing, tick·GPS·tile 콜백 공유, immediate가 기존 예약을 추월하지 않음 | 불필요한 정지 상태 갱신 및 컨트롤 지연 후보. Host 원인과 분리 필요 |
| 앱 렌더 | `root.removeAllViews` 기반 재구성, 조건/UX 갱신 시 동기 save·View 재생성 | 입력 포커스는 5C 보완됨. Keystore/commit/렌더 지연 실측 없음. Compose 등 재작성 근거 없음 |
| route 요청 | 통합 비교 직렬, 중복 geometry는 응답 이후 판정 | 실제 비교 목적의 3 priority 요청을 근거 없이 없애지 않음. A07/A08 먼저 수정 |
| 서버 | 이벤트 루프 async HTTP, per-request client, JSON/geometry 정규화는 loop에서 동기 CPU 작업 | A12. 30회 burst·큰 응답 동시성 시험 없음. 인증된 로컬 개발 부하 기준으로만 평가 |
| Focus 자료구조 | lease와 native callback identity를 service 수명 동안 보존 | 늦은 grant를 구분하기 위한 안전 목적. 연속 재연결로 참조가 늘 가능성은 있으나 누수 실측 없음. 무조건 clear하면 안전성이 깨짐 |

성능 개선은 수치를 먼저 확보하고 진행한다. map matching search window를 줄이더라도 GPS 재획득·중첩 구간의 전체 탐색 fallback과 모호성 판정은 유지해야 한다.

## 9. 기존 492개 테스트의 의미

이번 재실행은 **앱 JVM 196 + PoC JVM 196 + FastAPI 90 = 482 PASS**, 실패/오류/skip 0이다. Android 계측 **10 PASS는 5C 기존 실행 근거**이며 이번에는 기기·앱·사용자 데이터를 건드리지 않아 재실행하지 않았다. 따라서 “이번에 492개를 모두 다시 실행했다”는 주장은 하지 않는다. 합성 감사 probe 3개도 기존 492개에 더하지 않는다.

| 종류 | 수·내용 | 검증하지 않는 것 |
|---|---|---|
| 앱 JVM | 196. 추천/제외/상태/복원 codec 40, 자연어·patch·입력 계약·STT 모델 61, 실제 경로·통합 비교·handoff 관련 67, Vehicle/Navi session 27, 예제 1 | 실제 Vehicle·NaviHelper·마이크·Keystore·AndroidLocationManager 실기 동작 |
| PoC JVM | 196. matcher 14, ArrivalGate 5, PositionSession 2, reroute 6, refresh HTTP 10, ownership/Trip 계약, TTS controller 33, Pleos fake session 7, 지도·packet·UI 계약 등 | 실차 GNSS, 실제 Focus/Binder, 스피커 청취, 실제 Host 렌더/컨트롤 성능 |
| 공식 SDK 계약 | AndroidX `TestNavigationManager` 3 및 Robolectric adapter/Trip 검사 | 공식 app-testing의 FakeHost이며 Pleos Host 성공/포커스 실획득 시험이 아님 |
| backend | 90 pytest 항목. `TestClient`/`MockTransport`, 인증·상한·입력·정규화·오류·개발도구/replay guard | 실제 Kakao SLA·traffic 품질·실제 socket 전체 timeout·상용 부하 |
| 계측 | 5C 10개 = UI 검증 9 + 예제 1, 3개 density/font 조건 반복 | 30회 반복을 고유 30개로 세지 않음. Navigation PoC 전이 계측 없음 |
| AVD E2E | 5C 추천/준비/안내/공개/중단/도착/평가/부분 복귀 화면·로그 | 자동 회귀 suite가 아님. 원 복귀 경로 연속 완주·실차·전체 시스템 비공개 미검증 |
| 실제 SDK 실행 | Phase 1 Vehicle 응답, NaviHelper 연결/공개 시험, 후속 Focus·Trip·ON_DEVICE TTS 콜백 관측 | 위 492개에 실제 SDK 테스트가 별도 다수 포함된 것으로 계산하지 않음. STT final 미수신, TTS 실청취 미검증 |

앱 집계의 영역별 숫자는 XML suite를 기준으로 분류했다. JVM 실행은 앱 21 suites, PoC 29 suites다. JUnit 테스트 파일 23개 중 일부는 Fake 보조 파일이므로 파일 수와 suite 수가 다르다.

핵심 공백은 A01 도로별 비율/회전 앵커, A02 geometry 단절/연결, A03 process death 뒤 도착·복귀 전이, A05 Screen+실제 adapter 경계의 생명주기, A06 쓰기 도중 실패, A07 동일 geometry/다른 시간, A10 실제 renderer의 타일 만료, A13 Android TTS 무콜백, A14 LIVE 방문 직후 재추천이다. line/branch coverage 수치는 산출하지 않았으므로 “전체 커버리지” 백분율은 없다.

기존 테스트들은 검증 층이 달라 이름이 비슷해도 중복 삭제 근거가 없다. 예제 합계 테스트 2개는 제품 보증 기여가 작지만 이번에는 그대로 보존했다. 안전 계약 테스트를 줄여 숫자를 낮추는 계획은 없다.

### 실행 기록

```powershell
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
# 90 passed, Starlette/httpx deprecation 경고 1

# 프로젝트 전용 GRADLE_USER_HOME/ANDROID_USER_HOME을 사용
.\gradlew.bat '-PnavHostPoc=true' ':app:testDebugUnitTest' ':nav-host-poc:testDebugUnitTest' '--rerun-tasks' '--offline' '--no-daemon' '--console=plain' '-Pkotlin.compiler.execution.strategy=in-process'
# BUILD SUCCESSFUL, 55 tasks executed, XML 합계 각 196/실패 0
```

첫 Gradle 시도는 PowerShell의 인자 해석으로 `.compiler.execution.strategy=in-process`를 task로 전달해 실행 전 실패했다. 옵션을 문자열로 고쳐 전체 재실행했다. 제품 결함으로 집계하지 않았다. 기존 Car App/deprecated API·JDK native access·Starlette 경고는 남는다.

## 10. 불필요한 복잡성과 보존할 코드

| 후보 | 결론 |
|---|---|
| 두 실제 추천 엔진 | 호출부가 모두 있고 debug 비교 기능에서 사용된다. dead code가 아니다. A08 정책 차이를 먼저 고정하고 공통 계산만 최소 통합하거나 비교 기능의 필요성을 결정 |
| DEMO와 실제 경로 분리 | 출처·실제 도착·개인정보 의미가 달라 유지. 통합 상태기계로 합치면 가짜 주행/평가 혼합 위험 |
| Dynamic/GPS/Ownership 실험 Screen | `MockNavigationService` chooser에서 실제 도달 가능. 사용하지 않는 코드로 단정 불가. 제출 APK에서 노출 범위만 별도 결정 |
| Focus/Navigation/Voice 인터페이스 | 늦은 콜백·native 경계·테스트 대체 구현에 실제 사용. “한 구현”만으로 제거 금지 |
| 두 UX monitor | 다른 APK에서 물리 display context로 fail-closed를 보장. 소규모 중복 때문에 새 공통 모듈 도입은 보류 |
| JSON strict reader | 제품 외부 provider 호출은 없지만 의도된 검증 경계·계약 테스트가 있다. 일반 org.json으로 단순 치환하면 중복 key/강제 변환 검사 퇴행 가능 |
| 반복 catch/close | 여러 독립 자원을 끝까지 정리하고 원문 유출을 막는 부분은 유지. 가독성을 위한 일괄 catch 삭제 금지 |
| test-only Fake main 배치 | A15. 호출 검색 완료 후 test source로 이동 가능한 두 작은 항목만 선택적으로 제안 |
| 라이브러리 | Material은 테마 style 참조가 있고, Robolectric/app-testing은 테스트 사용. 확인된 불필요 런타임 라이브러리 삭제 수 **0개** |

**간소화 예상:** 순감소 LOC는 미산정이다. 정책 중복과 fixture 배치 정리가 후보이며, 추가될 회귀·입력 검증을 제외한 절감 수치를 꾸미지 않는다. 새 프레임워크·지도 SDK·DI 계층·상태관리 재작성은 계획하지 않는다.

## 11. Phase 보고서 대조와 남은 제약

`docs/phase0`–`phase5`의 상위 Markdown **37개**(구현 계획 포함)를 목록화하고 판정·제약·마지막 갱신 문구를 대조했다. 과거의 “당시 미검증”을 현재 전체 미구현으로 복사하지 않고 후속 실행 보고서를 우선했다. 증거 디렉터리의 텍스트/JSON 집계도 관련 항목별로 읽었지만 모든 PNG를 시각적으로 다시 감사하지 않았다.

| 단계 | 현재 승계할 사실·제약 |
|---|---|
| 0/0B | 앱·빌드 기반 확보. 당시 SDK/API 미검증은 후속 Phase 1 실행으로 일부 해소 |
| 1A Vehicle | SOC/odometer 실응답, fuel 미지원. gear/range 공개 계약 및 실차 변화 검증 미완료 |
| 1B/1C | OEM NaviHelper 경로는 목적지 노출 확인. 일반 overlay NOT_VIABLE. 독립 Host 정적 표시 성공은 전체 비공개·주행 승인과 다름 |
| 2A | DEMO 상태·암호화 복원. 실제 방문 기록과 분리 유지 |
| 2B/2B-1.5 | 실제 API 호출 0이라는 초기 상태는 후속 LIVE 성공으로 대체. 주차/진입/운영 적합성 UNKNOWN은 유지 |
| 3A/3B-1 | 모의 진행·GPS 실험 성공. 비율 기반 안내·ETA는 근사, 복잡 GNSS/긴 경로/실차 검증 미완료 |
| 3B-2/2.5/3B-3 | 초기 CLOSED 실연동은 후속 명시적 정차 AVD 활성 관측으로 일부 해소. 공통 기본 gate와 실제 실험 gate를 구분. DESTINATION_PRIVACY PARTIAL 유지 |
| 3B-4 | 재탐색·TTS 콜백·복귀·약 10.5분 soak. 청취/실차/장시간 검증 아님 |
| 4A | AI_PROVIDER RULE_BASED, STT PARTIAL. 임의 장소·복잡한 OR·한국어 전체·외부 LLM 미지원 |
| 4B | 통합 비교와 선택 경로 유지. ROUTE_QUALITY_EVIDENCE PARTIAL, 실제 경관 검증 후보 0곳, waypoint LIVE CLOSED |
| 5A/5B/5C | 5A/5B 계획의 “미구현”보다 5C 실행 결과 우선. VISUAL_ACCEPTANCE PENDING_USER_REVIEW, Host 지연/legacy ETA, 지도 야간·TalkBack/전체 로터리·두 번째 물리 해상도 미검증 |

5C는 heading 없는 겹친 복귀 구간에서 안전 종료한 후 현재 위치로 경로를 재요청해 마지막 접근 구간을 완료했다. 이를 원 복귀 경로 전체 연속 주행 성공으로 바꾸지 않는다. Phase 5C READY_WITH_LIMITATIONS를 실차 배포 READY로 승격하지 않는다.

## 12. 제출 전 조건과 최종 판정

통제된 정차 데모 제출 전에는 A01/A02의 입력·안내 정책 수정과 회귀, A03/A06의 복원·쓰기 실패 보완, A04 종료/조작 접근 검증, A05 핵심 통합 상태 전이 검증을 먼저 완료한다. 결과물은 정차 개발용으로 명시하고, STT·경관·비공개·호스트·시각 승인 제한을 제출 문구와 시연 범위에 일치시킨다. 공개 자료는 키 literal 검사에 더해 화면·문서·증거를 별도 검토한다.

Phase 6B는 이 감사의 ID·기준 해시·기존 미커밋 변경을 고정한 작은 수정 묶음으로 시작할 수 있다. **6B 진입 허용과 배포 허용은 다르다.** 실차·공개 서버·원격 사용자 서비스는 위험 등록부의 별도 게이트를 통과해야 한다. 이번 감사는 6B 구현 승인이나 자동 실행 요청으로 해석하지 않는다.

**최종 판정: AUDIT_WITH_LIMITATIONS.** 주요 모듈·신뢰 경계·테스트·이력 분석은 완료했다. 실제 Host/차량·음향·장시간 성능·전체 시스템 개인정보 채널·공급자 승인 검증은 제한된다.

미실행: 기기 계측 재실행, 실제 경로 시작, replay, 실차, 서버/API 호출·배포, commit/push. 남은 위험: 기존 492개 통과로 A01–A16과 실제 주행·상용 배포 적합성이 해소되지 않는다.
