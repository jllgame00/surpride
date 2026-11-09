# Phase 3B-2.5 Pleos Navigation Host 통합 게이트

2026-11-09. **판정: CONDITIONAL.** 코드 기반은 준비됐지만 목적지 없는 활성 Trip의 Pleos 정책·권한·실제 수용 조건은 미확인이다. GO_FOR_CONTROLLED_TEST로 판정하지 않는다. 기존 DESTINATION_PRIVACY는 자체 UI 범위의 PARTIAL이며, 시스템 전체는 UNVERIFIED다.

시작: `main / 279c44407f865b2ba038d37d8d10282f02f215d4 / clean`. 기존 앱·SDK·GPS·FastAPI·DEMO 및 검증 자료를 보존했다. 커밋/Push하지 않는다.

종료도 같은 브랜치/HEAD이며 수정1개·신규8개(문서/증거 포함)다. diff/신규 텍스트 공백 및 증거 링크 검사를 통과했다. 실제 인증 바이트는 Git-visible 파일과 설치 APK에서 발견되지 않았다.

## 1. 공식 지원 계약과 필수 선언

현재 `app/app-automotive/app-testing:1.7.0`, target34/compile37, 기존 AGP/Gradle/JDK 구성을 유지한다. 새 의존성·권한은 없다. 1.7.0 실제 bytecode/실행 감사는 [이전 계약 증거](ownership-evidence/contract-audit.txt)를 보존하고 관련 테스트를 다시 실행했다.

