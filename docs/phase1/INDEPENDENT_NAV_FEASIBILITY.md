# Phase 1C-2 — Independent Navigation Host Feasibility

**최종 판정: CONDITIONAL.** Pleos Connect v2.1.0/API 34의 기존 시스템 호스트에서 공식 `CarAppService` → `CarAppActivity` → `NavigationTemplate` 실행 경로를 실제 확인했다. 목적지 이름을 제공하지 않는 정적 모의 UI와 회전/남은 시간 영역도 표시됐다. 그러나 실제 지도·경로 안내 엔진, 활성 경로 소유권, 전체 채널의 목적지 비공개, Pleos 심사 승인은 확인하지 않았다.

기준일: 2026-11-09(KST). 캡처의 에뮬레이터 시각은 호스트 시각과 다르다. **실도로 안내나 완성된 독립 내비게이션의 검증 결과가 아니다.**

## 1. 네 가지 독립 판정

| 항목 | 판정 | 확인한 근거와 한계 |
| --- | --- | --- |
| A. NavigationTemplate 호스트 실제 실행 | **확인 / GO** | 설치된 시스템 호스트로 연결, Car App API 4 협상, 실제 템플릿·Surface 표시. ETA 카드 하단 일부 잘림 있음 |
| B. 목적지 숨김 UI 표현 | **정적 UI 범위 확인 / CONDITIONAL** | 목적지 이름·주소·좌표·Destination 객체 없이 모의 회전/거리/시간 표시. 실제 목적지를 가진 Trip/지도/클러스터/음성의 비공개는 미검증 |
| C. 실제 독립 경로 안내 | **조건부 / 미검증** | 화면·Surface 계약만 확보. 경로/지도 공급자, GPS, TBT·재탐색·ETA 엔진, 소유권 통합이 별도 필요 |
| D. Pleos 앱마켓 승인 | **미검증 / CONDITIONAL** | 공식 카테고리/안전 기준은 확인했지만, 이 mock 앱은 제출하지 않았으며 목적지 숨김 UX 승인 근거 없음 |

A의 성공을 C·D의 성공으로 올려 판단하지 않는다. 현재 결과는 후속 **정차 환경의 엔진/정책 조사**를 진행할 근거이며 전체 내비게이션 개발 착수를 무조건 승인하는 GO가 아니다.

## 2. Git·빌드 환경 보존 및 PoC 격리

