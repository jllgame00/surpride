# SURPRIDE Phase 6B-3 — Host·Screen 생명주기·음성 종료 보장

2026-11-10 · **READY_WITH_LIMITATIONS** · **Host ACTIVE = BLOCKED**

HOST_CONTROL = PARTIAL · FOCUS_LIFECYCLE = PARTIAL · SURFACE_STABILITY = PARTIAL · VOICE_WATCHDOG = VERIFIED_CONTRACT · DESTINATION_PRIVACY = PARTIAL

## 1. 기준과 범위

- 시작은 `main / 2dcd2adbb30f4a5a72febdb47ed277292d2a7280`, 작업 트리는 깨끗했다. 과거 감사 HEAD를 현재 기준으로 사용하지 않았다.
- [감사 A04/A05/A13](CODEBASE_AUDIT.md), [개선 계획](REFACTOR_PLAN.md), [위험 등록부](RELEASE_RISK_REGISTER.md), [B1](B1_GUIDANCE_ACCURACY.md), [B2](B2_PERSISTENCE_RESTORATION.md), [Phase 5 UI 검증](../phase5/UI_UX_FINAL_VALIDATION.md)을 대조했다.
- 기존 검사 576개: 앱 JVM 200 + PoC JVM 257 + FastAPI 102 + 계측 17(앱 16/PoC 1). 반복 실행은 고유 검사 수에 더하지 않는다.
- 제품 소스 변경 범위는 PoC Screen·Service의 Surface 정리/갱신, 기존 Focus/NavigationManager 경계의 유한 진단 이벤트, Android 음성 controller의 watchdog이다. 메인 앱 UI, geometry, matcher 임계값, packet/평가 포맷, 추천 엔진, SDK/의존성은 변경하지 않았다.
- 사용 SDK: AndroidX Car App/automotive/testing 1.7.0, NaviHelper 3.0.5, PleosTextToSpeech 3.3.1. 공식 Car App API 4 범위를 유지했다. 권한 우회·Surface 터치 버튼·overlay·Host 카드 강제 배치 없음.
- 기존 사용자 자료를 백업하고 바이트 단위 복원한다. Git 이력 변경·커밋·Push·AVD 초기화·실차·배포 없음.

## 2. FocusEvent ANR 조사

B2의 앱 계측 한 시도에서 `FocusEvent(hasFocus=false)` 5003ms timeout이 관측됐다는 기존 사실을 유지한다. 이후 16개 재실행 성공은 수정 증거가 아니다. 이번 시작 시 own-package exit-info를 읽었으나 앱 17건/PoC 16건 중 접근 가능한 trace는 각각 0건이었다. 이전 실패 프로세스는 이미 종료됐고 B2에는 finished instrumentation/force-stop 기록만 남았다. 당시 main/Binder의 대기 stack과 정확한 lock 보유자가 없어 원인은 확정할 수 없다.

별개로 B2에는 Host activity의 `Surface isn't valid, source.isValid() == false` 예외가 있었다. 이번 수정 전 APK 재진입에서도 오류 화면을 관찰했지만, 그 화면과 일치하는 새 Java stack을 확보하지 못했다. 과거 instrumentation 설정 오류의 stack을 이번 ANR 또는 Surface 오류의 원인으로 사용하지 않았다.