- [Trip.Builder](https://developer.android.com/reference/androidx/car/app/navigation/model/Trip.Builder)는 Destination 목록 전체의 생략을 허용한다. Step을 넣으면 대응 TravelEstimate가 필요하다. 이름·주소가 모두 비어 있는 Destination 객체는 실제1.7.0에서 실패하므로 만들지 않는다.
- [NavigationManager](https://developer.android.com/reference/androidx/car/app/navigation/NavigationManager)는 callback 등록→사용자 요청에 따른 navigationStarted→updateTrip, 종료/도착 시 navigationEnded 계약을 제공한다. host stop 이후 update를 멈춰야 한다. **활성화 전 updateTrip 호출로 실제 host의 수용 여부를 시험할 수 있는 계약이 아니다.** 로컬 단계에서 거부되는 호출을 host 검증으로 세지 않는다.
- [AAOS 템플릿 앱 가이드](https://developer.android.com/training/cars/apps/automotive-os)는 CarAppService/CarAppActivity, automotive/template-host feature, APP_MAPS와 NAVIGATE/geo intent 등의 선언을 설명한다. [Navigation 앱 가이드](https://developer.android.com/training/cars/apps/navigation)의 NAVIGATION category/템플릿·Surface 권한 및 intent 처리 요구도 별개다.
- [Pleos Maps/Navigation 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide)은 APP_MAPS와 마켓 분류 일치, 단일 활성 경로·명시적 전환, 주행 조작 제한·필수 운전 정보, 최소 권한을 요구한다. 위치 권한은 필요하며 불필요한 microphone/특권 권한을 추가하지 않는다. 위치 보관·HTTPS·개인정보 정책도 정식 서비스 요건이다.
- [CarAppFocusManager](https://developer.android.com/reference/android/car/CarAppFocusManager)는 포커스 요청/상실/반납을 제공한다. 실제 Pleos의 허용 범위/필요 승인과 SecurityException 조건은 미검증이다. 문서에 없는 permission 이름을 추정하지 않았다.

현재 PoC에는 기존 APP_MAPS, AndroidX NAVIGATION, NAVIGATION_TEMPLATES/ACCESS_SURFACE, foreground COARSE/FINE, CarAppActivity 선언이 있다. **정식 navigation intent 필터/입력 처리와 전체 배포 품질 요건은 아직 완성되지 않았다.** 이번에는 intent 수신으로 자동 안내될 여지를 만들지 않고 Manifest를 유지했다. Android/Google의 일반 가이드를 Pleos 마켓 개별 승인으로 대체하지 않는다.

## 2. 정적·테스트 호스트·실제 Pleos 결과

| 항목 | 정적/로컬 라이브러리 | 공식 TestNavigationManager | 실제 Pleos |
|---|---|---|---|
| Destination 없는 Trip | VALID | 정상 세션에서 예외 없이 update | 미전송, UNVERIFIED |
| Step/Maneuver | 기본 회전 유효, type별 필수 정보 없으면 거부 | next Step 전달 검증 | 활성 Trip 표시 미검증 |
| TravelEstimate | next Step용 distance/time/arrival 유효 | Step ETA 전달 검증 | 최종 ETA 처리 미검증 |
| 직렬화 | 실제 Bundleable/Parcel 왕복 통과 | 기록된 객체/기본 SDK 검증 확인 | 실제 IPC 수용 미검증 |
| 중지/종료 | 상태·정리·오류 회귀 | 공개 callback에 stop 주입, ended/clear 확인 | 실제 host stop 발생시키지 않음 |
| UI/호스트 연결 | 로컬 감사 데이터 | 실제 차량 호스트를 대체하지 않음 | API4 및 CLOSED 감사 화면 확인 |

테스트 도구의 tripsSent/count는 base 검증 전에 **시도**를 기록한다. 예외 유무와 상태를 함께 검사하며 실제 전달 성공으로 해석하지 않는다. RestrictTo인 manager의 내부 stop/Binder 메서드는 호출하지 않는다.

### A~G 비공개 데이터 계약

**A/B.** 기존 PrivacyTripFactory의 유효 cue/도로명/Maneuver와 next-step estimate를 재사용한다. 잘못된 거리/시간/type이나 identity가 섞인 안전 문구는 전체 payload를 거부한다. 안전 안내를 지우거나 허위 목적지 이름/좌표로 숨기지 않는다.

**C.** hidden payload는 `currentRoad + Step(cue/road/maneuver) + Step TravelEstimate(distance/remainingSeconds/arrival)`다. Destination와 destination estimate는0이며 이미지/URI를 넣지 않는다. 이1.7.0 다섯 모델에는 직접 최종 좌표 필드가 없고 factory도 좌표를 받지 않는다. 문자열·이미지·주변 도로 형태로부터의 추론까지 차단한다는 의미는 아니다.

**D.** 실제 host의 destinationless Trip 표시·필수 필드·유지/거부/오류 동작은 미확인이다. 이번 기기 화면은 **ListTemplate에 로컬 개수와 gate 상태를 표시한 것**이며 활성 Trip 렌더링 증거가 아니다. 기존 root의 정적 MOCK NavigationTemplate도 실제 Trip 전송과 다르다.

**E.** hidden Trip에는 최종 destination estimate가 없다. next Step ETA를 최종 ETA로 표시하지 않는다. 별도 NavigationTemplate/Surface에서 수치를 표현하는 것과 최종 ETA를 host/cluster에 제공하는 것은 다른 계약이다. 목적지 없이 최종 ETA가 필수로 전달되어야 하는지, 허용된 대체 표현은 무엇인지 질의가 필요하다.

**F.** 문서상 host가 Trip 정보를 cluster/HUD 등에 사용할 수 있다. 실제 Pleos에서 무엇을 표시·저장·발화하는지는 미확인이다. 미지원/미표시를 PASS로 판단하지 않는다.

**G.** 새 Adapter의 revealDestination은 명시적 공개 요청이 없으면 상태/Trip을 바꾸지 않는다. 요청이 있으면 기존 실제 identity와 목적지 estimate를 factory로 구성해 update하는 테스트를 했다. 경로 엔진의 목적지를 새로 설정하는 기능이 아니다. 실제 host 전환은 미검증이며, 다시 숨겨도 이전 공개 기록을 지운다고 보장하지 않는다.

## 3. 준비한 Adapter와 실행 차단

`NavigationOwnershipAdapter`/`AndroidXNavigationOwnershipAdapter`는 기존 OwnershipController를 사용한다. 실제 시스템 연결은 다음 조건으로 제한한다.

1. 기본 HostIntegrationGate는 정책·권한·에뮬레이터 확인이 없는 **UNVERIFIED/CLOSED**다. caller가 userRequested=true를 전달해도 열린 gate가 없으면 managerProvider를 조회하거나 focus를 요청하지 않는다.
2. 열린 gate에서도 일회성 ControlledTestApproval과 유효한 ActivationChecks가 필요하다. 한 번 시도한 승인은 재사용할 수 없으며 재시작/재연결로 승인되지 않는다. 이 객체/flag는 호출자가 확인한 사실을 표현하며 실제 정책 승인/사용자 동의를 자동 인증하는 시스템은 아니다.
3. NavigationManager 조회와 callback 등록은 승인된 request 이후에만 한다. 조회 중 stop/reconnect가 발생하면 해당 결과를 캐시하거나 오래된 callback을 등록하지 않는다.
4. focus grant 후 navigationStarted, ACTIVE에서만 factory를 통한 update가 가능하다. public API로 임의 raw Trip을 전송하는 경로는 제공하지 않는다. 중복 활성화를 막고 OEM active/unknown이면 기존 모델이 차단한다.
5. host stop/포커스 상실/앱 background/환경 악화/release는 정리를 수행한다. stopGuidance hook은 한 세션의 중단 성공 후 중복 실행하지 않으며, 실패하면 SDK ended/clear도 시도한 뒤 ERROR·재연결 요구를 유지한다.
6. 명령은 main thread에서 처리한다. callback executor는 main thread로 전달하며 이전 callback은 컨트롤러 세대 검사로 새 세션에 영향을 주지 않는다. reconnect는 정리 성공 후 manager 참조를 버리고 다음 명시 요청에 새로 조회한다.

이번 앱의 감사 화면은 **실제 Adapter를 생성/연결하지 않고 기본 gate만 읽는다.** 실제 활성화 버튼, 승인 intent/디버그 우회, 저장된 자동 승인 기능은 없다. 테스트에서만 확인된 gate와 합성 focus를 주입한다.

실제 CarAppFocusManager 구현은 아직 기존 FocusPort 뒤에 연결하지 않았다. 또한 stopGuidance를 기존 GPS/TBT/렌더링 작업 전체에 연결하는 실제 세션 통합은 후속 작업이다. 현재 테스트의 hook 호출을 실제 GPS/음성 중단 검증으로 표현하지 않는다.

## 4. 실제 호스트에서 확인된 내용과 미확인 내용

Pleos Connect v2.1.0/API34, emulator-5554/사용자10에서 APK 설치 Success, CarAppActivity COLD373ms/Wait380ms, host API4를 확인했다. [비활성 감사 화면](host-gate-evidence/inactive-gate.png)에 다음 값이 표시됐다.

- `Adapter gate CLOSED`, `POLICY_UNVERIFIED`, `소유권 INACTIVE`.
- 로컬 Destination0/Step1/Step ETA1, 최종 ETA가 아님.
- 시스템 Trip/cluster/HUD/알림/음성/이력 UNVERIFIED.
- 로그: `adapterBound=false, actualActivation=false, carApi=4`.

AppFocusService는 전후 `mActiveAppTypes:{}`였으며 기존 main NaviHelper 연결/OEM 활성 경로 false를 확인했다. 새 Adapter의 실제 request/navigationStarted/updateTrip은0건이다. 실제 음성, 실도로, 새 API 경로 요청, geo fix, OEM route 취소/변경을 실행하지 않았다.

따라서 **문서에 없는 Pleos 필수 Trip 필드, destinationless 활성화 허용, 최종 ETA, host의 기존 목적지 잔류/기록, 실제 포커스 획득 권한은 모두 미확인**이다. Car App API4 연결이나 APK 설치만으로 해결됐다고 판단하지 않는다.

## 5. 목적지 비공개 채널 상태

| 채널 | 현재 상태 | 근거/남은 조건 |
|---|---|---|
| 자체 NavigationTemplate | 제한적 PASS | 기존3B-1 비공개 projection/화면 및 이번 회귀. 활성 Trip 연계 미검증 |
| Surface | 제한적 PASS | 기존 국소 경로/끝점 제외 회귀. 실제 전체 지도/추론 방지 아님 |
| 실제 시스템 Trip | UNVERIFIED | 이번 실제 updateTrip0건 |
| 계기판/HUD | UNVERIFIED | 지원 표면 및 실제 전달/표시 미검증 |
| 알림 | UNVERIFIED | 실제 활성 안내 알림 미발생 |
| 음성 | UNVERIFIED | 실행 금지, 파생 host 발화도 미검증 |
| 최근 목적지/기록 | UNVERIFIED | 등록/보관/삭제 계약 미확인, 보호된 OEM 기록에 접근하지 않음 |

새 LEAK를 관찰하지 않았다는 사실을 전체 비공개 CONFIRMED로 해석하지 않는다. 이전 NaviHelper/OEM 노출 결과도 보존한다.

## 6. 안전한 활성 경로 실험 절차 — 현재 실행 불가

아래는 조건 충족 후 **별도 사용자 승인으로만 실행할 절차**다. 현재 gate를 열라는 실행 지시가 아니다.

1. 사무국/공식 문서에서 이 이미지·앱 분류의 독립 활성 안내 실험 허용, 필요한 일반 권한·등록 절차, 목적지 없는 Trip 취급 조건을 확인한다. 실제 FocusPort 구현과 stopGuidance 전체 연결도 먼저 검증한다.
2. API34 Pleos 개발 AVD만 연결하고 사용자10/모델·정차/P/0kmh/공식 UX 제한 해제를 확인한다. 실제 차, 상태 불명, 권한 오류이면 중지한다. root/강제 grant/보호 설정 변경은 사용하지 않는다.
3. 기존 MainActivity의 연결된 NaviHelper 진단에서 **새로 조회한** routeActive=false 및 routeBusy=false를 확인하고, AppFocusService의 활성 상태도 읽기 전용으로 확인한다. 오래된/누락/unknown 데이터면 차단한다. OEM route가 있으면 사용자에게 알리고 OEM의 정상 취소 UI로 종료한 뒤 재확인한다. 자동으로 타 앱 경로를 취소하거나 소유권을 빼앗지 않는다.
4. 별도 정차 실험 UI에서 목적/데이터/중지 방법을 보여주고 다음 동작을 정의한다: **“정차 에뮬레이터에서 목적지 없는 Trip 활성화 실험을 1회 승인”**. 취소와 승인 버튼을 구별하고, 도로명/회전/거리/시간이 host에 전달될 수 있음을 고지한다. 이 버튼은 현재 앱에 구현하지 않았다.
5. main thread에서 callback 등록→해당 요청의 focus 획득 승인→navigationStarted→검증된 hidden Trip update 순으로 수행한다. 실제 의미가 확인된 데이터만 쓰고 목적지 placeholder/허위 좌표로 거부를 회피하지 않는다. 음성·실도로 안내는 시작하지 않는다. 실패하면 즉시 정리한다.
6. 자기 Template/Surface와 허용된 host·알림·cluster/HUD 공개 UI를 조사한다. 지원하지 않는 표면은 UNVERIFIED로 남기며 보호된 앱 파일/설정을 읽거나 수정하지 않는다. 음성 검증은 별도 허용 없이는 계속 미실행이다.
7. 즉시 공개 실험은 별도 **“현재 목적지 이름·주소·ETA를 host에 공개”** 선택 후 같은 내부 목적지 metadata만 전환한다. 공개 후 이력 삭제를 보장하지 않으며 새 route 전환과 혼동하지 않는다.
8. 사용자 종료/host stop/UX 변경/포커스 상실/background/오류에서 GPS/TBT/시각 안내 작업을 먼저 멈추고 navigationEnded→요청별 focus 반납→callback 해제를 확인한다. 정리 실패/강제 종료 뒤 상태 불명은 차단 상태를 유지한다. 재연결·재실행은 새 승인과 preflight가 필요하다.

필수 운전 정보는 실제 회전·거리·도로·차선/규제 등 해당 경로에 필요한 안전 정보다. 이는 전체 요구를 충족했다는 판정이 아니다. 목적지 비공개를 위해 안전 문구를 삭제하지 않고, 충돌 시 요청 중단 또는 명시적 공개를 선택하도록 설계한다. 마켓 제출 전 navigation intent/필수 기능·다중 화면·장기 안정성·지도 표시/데이터 이용 조건도 별도로 충족해야 한다.

## 7. 사무국 문의 문안 — 작성만 했으며 전송하지 않음

**제목: [SURPRIDE] Pleos Connect v2.1.0 목적지 없는 Trip 및 독립 안내 실험 지원 확인 요청**

안녕하세요. SURPRIDE는 목적지 정보를 출발 전 비공개로 유지하고 도착 후 공개하는 차량용 경험을 검토하고 있습니다. 현재 Pleos API34 개발 에뮬레이터에서 AndroidX Car App Library1.7.0/Car App API4의 비활성 템플릿을 확인했으며, 실제 경로 소유권·음성 안내는 활성화하지 않았습니다. 다음 사항에 대한 공식 지원 범위와 허용 절차를 문의드립니다.

1. Pleos의 공식 Templates Host에서 CarAppService/NAVIGATION + CarAppActivity/APP_MAPS 기반 독립 내비게이션을 지원하나요? v2.1.0의 검증된 라이브러리/API 수준 및 앱 등록·심사 조건은 무엇인가요?
2. `Trip.destinations=[]`, `destinationTravelEstimates=[]`이고 Step/Maneuver/Step TravelEstimate는 유효한 활성 Trip을 수용하나요? 필수 목적지 metadata, 거부/오류 코드 및 이전 목적지 label 잔류 동작을 확인할 문서/예제가 있나요?
3. 목적지 없이 최종 ETA를 제공하는 공식 권장 계약이 있나요? Template/Surface 수치 표시와 cluster/HUD 전달의 요건은 각각 무엇인가요? 최종 이름·주소·핀을 숨기는 UX가 필수 안전 정보 기준과 충돌하나요?
4. CarAppFocusManager와 NavigationManager를 함께 사용할 때 필요한 일반 권한, 프로젝트/패키지 등록, 포커스 획득·상실·반납 순서가 있나요? 별도 system/signature 권한이 필요하다면 일반 공모전 앱이 공식적으로 접근 가능한가요?
5. Trip/Template/Surface 정보 중 cluster/HUD/알림/음성/홈/최근 목적지에 전달·보관되는 항목은 무엇인가요? 목적지 생략 시 저장/삭제/공개 전환을 제어하는 공식 API가 있나요?
6. OEM route가 없는 정차 AVD에서 명시적 1회 승인 후 음성 없이 활성화하는 실험을 허용하나요? 기존 비활성 MOCK TBT/국소 Surface 개발 실험의 정책 분류 및 사용 가능한 공식 검증 도구도 확인 부탁드립니다.
7. 실험 승인과 앱마켓 최종 심사에 각각 필요한 UX·개인정보/위치정보·지도 데이터 사용 자료는 무엇인가요? 실제로 지원되지 않는 부분이 있으면 허용되는 대안을 안내 부탁드립니다.

회신 전 실제 활성화는 보류하겠습니다. 보호된 OEM 접근, 강제 권한, 이중 안내 및 실제 차량 주행은 수행하지 않습니다. 감사합니다.

API 키·개인 위치·인증정보는 이 문의 문안에 포함하지 않았다. 발송 채널/대상은 사용자가 사무국의 공식 안내에 따라 확인해야 한다.

## 8. 테스트·변경 파일·Phase 3B-3 진입 조건

**앱89 + PoC60 + FastAPI33 =182개 통과**, 기존170개와 신규 Adapter12개다. main assemble/test를 -RerunTasks로, PoC는 표준 -Test로 실행했다. SDK 조회 중 stop의 callback 잔류는 **60개 중1실패**를 확인한 뒤 세대 검사를 추가하여 수정했다. 최종 독립 검토에서 남은 Important/Critical 지적은 없었다.

현재 기기 PoC PID5081의 crash buffer FATAL은0건, 패키지 exit-info의 ANR은0건이었다. 정상 종료/AUDIT_DESTROYED, 전후 활성 포커스 없음, 종료 후 Vehicle RESPONDING/NaviHelper CONNECTED/OEM 활성 경로 false를 확인했다. 과거 개발 crash와 장기 안정성 보장은 별개다.

신규 검증은 기본 gate·승인 없음/재사용·환경·중복, 승인 전 SDK 조회0, focus grant 전 시작0, hidden/reveal, host callback/포커스 상실/background/환경 변경/release, 중단 hook 실패의 SDK 정리, lookup 실패/조회 중 stop, 재연결·오래된 callback·main thread를 포함한다. 기존 Trip Parcel 및 라이브러리 계약 테스트도 회귀했다. TestNavigationManager와 합성 FocusPort 결과를 실제 CarAppFocusManager 실험으로 세지 않는다.

변경: `OwnershipAuditScreen.kt`에 CLOSED gate 표시. 추가: `ownership/NavigationOwnershipAdapter.kt`, `NavigationOwnershipAdapterTest.kt`, 이 보고서·계획·`host-gate-evidence/`. 기존 Controller/Factory/GPS/MVP/SDK/Manifest/의존성·빌드 스크립트는 변경하지 않았다.

명령·hash·런타임 증거: [테스트 집계](host-gate-evidence/test-summary.txt), [런타임/상태](host-gate-evidence/runtime-health.txt), [생명주기](host-gate-evidence/lifecycle.txt). 기존 JDK native-access/Car App deprecated API/FastAPI 경고는 유지하며 숨기려고 버전을 바꾸지 않았다.

```powershell
.\scripts\build-codex.ps1 -Test
.\scripts\build-nav-host-poc.ps1 -Test
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
# 설치 후 기존 ‘소유권·비공개 계약 감사’ 화면에서 CLOSED 상태만 확인한다.
```

**Phase 3B-3 진입:** 공식 실험 허용/권한·등록 조건 확인, 실제 request별 FocusPort 및 전체 안내 중단 연결, 신뢰할 수 있는 정차/OEM 상태 공급, 별도 일회성 사용자 승인 절차가 모두 필요하다. 실제 host의 목적지 없는 Trip 수용과 채널별 노출은 그 통제 실험에서 판정한다. 현재는 그 전제 조건이 충족되지 않아 CONDITIONAL을 유지한다.
