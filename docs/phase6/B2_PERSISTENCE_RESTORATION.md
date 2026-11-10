# SURPRIDE Phase 6B-2 — 원자적 경로 저장과 안전한 세션 복원

2026-11-10 · **READY_WITH_LIMITATIONS**

**PERSISTENCE_INTEGRITY = VERIFIED_CONTRACT · SESSION_RESTORATION = PARTIAL · DESTINATION_PRIVACY = PARTIAL**

저장·명시적 결과 복원 계약은 검증했다. 실제 Host의 모든 재진입 화면과 활성 안내 E2E는 검증 완료가 아니다. 실차 안전성·상용 배포 승인이 아니다.

## 1. 기준·범위·Git

시작은 `main / 3a88b32422c141aa8082ab8444e50313b551a2a9`, clean이었다. 과거 6A HEAD를 현재로 간주하지 않았다. [6A 감사](CODEBASE_AUDIT.md)의 A06/A03, [B2 계획](REFACTOR_PLAN.md), [위험 등록부](RELEASE_RISK_REGISTER.md)의 R03, [B1 결과](B1_GUIDANCE_ACCURACY.md)를 현 코드와 대조했다. B1 소스·517 JVM/서버 검사·기존 계측 10개를 기준으로 보존했다.

수정은 packet 저장, 명시적 결과 metadata, 관련 Screen/진입점, 기존 평가 journal 및 테스트다. Phase 5 화면의 색·배치·자연어/추천 흐름, B1 matcher/geometry, 서버 제품 코드는 변경하지 않았다. 기존 AndroidX 테스트 도구를 재사용하며 PoC androidTest runner를 연결했다. 새 제품 SDK나 상태관리 프레임워크는 없다. 사용자 지정 작업 트리에서 수행했고 커밋·Push·이력 변경은 하지 않는다.

종료도 같은 `main / 3a88b32422c141aa8082ab8444e50313b551a2a9`다. 추적 파일 13개 수정·신규 파일 16개를 미커밋으로 남겼다. 시작 시 추적 680개 중 범위 밖 667개는 바이트가 같다. staged 변경 없음, `git diff --check` 오류 없음.

## 2. A06 기존 결함과 원자적 저장

이전 `ActiveRoutePacket.rememberStart/replace/load`는 `writeText`로 정상 파일을 직접 덮었다. `rememberStart`는 디스크 쓰기 전에 메모리 envelope도 바꿨다. 저장 중단 시 잘린 JSON이나 메모리/디스크 불일치가 남을 수 있었다.

[`ActiveRoutePacket.kt`](../../nav-host-poc/src/main/java/com/surpride/navhostpoc/ActiveRoutePacket.kt)는 기존 `DemoStateStore`의 Android `AtomicFile` 방식을 재사용한다. 파일은 **기존 `cache/route-guidance.json` 그대로**다.

```text
전체 작업 잠금 → 이전에 읽은 committed raw와 현재 저장본 대조
→ 새 packet parse/검증 → startWrite → write/flush/fd.sync
→ finishWrite → openRead로 실제 committed bytes 대조
→ 성공한 경우만 packet/Screen 메모리 갱신
실패 → failWrite → 기존 상태 유지, 정상 완료 표시 없음
```