이후 수정 APK의 **새 Surface stack**은 확보했다. 처음 집계한 10건은 앱 계측 두 번째 실행으로 activity가 전환되던 구간에 PoC PID 30837에서 발생했고, `PixelCopy.request → BaseCarAppActivity.takeSurfaceSnapshot:374 → onPause:391`였다. 이는 **앱 프로세스 안의 AndroidX automotive renderer 경계**다. OEM Host 내부 stack 또는 Active Screen paint stack으로 분류하지 않는다. [공식 BaseCarAppActivity](https://developer.android.com/reference/androidx/car/app/activity/BaseCarAppActivity)와 실제 설치에 사용한 app-automotive 1.7.0 AAR bytecode를 대조했다. 해당 private snapshot 메서드는 holder Surface의 null/크기만 확인하고 PixelCopy를 호출하며 Exception을 catch해 `CarApp.Act`로 기록한다. 이 stack 자체는 잡힌 snapshot 실패이고 새 fatal crash/ANR stack이 아니다. source Surface가 왜 invalid였는지, 앱·Host의 Surface 전달 순서 중 누가 선행 원인인지는 미확정이다.

최소 정차 재현도 추가했다. 비활성 합성 준비 화면 → data 없는 메인 앱 LiveNavigationResultActivity → 일반 BACK 복귀를 두 번 시도해 1/2회 동일 snapshot 예외가 새로 기록됐다. 다른 한 번 미재현을 성공적인 안정성 증거로 쓰지 않는다. 누적 새 오류는 11건이다. SDK private 메서드 변경/반사·OEM 변조나 근거 없는 SDK 교체는 하지 않았다. 이전 FocusEvent ANR과 이 caught snapshot 오류의 동일 원인도 입증하지 못했다.

확인한 앱 결함은 아래 두 가지다. ANR과의 인과관계는 확인하지 않았다.

1. `refresh(immediate=true)`가 이미 예약된 일반 갱신 때문에 반환된다. 사용자 조작이 앱 단계에서 최대 약 1초의 기존 예약을 기다릴 수 있다.
2. Active/정적 fallback Screen 모두 받은 Surface의 `release()`가 없었다. Active Screen은 이전 시작 주기의 늦은 callback도 수용했다.

main-thread 후보도 코드로 점검했다. packet 원자 저장/사용자 상태 저장, Canvas `lockCanvas`·지도 paint, SDK connect/close·Focus/NavigationManager 호출은 동기 작업을 포함한다. Screen 이벤트는 main executor로 직렬화되고, HTTP는 기존 worker/cancellation 경계를 사용한다. RerouteCoordinator의 재진입 가능한 synchronized 영역과 callback/취소 경계도 점검했다. 이번 자료에는 특정 파일 I/O·Binder·지도 작업의 5초 정지를 입증하는 native stack/프로파일이 없다. 따라서 이를 ANR 원인으로 단정하거나 저장 안전성을 제거하지 않았다.

## 3. 진단 계약과 관측 한계

`HostDiagnostics.kt`는 `SystemClock.elapsedRealtimeNanos()`와 미리 정한 enum만 기록한다. 경로/목적지/좌표/nonce/토큰/음성·조건 원문을 인자로 받지 않는다. 기존 FIX 로그에서도 진행·다음 회전 거리를 제거했다.

관측점은 `FOCUS_REQUEST`, `FOCUS_CALLBACK`, `OWNERSHIP_DECISION`, `SCREEN_ACTIVE`, `CONTROL_REQUEST`, `REFRESH_SCHEDULED`, `TEMPLATE_UPDATE`, `TEMPLATE_READ`, `SURFACE_READY`, `FOCUS_RELEASE`, `TERMINATED`다. 비동기 경합과 거부 경로가 있어 모든 실행이 단일 직선 순서로 끝나는 것은 아니다. `TEMPLATE_READ`는 앱의 `onGetTemplate` 진입이며 실제 Host 픽셀 표시 시각이 아니다.

`FOCUS_RELEASE`는 **해제 시도** 시각이다. 호출 전/생명주기 정리에서 중복될 수 있고 native 반환 성공을 입증하지 않는다. Focus 실제 상태는 별도의 공식 `dumpsys car_service --services AppFocusService`로 확인한다. `TERMINATED`도 Screen 정리 경계를 뜻하며 Host 내부 자원 0개를 보증하지 않는다.

## 4. A04 앱 측 갱신 수정

`ActiveMysteryScreen.refresh`는 하나의 Runnable을 재사용한다. 일반 갱신이 예약돼 있어도 immediate 요청이 오면 해당 작업을 제거하고 0ms로 당긴다. 이미 immediate가 예약됐으면 합친다. 일반 paint/invalidate는 기존 1초 간격을 유지하며 stop 때 Handler 작업을 제거하고 예약 상태를 초기화한다. 반복 invalidate 루프나 타이머 증식은 추가하지 않았다.

수정 전 `immediateRefreshPromotesAnAlreadyScheduledNormalUpdate`가 실패했고 수정 후 같은 가상 단조시각에서 통과했다. 이것은 앱 예약 지연 제거 증거이며 Host 메뉴 표시 지연 전체가 해결됐다는 뜻이 아니다. 공식 메뉴와 기존 UX를 유지했다. Host native ETA 카드 위치/크기, 메뉴 렌더링을 앱이 우회해서 고치지 않았다.

## 5. Surface와 정적 fallback

공식 [AndroidX SurfaceCallback 계약](https://developer.android.com/reference/androidx/car/app/SurfaceCallback)은 받은 Surface 인스턴스의 해제를 앱 책임으로 명시한다. availability callback은 크기/DPI 변경 때도 반복될 수 있으므로 이를 Focus 획득 사건으로 취급하지 않는다.

- Active Screen의 시작마다 callback epoch를 바꾼다. 이전 epoch의 area/destroy callback은 현재 Surface를 변경하지 않는다. 늦게 전달된 Surface handle은 해제한다.
- 교체 시 이전 handle, stop 시 현재 handle을 해제한다. foreground가 아니면 paint하지 않는다. Canvas unlock은 기존 finally로 유지한다.
- 현재 수명주기의 Surface destroy는 pending 시작/조작/재준비를 취소하고 adapter·음성 ownership·GPS·HTTP를 정리한다. 화면 없이 활성 안내를 유지하지 않는다.
- Surface가 없어진 동안 GPS 자동 재구독을 막는다. 새 Surface는 새로운 준비 GPS를 허용할 수 있지만 pending 시작을 복구하거나 Focus/NavigationManager/TTS를 자동 활성화하지 않는다.
- 같은 수명주기의 destroy callback이 교체 전/후 handle 중 어느 것인지 공개 API만으로 확정할 수 없으면 안전하게 중단한다. Java wrapper 참조가 다르다는 이유만으로 실제 파괴를 무시하지 않는다. 비용은 불필요한 재준비 가능성이다.
- 정적 MockNavigationScreen도 매번 받은 handle을 paint 후 finally에서 해제하고 onStop 때 callback을 해제한다.

데이터/명시적 RESUME action 없는 Service 진입은 의도적으로 정적 MOCK 화면을 선택한다. 이를 활성 경로 복원 성공으로 보지 않는다. 반대로 명시적 재진입에서 정적 화면/오류 overlay로 남는 Host 전환은 성공 처리하지 않는다.

## 6. A13 Android TTS watchdog

`NavigationVoiceController.emit`에서 Android TTS backend의 queue 수용 이후 발화별 deadline을 건다. 기존 `VoiceDelayPort`/`AndroidVoiceDelay`를 사용하며 새 프레임워크/SDK는 없다. Pleos backend의 기존 15초 SDK callback deadline은 그대로 유지한다.

기한은 `clamp(5000ms + Unicode code point 수 × 500ms, 10000ms, 30000ms)`다. 엔진·pre-roll 여유 5초와 보수적인 초당 2문자 예산이다. 통제 문구 `500미터 앞에서 우회전하세요.`는 17문자여서 13.5초다. 이는 청취 측정값이나 모든 엔진 속도의 보증이 아닌, Focus 보유의 유한 상한 정책이다. 실제 차량 음향에서 추가 보정이 필요하다.

session·utterance ID·Focus lease가 모두 현재일 때만 timeout을 적용한다. timeout은 엔진 stop, Audio Focus abandon, enabled=false, 오류 1회 및 `TIMEOUT` 상태로 끝난다. 다음 발화의 Focus를 이전 timeout이 해제하지 않는다. 자동 재발화·backend fallback은 없다. done/error/ownership loss/Audio Focus loss/stop/close도 기존 세대를 먼저 무효화한 뒤 timer를 취소한다. 동기 완료 callback을 뒤늦은 QUEUED 상태로 덮어쓰지 않는다.

실제 AudioManager/TTS SDK의 callback 성공과 사람이 들었다는 사실은 별도다. native 계측도 여기서는 fake speech/focus와 deadline 주입을 사용하며 실제 발화·실음향 성공으로 세지 않는다.

## 7. A05 실제 Screen 조합 검사

`ActiveGuidanceIntegrationTest`는 기존 harness를 재사용하고, 신규 케이스는 실제 `ScreenController` STARTED→CREATED/DESTROYED→STARTED를 통과한다. TestCarContext/TestNavigationManager를 사용하되 Focus·GPS·음성·HTTP는 기존 port에서 제어한다. 새로운 상태관리 controller를 만들지 않았다.

| 요구 시나리오 | 검증 경계 |
|---|---|
| A/B REQUESTED 중 stop, 늦은 grant | navigationStarted/Ended 0, Focus 회수, GPS 0, 중복 stop 무해 |
| C/D ACTIVE loss, queued GPS | 기존 Screen loss 검사 + 실제 stop 검사: Trip/발화 증가 없음, GPS 0, ended 1 |
| E/F HTTP 중 stop, 늦은 성공 | pending HTTP 1→0, 늦은 성공의 route generation/Trip 불변 |
| G UX 변경 중 음성 클릭 | 실제 template의 queued click을 제한 변경 후 전달, 음성 ON/발화 없음 |
| H 메뉴 중 GPS/도착 후보 | 메뉴의 종료 row 유지, 후보만으로 도착 확인/공개 없음, 공식 종료 callback 정리 |
| I/J 저장 복원·자동 시작 없음 | 기존 B2 SessionRestorationIntegrationTest + 이번 stop/start: 자동 Focus/발화 없음 |
| K 복귀 mode ambiguity | 교차 합성 route/return mode를 Screen에 제공, AMBIGUOUS→loading Trip, 정확한 step·도착 후보 없음; 실제 복귀 주행 아님 |
| L Surface destroy/늦은 paint | Surface 해제·GPS/Focus/음성 정리, replacement만으로 재활성화 없음 |
| M 종료 뒤 중복 callback | 늦은 grant/loss/GPS/HTTP/음성 완료 및 timer 반복 무시 |
| N 재연결 listener | stop/start 후 GPS subscriber 0, 재활성화 0; native Host/Binder listener 개수는 미측정 |
| O 음성 중 Screen stop | CLOSED, ended 1, Focus 회수, GPS 0, queued callback의 Trip/발화 증가 없음 |

GPS 0은 해당 fake subscription 경계의 현재 수다. HTTP 0도 취소 port의 pending 수이며 OS socket/Host native resource 전체 개수가 아니다. 각 테스트의 navigationStarted/Ended, Trip, speech, Focus fake lease를 대조했다. 모든 A~O의 실제 Host/Binder 조합 완료라고 주장하지 않는다.

## 8. RED→GREEN과 전체 회귀

첫 정식 RED 11개 중 7개가 실패했다. 일반 예약에 막힌 immediate, Surface 교체/stop/이전 epoch callback, Android queue 성공 뒤 callback 유실의 무한 Focus가 원인이었다. 수정 후 동일 11개가 통과했다. 정적 fallback handle 해제도 별도 테스트에서 실패 후 수정했다.

신규 테스트 작성 중 compile/API 확인 오류는 결함 재현으로 세지 않았다. 이후 두 Screen 테스트의 한글이 쉘에서 `?`로 변환돼 실패해 동일 문구를 Unicode escape로 바로잡았다. 복귀 ambiguity 테스트는 null navigationInfo를 기대했으나 기존 구현이 정확한 step 없는 공식 loading RoutingInfo를 반환하는 것이 확인돼 `isLoading && currentStep == null`로 올바른 안전 계약을 검증한다. 프로덕션 임계값/기존 테스트 기대값은 완화하지 않았다.

최종 `final-full.log`는 BUILD SUCCESSFUL이며 XML을 직접 집계했다. 기존 검사 삭제·skip·비활성화 없음.

| 구분 | 기존 | 신규 | 최종 고유 PASS |
|---|---:|---:|---:|
| 앱 JVM | 200 | 0 | 200 |
| PoC JVM | 257 | 24 | 281 |
| FastAPI | 102 | 0 | 102 |
| 앱 계측 | 16 | 0 | 16 |
| PoC 계측 | 1 | 2 | 3 |
| 합계 | 576 | 26 | **602** |

JVM/서버 583, native 19다. 실패·오류·skip 0. 신규 JVM은 HostSurfaceSchedulingTest 5개, AndroidVoiceWatchdogTest 10개, 실제 Screen 조합 9개다. native 신규 2개는 실제 Surface.release와 주입한 deadline의 controller 정리다. 앱 native 전체 16개를 2회, 수정 PoC native 전체 3개를 2회 통과했다. 반복 38회를 고유 검사 수에 중복 가산하지 않는다. FastAPI는 102 passed이며 기존 환경 경고 1건이다.

재실행 명령:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle/codex-user-home'
$env:ANDROID_USER_HOME = Join-Path $env:GRADLE_USER_HOME 'android-user-home'
.\gradlew.bat '-PnavHostPoc=true' ':app:testDebugUnitTest' ':nav-host-poc:testDebugUnitTest' ':app:assembleDebug' ':app:assembleDebugAndroidTest' ':nav-host-poc:assembleDebug' ':nav-host-poc:assembleDebugAndroidTest' '--offline' '--no-daemon' '--console=plain' '-Pkotlin.compiler.execution.strategy=in-process'
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
# adb에는 반드시 -s emulator-5554, instrument에는 --user 10을 지정한다.
```

독립 최종 읽기 전용 코드 리뷰는 Critical/Important 0건이었다. minor로 남은 것은 immediate 반복 폭주/stop을 별도 전용 테스트로 더 보강할 여지와 release 이벤트를 성공 증거로 오해할 위험이다. 후자는 본 문서에서 해제 시도와 실제 Focus dump를 명확히 구분했다. 리뷰 당시 pending이던 검사는 최종 XML에서 위 수로 확정했다.

## 9. 정차 AVD와 ACTIVE 게이트

기존 `emulator-5554`, `crp_avd`, 사용자 10을 명시적으로 사용했다. 다른 기기를 선택하거나 초기화하지 않았다. APK 2개와 test APK 2개를 빌드·설치하고 설치본 해시를 빌드 output과 비교했다. 메인 앱의 네이티브 UI/복원 계측과 PoC의 네이티브 Surface/저장/음성 계약 계측을 구분한다.

공개 합성 MOCK packet과 GPS (0,0)을 사용했으며 개발 인증정보/API 경로를 출력하지 않았다. 첫 수정 APK에서 준비 NavigationTemplate/Surface와 비공개 문구는 표시됐다. 공식 준비 메뉴 세 번의 요청을 기록했으나 관측 시점에 메뉴 항목이 보인 것은 1/3, 잔상 없는 안정적 메뉴 표시는 0/3이었다. 첫 관측은 약 1.53초, 나머지는 약 2.77/2.96초 시점의 screenshot이다. 이는 표시 완료 지연의 정밀 측정값이 아닌 요청→관측 시간이다. Host 뒤로가기 화살표로 준비 화면 복귀를 확인했다. Android keyevent BACK과 Host 화살표의 결과가 같다고 간주하지 않는다.

준비 메뉴에는 활성 안내 종료 row가 필요하지 않으므로 이 화면의 표시를 활성 종료 UX 합격으로 대체할 수 없다. 활성 메뉴 접근/정상 종료 시도는 0회, 성공률은 미산정이다. 단위 테스트에서 official 종료 callback이 성공했어도 native 종료 UX가 확보된 것은 아니다. **Host ACTIVE는 BLOCKED**이며 실제 Focus 획득/활성 TBT·TTS/자동 재탐색/도착·평가·복귀 E2E를 강행하지 않았다. ADB force-stop은 비활성 시험 환경 정리 수단으로만 사용했다.

추가 진단 시행 4에서는 cold 준비 화면의 CONTROL_REQUEST를 앱이 수신했고 Screen 정리 완료 이벤트까지 27.52ms였다. 잔상 없는 메뉴가 요청 후 약 2.60초와 8.18초 관측에 모두 표시됐다. 따라서 **총 네 번 중 메뉴 항목 관측 2/4, 첫 관측에 잔상 없는 메뉴 1/4**다. 서로 다른 관측 창/따뜻한 전환·cold 진입을 혼합했으므로 일반 성공률/백분위 지연으로 일반화하지 않는다. 준비 메뉴 복귀 뒤 Home→명시적 재진입 1회에서 준비 Surface를 다시 확인했다. 회전/창 크기 변경은 지원 설정을 임의 변경하지 않아 미실행이다.

첫 native 이벤트 구간의 앱 invalidate→onGetTemplate 관측 63쌍은 중앙값 0.79ms, 최대 41.32ms였다. 이는 해당 앱 callback 처리 지연이며 Host 메뉴/화면 표시 완료 지연이 아니다. 새 진단 시행에서 Focus request/NavigationManager started/음성 요청은 각각 0회였다. 화면 재생성 뒤에도 자동 안내가 시작되지 않았다는 제한적 관측이다. 실제 acquire→release 검증은 없다.

## 10. 후속 위험과 진입 조건

- A04 앱 예약 결함은 해결했으나 Host 메뉴·잔상/전환·native ETA 카드 제약은 잔존한다. 공식 종료 도달성과 실제 acquire→release가 확인돼야 ACTIVE 게이트를 열 수 있다.
- 이전 FocusEvent ANR 원인은 미확정이다. 이번에 재현되지 않았다는 사실은 해결 증거가 아니다. 다음 재현 시 실패 직후 own-process thread/exit trace와 main/Binder 시점을 확보해야 한다.
- Surface의 Java/native lifecycle, Host 재접속 listener, 실제 socket, AudioManager Focus 반환 및 audible TTS는 별도 실제 플랫폼 검증이 남는다.
- 동기 저장/Canvas/Binder의 main-thread 비용, 장시간 안정성은 실제 계측 없이 원인 또는 안전으로 확정하지 않는다.
- B1 안내 정확성과 B2 저장 계약은 기존 회귀로 유지한다. DESTINATION_PRIVACY는 PARTIAL이며 Host 전체 비공개 보증으로 올리지 않는다. A11의 물리적 흔적 보존/삭제 정책도 이번 범위 밖이다.
- Phase 6B-4의 독립적 코드·계약 개선은 진행 가능하다. 실제 활성 시연/차량·배포 판정은 A04/ANR/플랫폼 위험이 닫힐 때까지 별도 차단한다.

## 11. 최종 판정과 증거

| 항목 | 판정 | 제한 |
|---|---|---|
| Phase 6B-3 | READY_WITH_LIMITATIONS | 앱 결함/계약 회귀 완료, 플랫폼 잔여 위험 |
| HOST_CONTROL | PARTIAL | 준비 메뉴 반복 불안정, 활성 종료 UX 미확인 |
| Host ACTIVE | BLOCKED | 안전한 공식 종료 경로의 native 증거 부족 |
| FOCUS_LIFECYCLE | PARTIAL | Screen fake 경합 통과, 실제 활성 acquire→release 미실행 |
| SURFACE_STABILITY | PARTIAL | handle/epoch 계약 통과, AndroidX pause snapshot 오류 잔존 |
| VOICE_WATCHDOG | VERIFIED_CONTRACT | timer/lease/controller, 실제 엔진·청취 미확인 |
| DESTINATION_PRIVACY | PARTIAL | 비공개 준비 문구/Trip 계약 유지, 플랫폼 전체 보증 아님 |

602개 고유 검사 통과와 비활성 상태의 Focus 비어 있음은 실차 안전성 인증이 아니다. 다음 단계는 A04 공식 종료·AndroidX snapshot 경계의 재현 자료를 유지하면서 B4의 독립 작업을 진행하는 것이다. 활성 시연과 차량·상용 배포는 차단을 유지한다.

공개 증거는 [evidence/b3](evidence/b3/README.md)의 검사 요약, APK 해시, 재현/오류 집계, 유한 이벤트 및 합성 시험 화면으로 한정한다. 원 사용자 packet·GPS·인증정보·전체 logcat/bugreport는 포함하지 않는다.

## 12. 종료 상태와 자료 보존

종료 branch/HEAD는 시작과 같은 `main / 2dcd2adbb30f4a5a72febdb47ed277292d2a7280`이다. 시작 추적 696개 중 범위 밖 687개는 바이트가 같고, 의도한 기존 파일 9개만 수정했다. 신규 코드·테스트·문서·공개 증거 24개 파일을 미커밋으로 남겼다. `git diff --check` 오류 없음. 커밋·Push·Git 이력 변경 없음.

원 private 파일 5개와 sidecar/시험 평가 파일의 기존 부재 상태 3건, 총 8건을 비교·복원했고 원 GPS를 복원했다. 이번에 만든 native fixture 폴더만 확인 후 제거했다. 복원 후 이번 임시 packet/토큰/GPS 백업도 삭제했다. 마지막 Navigation Focus는 비어 있다. 이 확인은 시험 정리 뒤의 상태이며 정상 활성 종료 UX의 증거가 아니다. APK는 검증한 수정 빌드를 유지한다.

실제 Host 활성 종료, AudioManager/SDK의 실제 청취·자원 반환, 장시간/실차는 검사하지 못했다. 특히 caught AndroidX snapshot 오류와 이전 원인 미확정 ANR 때문에 실제 활성 시연/차량·상용 배포는 여전히 별도 차단된다.