- 시작: `main`, HEAD `f414f30467487d0f7decd7759fca60459ec2b78b`, clean.
- 종료: 동일 브랜치/HEAD. 이번 수정·생성만 미커밋 상태이며 스테이징·커밋·Push·원격 변경 없음.
- 기존 `app` 소스/Manifest/권한/런처, Vehicle 2.3.2, NaviHelper 3.0.5, Phase 0B 빌드 스크립트와 이전 검증 문서를 수정하지 않았다.
- 별도 `nav-host-poc` 모듈, 패키지 **`com.surpride.navhostpoc`**, 표시명 **SURPRIDE Nav MOCK PoC**를 사용한다. 기존 앱과 별도로 설치된다.
- `settings.gradle.kts`에 `-PnavHostPoc=true`일 때만 모듈을 포함하는 조건을 추가했다. 기존 표준 빌드에는 기본적으로 포함되지 않는다.
- 기존 AGP/Gradle/Kotlin/JDK/앱 SDK 버전은 유지했다. 새 PoC만 compile SDK 37, min SDK 29, target SDK 34이며 Car App Library **안정 버전 1.7.0**을 사용한다. [공식 배포 버전/의존성](https://developer.android.com/jetpack/androidx/releases/car-app)
- SDK 바이너리/빌드 결과는 기존 `.gitignore`의 `.gradle/`, `**/build/`, `*.apk`로 제외된다. 전역 환경 설정은 변경하지 않았다.

## 3. 공식 계약 및 Pleos와의 관계

Android for Cars App Library는 템플릿과 호스트 통신을 제공한다. AAOS에서 템플릿 앱은 별도 호스트와 `app-automotive`의 `CarAppActivity`가 필요하며, 호스트가 지도 데이터나 경로 엔진을 자동으로 제공하는 것은 아니다. [공식 개요](https://developer.android.com/training/cars/apps/library), [AAOS 구성](https://developer.android.com/training/cars/apps/automotive-os)

| 구성 | PoC 선언/역할 | 실제 확인 |
| --- | --- | --- |
| CarAppService | `androidx.car.app.CarAppService` action + `androidx.car.app.category.NAVIGATION` | Package Manager가 `MockNavigationService` 1개 인식, Session 생성 |
| CarAppActivity | 공식 라이브러리 Activity, singleTask, DeviceDefault.NoActionBar | 실제 COLD 실행 및 호스트 통신 |
| AAOS 메타데이터 | `com.android.automotive` → `<uses name="template"/>`, minCarApiLevel 1 | API 4로 협상 |
| Activity APP_MAPS | Pleos/AAOS 런처·Navigation 분류 | PoC Activity에만 선언. 기존 SURPRIDE에는 추가하지 않음 |
| Service NAVIGATION | Car App Library의 NavigationTemplate 사용 카테고리 | APP_MAPS와 서로 대체되지 않는 별도 선언 |
| `androidx.car.app.NAVIGATION_TEMPLATES` | NavigationTemplate 접근 | 설치 패키지에서 `granted=true`, 실제 렌더링 |
| `androidx.car.app.ACCESS_SURFACE` | AppManager/SurfaceCallback으로 그리기 | `granted=true`, Canvas의 MOCK 문구 표시 |
| templates_host feature | 템플릿 호스트 필요성 표시 | 이미지가 feature 제공, required=true 설치 성공 |
| distractionOptimized | 공식 CarAppActivity 템플릿 구성 메타데이터 | 선언만 함. Pleos 주행 승인/실제 주행 검증을 의미하지 않음 |

카테고리·템플릿 권한은 [Navigation 공식 가이드](https://developer.android.com/training/cars/apps/navigation), Surface 권한/콜백은 [지도 그리기 계약](https://developer.android.com/training/cars/apps/library/draw-maps)을 따랐다. 실제 지도 대신 빈 배경과 `MOCK ONLY / NO GPS / NO ROUTE`만 그렸다. 지도/위치 데이터를 얻는 별도 권한으로 혼동하지 않는다.

호스트 검증은 `HostValidator.Builder(this).build()`를 그대로 사용한다. `ALLOW_ALL_HOSTS_VALIDATOR`, 임의 인증서 허용, 권한 우회는 없다. AAOS API 31+에서는 TEMPLATE_RENDERER 권한을 가진 호스트를 신뢰하는 [공식 검증 계약](https://developer.android.com/reference/androidx/car/app/validation/HostValidator)에 해당하며, 설치된 호스트의 해당 권한 부여를 확인했다.

PoC는 정적 UI 실험이다. 실제 Navigation 앱에 필요한 위치 권한, geo NAVIGATE intent 처리와 `Session.onNewIntent` 목적지 처리, 유효 지도·경로, 배포 요건을 모두 충족한 앱이라고 주장하지 않는다. 마켓 등록 대상이 아니며 실제 위치/목적지를 받거나 안내하지 않는다.

## 4. Pleos 호스트 발견 및 실제 실행 증거

대상: `emulator-5554`, foreground 사용자 **10**, Pleos Connect v2.1.0/API 34. 화면상 P/0 km/h 상태에서만 관찰했다. 차량 제어/위치 이동/실제 주행은 하지 않았다.

기존 시스템 구성:

- feature: `android.software.car.templates_host`.
- 패키지: `com.android.car.templates.host`, versionName `1.0`, versionCode `1`, 사용자 10에 설치/활성 상태.
- 실제 Renderer action: **`android.car.template.host.RendererService`**.
- 서비스: `com.android.car.templates.host/com.android.car.libraries.templates.host.RendererService`.
- `android.car.permission.TEMPLATE_RENDERER: granted=true`.

초기 추정 action으로 조회했을 때는 서비스가 없었으나, 패키지의 실제 공개 intent filter를 확인해 올바른 action으로 조회했다. 호스트가 없다는 결론을 내리지 않았다. 시스템 패키지를 추가 설치하거나 수정하지 않았다. [호스트 증거](independent-nav-evidence/host-discovery.txt)

| 검증 | 실제 결과 | 증거 |
| --- | --- | --- |
| OEM 기존 상태 확인 | 기존 SURPRIDE NaviHelper CONNECTED, SAFE_GUIDING/활성 false/탐색 false | [시작 상태](independent-nav-evidence/baseline-navigation.txt) |
| PoC APK 설치 | `Success`, 별도 패키지 설치 | [최종 설치/시작](independent-nav-evidence/final-install-start.txt) |
| CarAppService 인식 | NAVIGATION 카테고리 서비스 1개 | [서비스 조회](independent-nav-evidence/install-start.txt) |
| Activity 시작 | 최종 빌드 COLD / Status ok / 377ms | 최종 설치/시작 기록 |
| 호스트 연결 | `HOST_CONNECTED carApi=4` | [수명주기 로그](independent-nav-evidence/lifecycle-events.txt) |
| NavigationTemplate | 모의 우회전, 300m, 남은 10분/2km와 도착 시각 표시 | [실제 화면](independent-nav-evidence/navigation-template.png) |
| Surface | STATIC_SURFACE_DRAWN, 빈 배경과 MOCK 문구 | 동일 화면/로그 |
| 목적지 없는 UI | 목적지 이름·주소·핀 없이 모의 안내 표시 | 실제 목적지를 생성/전달하지 않은 정적 표현 검증 |
| 화면 전환 | 설명 버튼 → MessageTemplate, 호스트 뒤로 화살표 → NavigationTemplate | [설명 화면](independent-nav-evidence/explanation-screen.png), EXPLANATION_TEMPLATE 이후 재렌더 로그 |
| 종료 | 설명 화면의 PoC 종료 → finishCarApp → SCREEN_DESTROYED → 기존 MainActivity 복귀 | [종료 후 상태](independent-nav-evidence/after-exit-regression.txt), [종료 후 화면](independent-nav-evidence/after-exit.png) |
| Crash/ANR | 사용 가능한 버퍼에서 PoC crash 0/ANR 0, 호스트 crash 0 | [로그 상태](independent-nav-evidence/runtime-health.txt). 장시간 안정성 보장 아님 |

**표시 제약:** 이 호스트/창 크기에서 TravelEstimate 카드 하단 일부가 화면 경계에 걸렸다. 남은 10분·2km는 보였으나 최종 UI의 전체 카드/안전 영역/다양한 화면 크기 적합성을 통과했다고 볼 수 없다. Action strip은 시간 경과 후 숨겨질 수 있다. `uiautomator`는 템플릿 내부 텍스트를 계층으로 노출하지 않아 표시 판정은 캡처와 앱 콜백/호스트 로그를 함께 사용했다.

호스트 화면 전환 중 즉시 캡처에는 애니메이션의 검은 프레임이 포함될 수 있어 이를 종료 성공으로 집계하지 않았다. 실제 finish 요청 로그, Screen 해제, 복귀 Activity를 확인했다.

## 5. 단일 활성 경로·목적지 비공개·심사 제약

[Pleos Navigation 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide)은 APP_MAPS 카테고리와 마켓 카테고리 일치, 단일 활성 경로, 명시적 전환, 핵심 주행 정보 표시, 최소 권한 및 주행 중 조작 제한을 요구한다. 실제 내비 앱에서는 `CarAppFocusManager`로 경로 소유/포커스를 관리해야 한다. [공식 포커스 API](https://developer.android.com/reference/android/car/CarAppFocusManager)

이번 PoC는 `NavigationManager.navigationStarted`, `updateTrip`, `CarAppFocusManager.requestAppFocus`를 호출하지 않는다. OEM 내비에 새 경로를 요청·취소하지 않으며 음성/알림/클러스터 안내도 발행하지 않는다. 화면의 TBT는 MOCK임이 명시된 정적 템플릿 요소이고 실제 경로 안내가 아니다. 기존 OEM 경로가 없음을 확인한 후 시험했고 종료 후에도 idle였다. **동시 안내나 포커스 전환의 정확성을 테스트한 것은 아니다.**

실제 구현에서는 `NavigationManager`의 시작/종료/Trip/중단 콜백과 AAOS 소유권의 관계를 검증해야 한다. 템플릿 렌더링 성공이 Pleos가 요구하는 직접 포커스 관리까지 대신 보장하는지는 확인되지 않았다. 클러스터/통지에 제공하는 Trip 데이터의 Destination, 음성, 지도 도착 핀·최종 경로, 검색/이력을 모두 설계해야 전체 비공개를 판단할 수 있다. [공식 Navigation 메타데이터 계약](https://developer.android.com/training/cars/apps/navigation#communicate-navigation-metadata)

마켓 제출·사전 협의·실차 인증은 수행하지 않았다. 템플릿 사용이나 DO 메타데이터 선언은 UX 심사 면제가 아니다. [Pleos 주행 제한](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)에 따른 CarUxRestrictions 연동과 목적지 숨김이 핵심 주행 정보 의무에 부합하는지 별도 확인이 필요하다.

## 6. 실제 독립 내비게이션에 추가로 필요한 기술

난이도는 이 프로젝트에서의 구현 부담을 상대 비교한 판단이며 비용/일정 견적이 아니다. 아래 실주행 기능은 이번에 구현하지 않았다.

| 기술 | 지원 근거/현재 상태 | 예상 난이도 | 다음 확인 사항 |
| --- | --- | --- | --- |
| GPS 위치 업데이트 | Android LocationManager 공개 계약 존재, PoC는 권한/위치 조회 없음 | 중간 | Pleos 제공 provider·정확도·빈도·권한 승인, 터널/위치 소실. Google Play services 제공을 가정하지 않음 |
| 지도 렌더링/도로 geometry | 호스트 Surface/Canvas 접근 실제 확인. 지도·도로 데이터는 제공되지 않음 | 높음 | 허용된 지도 SDK/타일·geometry·라이선스, 좌표계·야간·안전 영역·오프라인 지원 |
| 외부 경로탐색 API | CAL은 경로 엔진이 아님. 공급자/계약/키 미선정 | 높음 | 주행용 라이선스, 도로 규제·교통/한국 데이터·geometry/maneuver 응답, 개인정보/네트워크 실패 |
| TBT 안내 생성 | Step/Maneuver/RoutingInfo 정적 표시 확인 | 높음 | 실경로 maneuver, map matching, 교차로/차선/이벤트 순서와 실제 위치 진행 동기화 |
| 이탈/재탐색 | PoC 없음, 별도 엔진 필요 | 높음 | 이탈 판정·재요청·새 경로 비교, 오류 시 안전한 안내 중단 |
| ETA 계산 | TravelEstimate에 전달한 모의 600초만 표시 | 중간~높음 | 실제 경로/교통/차량 속도/정지와 갱신 주기·오차 검증 |
| 활성 경로 소유권 | 공식 NavigationManager/CarAppFocusManager 존재, 획득/반납 미시험 | 높음 | 호스트 대리 역할과 Pleos 직접 관리 요구의 대응, 포커스 상실/전화 투영/OEM 전환 |
| 음성 안내 | CAL 템플릿이 독립 음성 엔진을 제공하지 않음, 이번 발화 없음 | 중간~높음 | 승인된 TTS, 내비 오디오 포커스/중단·재개, 안전 정보/목적지명 발화 분리 |
| 목적지/도착 공개 | 정적 UI에 이름 생략 가능, 실제 도착 상태·Destination 전달 없음 | 높음 | 지도/Trip/클러스터/음성/이력 노출, 정확한 도착 판정·공개 시점, 목적지 데이터 처리 보안 |
| Pleos 앱 심사 | 카테고리/주행 안전 기준 확인, 승인 미실시 | 외부 의존 높음 | APP_MAPS/실제 기능 일치, 목적지 숨김 UX 허용, 권한·라이선스·CarUxRestrictions/실차 시험 |

위치 API 근거: [LocationManager](https://developer.android.com/reference/android/location/LocationManager). 지도·경로 공급자를 새로 추천/도입하거나 외부 서비스에 연결하지 않았다. Canvas 접근만으로 지도 데이터 사용권이나 경로 탐색 권한을 얻는 것은 아니다.

우선순위는 (1) 사무국/마켓 담당에게 숨김 UX·필수 표시 정보·포커스 통합 확인, (2) 합법적으로 사용 가능한 지도/경로 공급자와 응답 계약 선정, (3) 정차 환경에서 가짜 위치 스트림/경로 fixture로 진행·이탈·도착 상태 테스트, (4) 승인된 권한 아래 실제 provider/소유권 검증 순서다. 지도 geometry로 목적지를 추정할 가능성까지 없애려면 화면 생략 이상의 UX 결정이 필요하다. 공식 확인 전에는 안전 안내를 감추거나 가짜 POI 이름을 쓰지 않는다.

## 7. 빌드·회귀 테스트 및 재현 명령

기존 표준 빌드 `build-codex.ps1 -Test` 성공을 먼저 확인했다. 이때는 UP-TO-DATE였으므로, 이후 실제 테스트 재실행을 위해 다음 명령으로 검증했다.

```powershell
.\scripts\build-codex.ps1 -Test -RerunTasks
.\scripts\build-nav-host-poc.ps1
```

- 기존 앱 최종 회귀: **BUILD SUCCESSFUL 29초, 41 tasks executed, 종료 코드 0**. **28개 통과/실패 0/오류 0/스킵 0**(Vehicle 14 + Navigation 13 + 기본 1). [빌드/회귀 로그](independent-nav-evidence/baseline-build-tests.txt), [JUnit 집계](independent-nav-evidence/test-summary.json)
- 최종 PoC: **BUILD SUCCESSFUL 14초, 종료 코드 0**. [로그](independent-nav-evidence/poc-build.txt). API 4 호환 기존 헤더 메서드의 deprecation 경고 2개는 남아 있다.
- 최초 PoC 빌드는 PowerShell의 점을 포함한 Gradle 인수 전달 분리로 실패했다. 문자열 배열로 전달하도록 수정했다. 다음 컴파일에서는 잘못 사용한 `setStep`을 발견해 실제 AAR의 `setCurrentStep` 계약으로 수정했다. 이 두 실패는 호스트 미지원으로 집계하지 않는다. [인수 오류](independent-nav-evidence/poc-argument-failure.txt), [API 오류](independent-nav-evidence/poc-api-failure.txt), [실제 모델 계약](independent-nav-evidence/model-contracts.txt)
- 새 스크립트는 프로젝트 루트를 기준으로 기존 로컬 Gradle/Android user home을 사용하며 임시 환경변수는 finally에서 복구한다. 기존 스크립트 수정 없음. 최종 PoC SHA256: `62BE16E520F837907B5E693EF20B53D972A7A3F854BD5B20AE227289AA2DB392`.
- 정적 host spike의 검증은 실제 APK/콜백/화면으로 수행했고 별도 PoC 단위 테스트 수를 기존 28개에 더하지 않았다. 실경로 테스트 없음.

```powershell
adb -s emulator-5554 shell am get-current-user
adb -s emulator-5554 shell cmd package query-services --brief --user 10 -a android.car.template.host.RendererService
adb -s emulator-5554 install --user 10 -r nav-host-poc/build/outputs/apk/debug/nav-host-poc-debug.apk
adb -s emulator-5554 shell cmd package query-services --brief --user 10 -a androidx.car.app.CarAppService -c androidx.car.app.category.NAVIGATION
adb -s emulator-5554 shell am start --user 10 -W -n com.surpride.navhostpoc/androidx.car.app.activity.CarAppActivity
git diff --check
```

현재 foreground 사용자/serial과 기존 경로가 없음을 먼저 확인한다. PoC에서 설명 → 호스트 뒤로 화살표로 복귀, 다시 설명 → PoC 종료로 종료한다. 다른 앱의 기본 지도 설정을 변경하지 않는다. 저장된 좌표가 없고 geo intent도 처리하지 않는 정적 PoC이므로 실제 안내에 사용하면 안 된다.

## 8. 변경 파일 및 남은 검증

- 수정: `settings.gradle.kts` — 선택형 모듈 include 4줄 추가.
- 생성: `nav-host-poc/build.gradle.kts`, Manifest, `MockNavigationService.kt`, `res/xml/automotive_app_desc.xml`, `res/drawable/ic_poc.xml`.
- 생성: `scripts/build-nav-host-poc.ps1`, 이 문서, `docs/phase1/independent-nav-evidence/`.
- 기존 `app`, `scripts/build-codex.ps1`, Gradle 버전 설정, Phase 0/1 검증 문서는 변경하지 않았다. 캐시/바이너리/원시 UI dump는 Git 제외 경로에만 두었다.

남은 필수 확인은 TravelEstimate 카드/다양한 창 크기 문제, 호스트 API 4의 기능 한계, 실제 위치 provider, 지도/경로 라이선스와 데이터 품질, 단일 경로 포커스/중단 콜백, 주행 제한/안전 UX, 실제 목적지를 가진 모든 출력 채널, Pleos 사전 정책 확인 및 최종 심사다. Car App Library/템플릿의 공식 지원과 앱마켓 승인 가능성을 분리해 계속 관리해야 한다.