`AtomicFile`에는 자체 잠금이 없고 일부 finish 오류는 로그로만 처리한다. 따라서 단일 PoC 프로세스 안의 공통 잠금, 쓰기 전 sync, 완료 후 바이트 대조가 필요하다. [Android 공식 AtomicFile 계약](https://developer.android.com/reference/android/util/AtomicFile)과 [AOSP 구현](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/util/AtomicFile.java)을 대조했다. 하드웨어 손상이나 저장장치 전체 정전 내구성을 인증한 것은 아니다.

## 3. 실패 주입·동시성·교체

`AtomicRoutePersistenceTest`는 Robolectric의 AtomicFile 경계에서 start/부분 write/finish 예외/finish의 무응답 실패를 주입한다. 실제 앱 데이터를 손상시키지 않는다. 이전 bytes와 메모리 보존, 실패 후 재시도, 중단된 `.new` 및 이전 `.bak` 복구, 잘린 JSON 거절을 검사한다.

수정 전 **8개 중 7개 assertion 실패**를 확인했고, 원자적 구현 후 같은 검사 8개가 통과했다. 이후 동시 출발 저장과 stale packet·다른 목적지·출발 시각 변경 회귀를 추가했다. 출발 시각은 한 번 확정하면 다른 값으로 덮지 않는다. 교체 응답은 현재 목적지 또는 원출발지, 측정 시각, 현재 저장본과 대응해야 한다. 오래된 객체는 최근 commit이나 새 세션을 덮을 수 없다.

재탐색 교체는 저장 성공 뒤 packet·route·matcher를 바꾼다. 실패하면 `installRoute`가 false를 반환하고 기존 재탐색 callback의 안전 종료 경로로 간다. 새 경로를 저장 실패 상태에서 정상 완료로 발표하지 않는다. 모든 파일 읽기는 2,000,000 bytes 상한이 있다. 예외 body·좌표·token은 로그에 기록하지 않는다.

## 4. 기존 packet 호환성

guidance `schema_version=1`, 기존 경로·원출발지·출발 시각·복귀 경로 필드와 저장 위치를 유지했다. terminal metadata가 없는 기존 packet은 **비공개·미확인 결과**로 읽는다. 과거 위치나 `startedAt`만으로 도착·공개를 만들어내지 않는다. 기존 reroute/waypoint/원출발지 보존 테스트도 유지했다.

새 `terminal_state`가 존재하지만 손상됐거나 지원하지 않는 버전이면 전체 packet을 거부한다. 손상된 metadata를 무시하고 정상 안내를 만들지 않는다. 같은 nonce의 원래 handoff를 다시 보내도 이미 저장한 공개·도착 결과를 지우지 않으며, 현재 기록이 손상/만료된 경우 동일 nonce 재전달로 조용히 초기화하지 않는다. 새 준비가 필요하다.

## 5. A03 최소 terminal metadata

[`SessionOutcome.kt`](../../nav-host-poc/src/main/java/com/surpride/navhostpoc/SessionOutcome.kt)의 `SessionOutcome`과 packet의 `terminal_state`를 사용한다.

| 항목 | 저장 의미 |
|---|---|
| version | metadata 버전 1 |
| packet_hash | terminal_state를 제외한 전체 packet의 정규화 SHA-256. route·nonce·candidate·원출발지·시각 등과 결합 |
| updated_at / expires_at | 생성·갱신 시각과 복원 만료 시각 |
| revealed | 명시적인 공개 또는 명시적인 도착 확인에 의한 공개 |
| arrived_at | 사용자가 확인한 도착 시각. 자동 후보가 아님 |
| return_phase | NONE / REQUESTED / READY / COMPLETED |
| checksum | metadata 자체의 정규화 SHA-256 |

SHA는 우발적 손상·혼입 탐지용이며 공격자가 private 저장소를 수정할 수 있는 상황의 인증 수단은 아니다. 공개 입력은 이 metadata를 주장할 수 없도록 별도로 거부한다.

**저장하지 않는 것:** GPS fix·신뢰도·도착 후보·속도·정차 판단·Focus lease·ACTIVE 상태·TTS 진행·화면 시작 동의. 복원 시 matcher는 UNKNOWN, 위치는 null, 새 ownership adapter는 비활성, 음성은 OFF다.

## 6. nonce·route·버전·TTL·입력 경계

packet의 nonce 형식과 candidate 형식을 검사하고, 명시적 resume Intent는 **nonce와 candidate가 모두 현재 packet과 같아야** 한다. 해시는 nonce·route를 포함한 현재 packet 전체에 묶인다. version/필드 집합/타입/checksum/시간과 복귀 단계의 일관성도 검증한다.

전체 세션은 원래 `prepared_at`에서 최대 **1시간**, 각 경로는 기존 측정 시각/응답 만료 계약을 적용한다. 새 재탐색이 원래 세션이나 terminal 만료를 계속 연장하지 않는다. metadata 갱신 시에도 기존 deadline을 늘리지 않는다. 만료된 세션은 새 준비로 안내한다.

FileProvider의 기존 authority·경로·읽기 grant를 유지했다. provider handoff에 `terminal_state`가 있으면 거부하므로 외부 packet이 공개/도착을 주장할 수 없다. 좌표·geometry·단기 인증정보를 새로운 외부 Intent extra로 전송하지 않는다. 복귀 재진입에는 opaque nonce와 public candidate ID만 사용한다.

## 7. Screen 복원과 자동 시작 금지

[`ActiveMysteryScreen`](../../nav-host-poc/src/main/java/com/surpride/navhostpoc/ActiveMysteryScreen.kt)은 복원된 명시적 결과와 실시간 `ArrivalGate`를 분리했다. confirmed arrival은 UI·평가·복귀 자격에 사용하고, 과거 후보를 fresh candidate로 복원하지 않는다. 공개/도착/복귀 요청은 저장 성공 뒤 UI 결과를 바꾸며, 실패하면 안내 중단·저장 오류를 표시한다.

`MockNavigationService`는 명시적 resume action만 캐시와 결합한다. 일반 data 없는 launcher 진입은 기존 정적 MOCK 진입을 유지한다. 잘못된 resume/만료/손상 packet은 목적지 없이 “SURPRIDE에서 다시 준비” 상태가 된다.

복원은 `pendingStart`를 설정하지 않는다. `navigationStarted`·TTS 발화·Navigation Focus 재획득도 호출하지 않는다. 실제 정차·현재 GPS·타 내비 상태는 새로 관측하고 필요한 안내 시작은 사용자가 다시 선택해야 한다.

## 8. 도착·공개·복귀 상태별 결과

| 재생성 시점 | 계약상 복원 결과 | 근거 구분 |
|---|---|---|
| A 추천/경로 준비 | 비공개 준비, 자동 시작 없음 | JVM/실기 fixture 재진입 |
| B 비공개 안내 중 | 비공개 결과만 유지, 위치·소유권·음성 권한 재사용 없음 | 실제 Screen 결합 회귀, 실제 활성 Host는 A04 제한 |
| C 도착 후보만 존재 | 후보·도착·공개 모두 복원하지 않음 | 실제 Screen 회귀 |
| D 명시적 도착 확인 | 확인 결과·공개와 평가/복귀 자격 유지, live candidate 없음 | Screen/lifecycle 및 실기 fixture |
| E 즉시 공개 | 같은 유효 세션의 공개만 유지 | Screen 및 실기 fixture |
| F 평가 저장 | 기존 완료 영수증 표시, 방문 기록 중복 추가 없음 | 앱 실제 Activity/저장/재진입 검사 |
| G 복귀 요청/HTTP 준비 중 | REQUESTED 유지, 요청/위치 권한은 복원하지 않고 재선택 안내 | Screen 및 실기 fixture |
| G 복귀 경로 준비 완료 | READY 경로만 복원, 명시적 새 시작 필요 | packet 및 실기 fixture |
| H 복귀 완료 | COMPLETED/안내 종료, 새 복귀 자동 시작 없음 | Screen 및 실기 fixture |
| I 완료 세션 재진입 | 동일 결과를 표시하고 시작/평가 중복을 막음 | 계약 회귀 |
| J 만료/다른 nonce/손상 | 정상 경로·TBT 대신 안전한 새 준비 안내 | 입력/Screen/실기 음성 없는 재진입 |

실기의 fixture 복원은 미리 생성한 **MOCK 사용자 결과**를 복원한 검사다. 실제 차량 도착이나 Host 버튼으로 도착 확인에 성공했다는 뜻은 아니다.

## 9. 평가 영수증과 중복 방지

기존 `LiveNavigationJournal`에 마지막 완료 세션의 nonce/candidate/preparedAt만 보관한다. 기존 public 방문 기록과 같은 commit에 저장한다. 새 준비는 이전 완료 권한을 초기화하고, 과거 방문 목록만으로 새 세션 권한을 만들지 않는다. 영수증은 해당 방문과 TTL까지 확인한다.

`LiveNavigationResultActivity`는 Bundle의 `saved=true`를 신뢰하지 않고 durable journal에서 완료 여부를 다시 계산한다. 임의/빈/오래된 nonce, 다른 candidate/source, 공개·종료 미확인은 평가·복귀 버튼을 승인하지 않는다. MOCK PoC packet을 DEVICE_API 평가 callback으로 바꾸는 것도 차단했다.

SharedPreferences commit 실패는 메모리 map을 이미 바꿨을 수 있어 이전 journal을 되돌린다. 이 문제는 기존 설치 APK에서 실제 실패를 확인하고 수정 APK에서 통과했다. 별도 native 검사에서 손상된 영수증이 정상 방문 목록까지 숨기는 회귀를 발견하여, 영수증만 무효화하도록 수정했다. 기존 30개 이력 정책은 유지한다.

## 10. 실제 Screen/lifecycle 통합 회귀와 독립 검토

실제 `ActiveMysteryScreen`, `TestCarContext`, `ScreenController`, AndroidX Screen stack, 기존 위치/음성/ownership port를 사용한다. 저장 성공/실패 후 재생성, 도착 직후 stop, 후보만 있는 재생성, 복귀 요청 중 재생성, 다른 nonce와 늦은 GPS callback, ownership loss, 실패 packet의 정상 TBT 없음, 중복 GPS 구독 방지, 자동 navigation/TTS 시작 없음을 검사한다.

독립 소스 검토는 **Critical 0 / Important 1 / Minor 0**을 보고했다. 조작 화면 pop 시 부모 UX가 UNKNOWN으로 초기화되어 공개/복귀 선택을 버리는 회귀였다. 실제 child row 클릭→pop→부모 onStart 테스트 **2개가 RED**였다. 고정 지연 대신 현재 packet에 묶인 일회성 메모리 동의를 두고, 새 정차 확인 후 처리했다. 15초 만료·세션 변경·운전 제한·stop/destroy는 동의를 폐기한다. 이 동의는 저장하지 않는다.

완료 영수증 검사, Native AtomicFile 검사는 실제 Android에서 실행하지만 전체 Host/Binder/플랫폼 소유권 E2E와 같지 않다. 테스트 수로 A04나 실차 안전성을 해제하지 않는다.

## 11. 전체 테스트·빌드

최종 성공 실행 기준은 **576 PASS**다. 기존 테스트 삭제·skip·안전 임계값 완화 없음. [검증 요약](evidence/b2/verification.json)에 집계·APK 해시·보존 결과를 기록한다.

| 검사 | B1 기존 | 신규 | B2 합계 |
|---|---:|---:|---:|
| 앱 JVM | 196 | 4 | 200 |
| PoC JVM | 219 | 38 | 257 |
| FastAPI | 102 | 0 | 102 |
| JVM/서버 | 517 | **42** | **559** |
| 앱 실제 계측 | 10 | 6 | 16 |
| PoC 실제 계측 | 0 | 1 | 1 |
| 고유 합계 | **527** | **49** | **576** |

PoC 신규 38개는 AtomicRoutePersistence 10, TerminalPacketIntegrity 10, SessionRestorationIntegration 17, 기존 ActiveGuidanceIntegration에 추가한 ownership/nonce 재생성 1개다. 앱 신규 JVM 4개는 영수증 계약, 앱 신규 계측 6개는 실제 Activity/journal, PoC 계측 1개는 native 파일/명시적 상태 여러 단계를 검사한다. 실행 반복 횟수를 고유 개수에 더하지 않았다.

`review-fixed-full.log` BUILD SUCCESSFUL과 XML의 앱 200/PoC 257, backend의 102 passed를 대조했다. 실패·오류·skip 0이다. `poc-instrumentation-final.log`는 OK (1 test), `app-instrumentation-final-retry.log`는 OK (16 tests)다. 도중의 기대 실패·계측 설정 오류·ANR은 아래처럼 별도 보존하며 무오류 연속 안정성으로 표현하지 않는다.

```powershell
$env:GRADLE_USER_HOME=Join-Path (Get-Location) '.gradle/codex-user-home'
$env:ANDROID_USER_HOME=Join-Path $env:GRADLE_USER_HOME 'android-user-home'
.\gradlew.bat '-PnavHostPoc=true' ':app:testDebugUnitTest' ':nav-host-poc:testDebugUnitTest' ':app:assembleDebug' ':app:assembleDebugAndroidTest' ':nav-host-poc:assembleDebug' ':nav-host-poc:assembleDebugAndroidTest' '--offline' '--no-daemon' '--console=plain' '-Pkotlin.compiler.execution.strategy=in-process'
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
```

비공개 작업 로그는 `.gradle/phase6-b2/`에 보존한다. `red-atomic`, `atomic-and-red-screen`, `red-terminal`, `red-entry-and-device-build`, `red-installed-review`, `red-installed-journal`, `app-current-instrumentation`, `red-review-control-pop`은 원인별 RED 증거다. 새 receipt API가 없던 최초 컴파일 실패는 assertion 재현과 별도로 기록한다. 기존 Starlette/httpx 및 Gradle 경고는 남는다.

## 12. 실제 Pleos 검증 방법

기존 `emulator-5554`, Connect x86_64, user 10 정차 AVD를 사용한다. 원래 private record/cache/설정과 GPS를 먼저 보관했다. 실제 사용자 위치·token·전체 packet 원문은 보고서에 넣지 않는다.

PoC 계측은 격리된 `cache/b2-native-contract`에서 실제 Android AtomicFile·parser·metadata를 실행하고 합성 fixture를 만든다. 앱 계측은 실제 Activity에서 평가를 저장·재생성·새 진입하고 원 journal을 finally에서 복구한다. 계측 이후 정차 Host에서는 이 **MOCK fixture**의 private cache를 사용해 프로세스를 종료하고 명시적 nonce/candidate 재진입을 관찰한다. 자동 안내를 시작하지 않는다. 원 데이터는 바이트 해시로 복원한다.

설치 산출물은 `app/build/outputs/apk/debug/app-debug.apk` 및 PoC outputs APK다. main의 과거 intermediates APK가 남아 있어 최초 설치를 바로잡았고, 최종 설치는 양쪽 APK 및 테스트 APK의 SHA-256을 기기와 대조했다. PoC 첫 계측의 runner 누락 crash는 test-only runner 연결 후 재실행해 해결했다. 실패 이력을 지우거나 기존 검사를 skip하지 않았다.

## 13. 실제 관찰과 A04 제한

| 정차 실기 관찰 | 결과와 한계 |
|---|---|
| 준비·공개 fixture cold 진입 | 비공개/공개 footer를 구분해 표시. [비공개](evidence/b2/host-prepared.png), [공개](evidence/b2/host-disclosed.png) |
| 도착 확인 fixture | 확인된 도착 footer가 나타났으나 Host 오류 overlay도 관찰. 전체 성공으로 판정하지 않음. [화면](evidence/b2/host-arrived.png) |
| 복귀 요청 중 fixture | 저장 계약은 native/JVM에서 확인. 해당 Host 진입은 정적 MOCK 화면으로 돌아가 화면 복원 미확인 |
| 복귀 READY / COMPLETED fixture | 각각 “복귀 경로 준비·안내 시작 전”, “출발지에 도착했어요”를 cold 진입에서 표시. [준비](evidence/b2/host-return-ready.png), [완료](evidence/b2/host-return-completed.png) |
| 만료 / 다른 nonce | 정상 활성 안내·Focus 없음. 일부 Host는 정적 화면/오류 overlay로 복귀해 자체 재준비 footer의 실표시까지는 미확인. [오류 관찰](evidence/b2/host-wrong-nonce.png) |
| 앱 평가 영수증 cold 진입 2회 | “오늘의 경험을 기록했어요” 표시, 시험 방문 1개·평점 4 유지. [평가 재진입](evidence/b2/host-review-cold-1.png) |
| 자동 시작 | 8개 PoC 진입의 own-process START_REQUEST 0, SurprideVoice requested 0, 각 단계 Focus empty, packet bytes 자동 변경 없음 |

단말에 표시된 합성 상태와 실제 사용자 도착 확인을 구분한다. MOCK fixture는 DEVICE_API 평가 callback으로 발신할 수 없다. 평가 검사는 별도의 유효 pending 테스트 설정에서 실제 평가 버튼을 눌렀으며, 실차 방문 인증이나 실제 안내→도착→평가→복귀 전체 E2E가 아니다.

Host 전환에서는 `CarApp.Act`의 **Surface isn't valid**와 오류 overlay/정적 화면 복귀가 관찰됐다. 일부 화면만 성공했으므로 SESSION_RESTORATION은 PARTIAL이다. A04의 실제 공식 컨트롤과 활성 TBT/TTS/Focus 획득→해제 E2E는 **BLOCKED**로 유지하고 시작하지 않았다. 최종 Focus 없음은 활성 acquire→release 주기 성공 증거가 아니다.

앱 최종 계측의 한 시도는 **FocusEvent(hasFocus=false) 5003ms timeout**으로 중단됐다. 직전 같은 앱 APK의 16개 성공과, 원래 데이터 복원 후 16개 재실행 성공을 모두 보존한다. 당시 프로세스는 이미 종료됐고 exit-info에 finished instrumentation의 force stop이 남아 Java 대기 trace를 확보하지 못했다. 원인은 확정하지 않았으며 앱 코드나 Host 중 한쪽을 무혐의로 판정하지 않는다. B3에서 결과 Activity/Host 전환 안정성을 함께 조사해야 한다.

검증 후 자체 두 앱을 종료하고 원 private 파일 5개의 바이트 해시를 확인해 복원했다. 원 GPS를 복원하고 이번에 만든 native fixture 폴더와 평가 fixture 파일을 제거했다. Focus는 비어 있다. 기존 AVD 초기화·사용자 이력 삭제·실차·서버 배포·API 호출은 하지 않았다. APK는 수정 빌드를 유지한다.

## 14. 개인정보·A11 잔여 위험

- PoC packet은 기존 private cache, allowBackup=false 범위를 유지한다. 앱의 `live_navigation_emulator.xml`은 기존 cloud backup/device transfer 제외를 유지한다.
- metadata는 논리적으로 최대 1시간이며 새 경로가 계속 연장하지 않는다. 같은 세션 공개만 복원하고 후보·과거 정차를 새로운 공개 동의로 사용하지 않는다.
- checksum은 암호화/사용자 인증이 아니다. private packet에는 원래 목적지·원출발지·geometry·단기 token이 남을 수 있다.
- 논리 만료는 물리 삭제가 아니다. 기존 cache, AtomicFile 보조 파일, 완료 영수증의 전체 cleanup/forensic 보존 정책은 A11 후속이다. 새 준비 시 완료 권한을 초기화하고 다음 저장에서 만료 영수증은 제외한다.
- packet의 parse/hash/sync는 기존 호출 스레드에서 동기 처리한다. 최대 크기/느린 저장장치의 지연은 측정하지 않았다. 원자적 저장 통과를 UI 지연·장시간 안정성 보증으로 바꾸지 않는다.
- DESTINATION_PRIVACY는 **PARTIAL**이다. OSM 라벨·시스템 다른 표면·종료 뒤 흔적까지 비공개가 증명된 것이 아니다. 실차·실음향·장시간 검증도 없다.

## 15. 감사 위험 후속 상태

6A의 당시 발견 수·원문은 보존한다. A06은 원자적 저장/실패 보존 계약으로, A03은 명시적 결과/평가 영수증 복원 계약으로 보완했다. R03은 저장·복원 계약 증거를 추가하되 실제 Host 활성 전체 흐름의 조건을 남긴다. A05는 실제 Screen stack/lifecycle 검증을 확대했지만 native Binder/Focus 조합은 PARTIAL이다. R04/A04와 A11은 미해소다.

## 16. Phase 6B-3 진입 조건과 판정

**제한을 유지한 6B-3 진입 가능.** 다음 B3는 A04 공식 Host 조작/즉시 종료 도달성·Surface 재연결 및 결과 Activity FocusEvent timeout → 남은 A05 플랫폼 경합 → A13 음성 callback/중단 경계다. 이번 packet/metadata/Screen/영수증 fixture와 B1 안전 검사를 기준으로 보존한다. A04가 해결되지 않은 상태에서 활성 안내 시연 합격을 선언하지 않는다. 실차·상용 배포는 별도 승인과 검증이 필요하다.

최종 **READY_WITH_LIMITATIONS / VERIFIED_CONTRACT / PARTIAL**. 판정에 포함하지 않은 범위는 전체 물리 보존/삭제(A11), 모든 실제 Host/Binder 경합, 실차·실음향·장시간이다. 잘못 판단하면 남는 비용은 각각 private 흔적 잔존과 실제 시연·주행 안정성 미보증이므로 해당 게이트를 유지한다.
