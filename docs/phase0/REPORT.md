# SURPRIDE Phase 0 결과

확인일: 2026-11-09. **사용자 수동 검증으로 Android Studio의 Assemble Project 빌드 성공과 Pleos Connect v2.1.0(API 34) 에뮬레이터의 MainActivity 실제 표시를 확인했다.** 화면의 `SURPRIDE`와 `목적지는 몰라도, 가는 길은 즐겁게.` 문구도 정상 표시되었다. 근거는 사용자가 전달한 수동 검증 결과이며, Codex가 재실행하거나 스크린샷을 확보한 것은 아니다. 기존 Gradle 캐시 쓰기 차단은 Codex 실행 환경의 제약으로 분리한다. SDK 연동은 미검증이며 Phase 1은 **READY_WITH_LIMITATIONS**다. 커밋·Push·프로젝트 재생성은 하지 않았다.

## 1. Git 시작 상태와 종료 상태

- 브랜치 `main`, HEAD `68c808f12fa9682c09886b60620ae2a23fb949ef` 유지.
- 최근 커밋: `68c808f chore: initialize SURPRIDE Android Automotive project`, `2c84072 Initial commit`.
- 시작 시 추적 파일 변경 없음. 기존 미추적 항목 10개 보존: `.idea/.name`, `AndroidProjectSystem.xml`, `codeStyles/`, `compiler.xml`, `deploymentTargetSelector.xml`, `deviceManager.xml`, `gradle.xml`, `misc.xml`, `runConfigurations.xml`, `vcs.xml`.
- 종료 시 추적 파일 4개 수정, 앱 소스/리소스 3개와 `docs/` 추가. staging/commit/push 없음. 원격 `origin`은 요청된 GitHub 저장소이다.

## 2. 기존 프로젝트 문제 및 실제 원인

`app/src/main`에 Activity와 CarAppService가 모두 없었다. Manifest의 `application`은 자식 컴포넌트 없이 종료되며 `MAIN`/`LAUNCHER` 필터도 없었다. AndroidX Car App Library 의존성·호스트 연결 코드도 없다. 따라서 **서비스 기반 앱의 진입점을 IDE가 못 찾는 문제가 아니라 실행 컴포넌트 자체가 없는 템플릿 상태**다. `Launch = Nothing`은 설치만 수행한다.

기존 AppCompat/Material 구성을 유지해 Kotlin `MainActivity` 하나와 XML 화면을 추가했다. CarApp 호스트가 필요한 서비스 구조를 새로 도입할 근거는 없다. Pleos의 [시작 가이드](https://document.pleos.ai/en/docs/pleos-only/getting-started)도 Kotlin Empty Activity와 API 28 이상을 안내한다. AndroidX 템플릿 앱을 선택할 경우에는 별도 [CarAppService/호스트 구성](https://developer.android.com/training/cars/apps/library/set-up-project)이 필요하다.

| 항목 | 확인 결과 |
| --- | --- |
| min / target / compile SDK | 28 / 37 / 37, 기존 값 유지 |
| 설치된 SDK | `android-34`, `android-37.0`; Build Tools `36.0.0` |
| AGP / Wrapper | 9.5.1 / 9.6.0 |
| 실행 JDK | Android Studio JBR 25.0.4, daemon 요구 버전 25 |
| Java/Kotlin JVM target | 기존 Java 11; 내장 Kotlin 기본값은 Java targetCompatibility를 따름 |
| Kotlin 플러그인 | AGP 9의 내장 Kotlin 사용; 별도 kotlin-android 플러그인 불필요 |
| Kotlin 정확한 해석 버전 | Codex 환경의 Gradle 실행 차단으로 앱 compiler dependency 해석은 별도 확인하지 못함; 사용자 빌드 성공과 구분하며 Gradle DSL용 내장 Kotlin과도 구분 |

[AGP 9.5 공식 호환성](https://developer.android.com/build/releases/agp-9-5-0-release-notes)은 API 37, Gradle 9.6.0, JDK 최소 17을 안내한다. [Gradle Java 호환성](https://docs.gradle.org/current/userguide/compatibility.html)은 Java 25 실행을 9.1 이상에서 지원한다. [AGP 내장 Kotlin 문서](https://developer.android.com/build/migrate-to-built-in-kotlin)에 따라 Kotlin 플러그인을 추가하지 않았다. 사용자 수동 검증으로 현재 프로젝트의 Assemble Project 빌드와 Pleos API 34에서 기본 화면 실행을 확인했다. target 37 관련 전체 동작과 다른 기기에서의 호환성 검증은 남는다.

## 3. 수정·생성 파일 목록

수정:

- `app/src/main/AndroidManifest.xml`: Automotive 필수, touchscreen 선택, exported MainActivity, MAIN/LAUNCHER, singleTask.
- `app/src/main/res/values/strings.xml`: SURPRIDE 및 요청한 한국어 문구.
- `app/src/main/res/values/themes.xml`, `values-night/themes.xml`: NoActionBar.

생성:

- `app/src/main/java/com/surpride/app/MainActivity.kt`: XML 시작 화면, 시스템 바/디스플레이 컷아웃 여백 처리.
- `app/src/main/res/layout/activity_main.xml`: 중앙 제목·문구.
- `app/src/main/res/values/start_colors.xml`: 시작 화면 배경색.
- `docs/superpowers/plans/2026-11-09-phase0.md`: 실행 계획·제약 기록.
- `docs/phase0/REPORT.md`: 본 보고서와 Phase 1 계획.
- `docs/phase0/evidence/`: Gradle, ADB, 정적 검사, 저장소 접근, 서비스 버전 확인 기록.

빌드 설정과 SDK 의존성은 수정하지 않았다. 시작 화면에는 위치·차량·음성 권한이 필요하지 않아 추가하지 않았다. 실제 내비게이션 기능이 없는 Phase 0에서는 `APP_MAPS`나 `distractionOptimized`를 선언하지 않았다. 주차 상태에서 확인하는 개발용 화면이며 주행 중 사용 승인이나 배포 적합성을 의미하지 않는다.

## 4. Gradle 빌드 결과

**사용자 수동 검증: Android Studio의 Assemble Project 빌드 성공.** 이는 현재 프로젝트의 실제 빌드 성공 결과로 반영한다. 별도의 `:app:assembleDebug` CLI 실행 로그, Gradle Sync 결과 및 단위 테스트 결과는 전달받지 않았으므로 각각의 성공까지 확대해 기록하지 않는다.

이전 Codex 환경에서의 시도: `gradlew.bat --version`, 이어서 `gradlew.bat help :app:assembleDebug :app:testDebugUnitTest --console=plain`.

두 시도 모두 Wrapper가 외부 사용자 홈의 `.gradle/wrapper/dists/.../gradle-9.6.0-bin.zip.lck`를 열 때 `AccessDeniedException`으로 종료했다. 빌드 시도 exit code는 1이며 Codex에서는 프로젝트 구성 평가·컴파일·단위 테스트에 도달하지 않았다. **이 기록은 Codex 실행 환경의 외부 캐시 접근 제한이며, 프로젝트 자체의 빌드 실패나 현재 Phase 1 진행 차단 사유로 취급하지 않는다.** Codex는 캐시 이동·복사, 권한 승격, IDE를 통한 우회 실행을 하지 않았다. 사용자 수동 빌드 성공과 이 과거 기록은 구분해 보존한다.

소스 XML 13개 파싱, exported MAIN/LAUNCHER, 차량 선언, 선택 touchscreen, 정확한 화면 문구 검사와 `git diff --check`는 통과했다. 이는 AAPT/Manifest merge/Kotlin 컴파일/UI 렌더링 검증이 아니다. 증거: [빌드 로그](evidence/gradle-build.txt), [정적 검사](evidence/static-checks.txt).

## 5. ADB 설치 결과

- `emulator-5554` online, 모델 `Connect_on_x86_64_emulator`.
- 현재 운전자 사용자 ID는 `10`이다. 기존 `com.surpride.app`은 user 10에 `installed=true`, user 0에는 `installed=false`다. 기본 `pm path`의 빈 결과만으로 미설치라고 판단할 수 없다.
- 기존 설치본: versionCode 1, versionName 1.0, minSdk 28, targetSdk 37.
- **이전 Codex의 수정본 ADB 설치 시도 실패**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`가 `failed to stat ... No such file or directory`로 종료. 당시 Codex 환경에서 APK가 생성되지 않았기 때문이다. 기존 설치본은 삭제하지 않았다.
- **이후 사용자 수동 검증에서는 수정본 MainActivity의 실제 화면 표시를 확인했다.** 따라서 에뮬레이터에 앱이 설치되어 실행되는 상태는 확인되었다. 다만 해당 설치 과정의 `adb install` 출력이나 설치 사용자 ID를 새로 전달받은 것은 아니므로 ADB 명령 성공 기록으로 대체하지 않는다.

이전 Codex 시도의 증거: [ADB 로그](evidence/adb-checks.txt). 사용자 수동 실행 성공의 근거는 이번 대화로 전달된 검증 결과다.

## 6. Pleos 에뮬레이터 실제 앱 실행 결과

**실제 앱 실행 검증 성공 — 사용자 수동 검증 결과.**

- 환경: Pleos Connect v2.1.0(API 34) 에뮬레이터.
- SURPRIDE 앱의 `MainActivity` 실제 표시 확인.
- `SURPRIDE` 및 `목적지는 몰라도, 가는 길은 즐겁게.` 문구 정상 표시 확인.
- 최소 실행 진입점과 시작 화면 표시 목표를 충족했다. Vehicle SDK/NaviHelper 연결이나 목적지 비공개 내비게이션 검증을 의미하지 않는다.

이전 Codex ADB 확인에서는 SDK 34 및 `android.hardware.type.automotive`를 확인했다. 당시 수정 전 설치본의 런처 조회 결과는 `No activity found`, `am start --user 10 -W -n com.surpride.app/.MainActivity`는 `Error type 3: Activity class ... does not exist`였다. 이는 수정본의 현재 실행 상태가 아닌 과거 실패 기록이다.

Codex가 수정본 실행을 직접 재확인하거나 스크린샷·새 PID·Logcat을 확보한 것은 아니다. 사용자 수동 검증은 정상 화면 표시까지이며, 장시간 무크래시·전체 런타임 권한·주행 중 UI 동작 검증으로 확대하지 않는다. SDK 권한과 연동은 계속 미검증이다.

## 7. Vehicle SDK 사용 가능성

**판정: 공식 읽기 API는 공개, 실제 연동은 조건부.**

[최신 가져오기 가이드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)는 `implementation("ai.pleos.playground:Vehicle:2.3.2")`, [시작 가이드](https://document.pleos.ai/en/docs/pleos-only/getting-started)와 [2026-10-19 SDK 릴리스](https://document.pleos.ai/en/docs/pleos-only/release-note/sdk-release-note)는 `2.3.1`을 안내한다. Maven은 `https://nexus-playground.pleos.ai/repository/maven-releases/`이다. 이 세션의 해당 POM HEAD 요청은 연결 종료 오류로 실패하여 다운로드·인증 필요 여부를 확정하지 못했다. HTTP 401/403을 확인한 것은 아니므로 승인 부족으로 단정하지 않는다.

| 필요한 데이터 | 공식 근거 / 판정 |
| --- | --- |
| 연료 잔량 | [Fuel](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/entry)의 `checkFuelPercentCapabilities`, `getFuelPercent`, register/unregister API 확인. 실제 지원은 capability와 오류 콜백으로 검증 필요 |
| EV 배터리 SOC | [EvBattery](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/entry)의 `checkEvBatteryStateOfChargeCapabilities`, `getEvBatteryStateOfCharge`, register/unregister API 확인 |
| 주행 가능 거리 | 현재 열람한 Pleos 공개 참조에서 호출 계약까지 확인하지 못함. 릴리스의 과거 `RangeRemainingListener` 언급만으로 사용하지 않음. AAOS [RANGE_REMAINING](https://developer.android.com/reference/android/car/VehiclePropertyIds#RANGE_REMAINING)은 공식 대안 후보지만 차량 지원·읽기 권한을 별도 검증해야 함 |
| 주차 / 주행 상태 | Pleos `DrivingMode`는 주행 모드·크루즈 정보이며 주차 판정과 동일하지 않음. [AAOS GEAR_SELECTION](https://developer.android.com/reference/android/car/VehiclePropertyIds#GEAR_SELECTION), 속도·브레이크 등 공식 속성의 지원/권한을 확인하는 별도 단계 필요. 속도 0만으로 주차로 보지 않음 |
| UI 사용 가능 상태 | [CarUxRestrictionsManager](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide) 연동으로 제한 확인. UX 제한 해제와 차량의 물리적 주차 여부는 별개 |

[권한 가이드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission): Fuel/EvBattery는 대표적으로 `pleos.car.permission.CAR_ENERGY`, DrivingMode/Brake/Odometer는 `CAR_DRIVE`. 각 API의 `@RequiresPermission`이 최종 근거이며 AAOS의 `android.car.permission.*`와 혼용하지 않는다. Phase 0에는 선언하지 않았다.

[프로젝트/SDK 접근 안내](https://document.pleos.ai/en/docs/pleos-only/create-my-project)는 Playground 가입·프로젝트 생성, Vehicle State/NaviHelper의 Ready to Use, Vehicle Control/FusedLocation/STT/TTS의 Not Available Yet를 명시한다. 2.1.0부터 CRN은 불필요하다. 문서 공개와 프로젝트별 사용 권한은 다르므로 계정의 실제 상태 확인이 남는다. [Vehicle 개요](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)는 에뮬레이터와 Virtual Console 검증을 안내한다.

ADB로 측정한 현재 런타임:

| 서비스/앱 | package | versionName |
| --- | --- | --- |
| Vehicle Service | ai.pleos.playground.service.vehicle | 2.3.22 |
| Navi Service | ai.pleos.playground.service.navi | 3.0.4 |
| FusedLocation Service | ai.pleos.playground.service.location.fused | 1.0.0 |
| Navigation app | ai.umos.maps.android.navigation.app | 3.3.2-rc5-hotfix.3 |

서비스 설치와 SDK 호출 가능성은 다르다. 클라이언트 SDK와 서비스의 버전 번호가 같아야 한다고 가정하지 않는다. 최신 Vehicle 개요는 서비스 2.3.21을 기준으로 하므로 현재 이미지에서 callback·capability 실측이 필요하다. 증거: [서비스 확인](evidence/sdk-runtime-packages.txt), [저장소 확인](evidence/sdk-repository-checks.txt).

## 8. NaviHelper 및 목적지 비공개 가능성

[공식 의존성](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk): `implementation("ai.pleos.playground:NaviHelper:3.0.5")`, 위와 같은 Maven. 오래된 2.0.3 예제와 혼합하지 않는다.

[초기화 계약](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release): `NaviHelper(applicationContext)` → `initialize(onConnected, onFailed)` → 연결 후 `addListener()`와 요청 → `removeListener()`/`release()`. 호출 수락과 경로 탐색 완료는 구분한다.

| 기능 | 판정 / 근거 |
| --- | --- |
| 목적지 설정·취소, 경로 요청 | 가능: [requestRoute(RouteInfo), getDestinationInfo, cancelRoute](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations) |
| 경로·도착·안내 상태 이벤트 | 가능: [NaviHelperEventListener](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)의 onRoutingCompleted/onDestinationArrived/onNavigationStatus/onTBTInfo 등. 실제 발행은 내비 앱 구현과 상태에 의존 |
| 잔여시간/ETA | 조건부: 3.0.5 릴리스는 DestinationInfo의 remainTime/remainDistance를 명시. 절대 ETA 필드·단위·업데이트 주기는 이번 열람/실행에서 미검증 |
| SURPRIDE 자체 화면에 목적지 문구 생략 | 가능: 앱이 직접 렌더링하는 정보는 제어 가능. 시스템 전체 비공개를 의미하지 않음 |
| 내장 지도·검색결과·알림·클러스터/HUD·최근 목적지·음성까지 비공개 | **미확인**: 열람한 공개 가이드에서 이를 보장하는 privacy/suppress API를 확인하지 못함. 존재하지 않는다고 단정하지도 않음 |
| 별도 길 안내 화면 | 조건부: TBT 조회/이벤트는 확인. 전체 경로 geometry, 지속 갱신, 자체 지도 렌더링 계약까지 확인되지 않아 완전한 내비 엔진으로 취급할 수 없음 |
| 별도 음성 안내 | 미확인/조건부: 내장 음성의 공식 억제·소유권 전환 계약 미확인. 자체 TTS 사용 가능성과 단일 안내 정책 확인이 선행되어야 함 |
| 내장 안내와 자체 경로/음성의 동시 실행 | 현재 정책상 불가: [단일 경로·단일 Navigation 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide)과 충돌 |

[권한 가이드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)는 정보 조회 `NAVI_ROUTE`, 경로 요청 `NAVI_ROUTE_SEARCH`, 내비 화면 요청 `NAVI_CUSTOM_MAP`을 구분한다. 화면 요청 권한은 목적지 숨김 권한의 증거가 아니다.

**핵심 질문 판정: NaviHelper만으로 목적지를 운전자에게 완전히 숨긴 내비게이션을 구현할 수 있는지는 미확인이다. 현재 공개 근거로 제품 수준의 완전 비공개를 보장할 수 없다.** 앱 내부 표시 생략은 가능하지만 요청이 [내장 Navigation으로 전달되는 구조](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)라 외부 표시를 앱이 통제한다고 가정할 수 없다. poiName을 가명으로 바꾸거나 requestNavigationScreen을 호출하지 않는 것만으로 주소·지도·음성·이력 노출이 해결됐다고 보지 않는다.

일부 API 참조 본문은 검색 도구에 노출되지 않았고 브라우저 보완 확인도 `browser access host unavailable`로 실패했다. POM 접근·SDK 실행 또한 확인되지 않았다. 따라서 메서드 전체 시그니처나 음성 억제 API를 추정하여 작성하지 않았다.

대안과 우선 질문:

1. 우선 **SURPRIDE는 추천·시작/도착 경험, 내장 내비는 유일한 안내 엔진**으로 분리한다. 이 경우 완전한 비밀 목적지는 보장하지 않는 제품 조건이 필요하다.
2. 완전 비공개가 필수라면, 목적지 표시 억제·음성 제어·클러스터/알림/이력 제어·백그라운드 이벤트 지원을 Pleos에 확인한 뒤 NaviHelper 채택 여부를 결정한다. 서비스 3.0.4 + SDK 3.0.5 조합도 확인한다.
3. 불지원이면 자체 UI/음성을 제공할 권리가 있는 별도 공식 내비 엔진을 검토한다. 지도·라우팅·위치·오디오 및 경로 소유권을 모두 담당해야 하며 OEM 안내와 동시에 실행하지 않는다. 이 대안 역시 Pleos 배포 정책과 엔진 라이선스를 먼저 검증한다.
4. 확인 전에는 주차 상태의 Mock 경로 시뮬레이션으로 UX를 개발한다. 중간 경유지 연쇄 설정, 시스템 화면 가림, 비공개 API로 숨김을 흉내 내는 방법은 채택하지 않는다.

주행 UI는 [CarUxRestrictions](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline)를 따라야 한다. [지역별 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy)상 DO 미선언 앱은 주행 중 차단될 수 있다. 추후 실제 내비 앱이 되면 APP_MAPS 카테고리, Navigation focus, 단일 경로 소유권, 주행 중 필수 정보 표시/입력 제한을 구현·심사해야 한다. 단순히 DO=true를 추가하는 것으로 해결하지 않는다.

### 기타 SDK 공개 범위

- FusedLocation: [v1.1.2 문서](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)와 `isSupported()`, 위치 callback, `pleos.car.permission.FUSED_LOCATION`은 공개되어 있다. 접근 안내의 Not Available Yet와 구분하며 현재 프로젝트에서 사용 가능하다고 가정하지 않는다. Google Play Services FusedLocation과도 다르다.
- TTS/STT: [TextToSpeech 3.3.1](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/importing-sdk), [SpeechToText 3.3.1](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/importing-sdk) 의존성 문서는 공개되어 있다. 프로젝트 접근 상태, 서버 연결 시 client ID/secret, 에뮬레이터 엔진/마이크 가용성은 별도 확인이 필요하다. 키·토큰을 코드에 추가하지 않았다.
- Gleo Interface: [App Schema/Action/Callback 가이드](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/core-concepts)가 공개되어 있으며 [assets/schema.json 심사·승인](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema) 절차가 있다. 임의 LLM SDK 접근과 동일하지 않다. [에뮬레이터 2.1.0 릴리스](https://document.pleos.ai/en/docs/pleos-only/release-note/emulator-release-note)는 LLM SDK 지원 종료를 명시한다.

## 9. 발견된 문제와 남은 수동 검증 사항

### 로컬 빌드·재실행

사용자가 Android Studio의 Assemble Project 빌드와 Pleos 에뮬레이터의 실제 화면 표시를 완료했다. 아래 절차와 명령은 재실행·추가 증거 수집용으로 유지하며, 기본 실행 검증이 미완료라는 의미는 아니다. 스크린샷·실행 로그, 별도 단위 테스트, 대형 글꼴·화면 크기 변경·재실행 검증은 추가 확인 대상으로 남는다. 기존 캐시 차단은 Codex 환경 문제로 구분하며 잠금 파일 삭제나 캐시 초기화가 필요하다고 단정하지 않는다.

1. Android Studio에서 기존 프로젝트를 열고 **Sync Project with Gradle Files** 수행. Gradle JDK는 기존 JBR 25 / daemon JVM criteria 25와 일치시키기.
2. Run > Edit Configurations > Android App `app`: Module `Surpride.app`(목록의 app 모듈), Launch **Default Activity**, Pleos API 34 장치를 선택. `Launch = Nothing`이 남아 있으면 변경. 사용자의 실제 실행 성공은 확인했으나 구체적인 Run Configuration 설정값은 별도로 전달받지 않았다.
3. 주차 상태로 Run. 런처에 SURPRIDE가 나타나고 두 문구가 표시되는지 확인. 대형 글꼴·화면 크기 변경·재실행도 확인.

프로젝트 루트에서 재현할 명령:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat help :app:assembleDebug :app:testDebugUnitTest --console=plain
# 위 명령이 BUILD SUCCESSFUL인 경우에만 계속한다.
$phase0Adb = 'C:\Users\surlw\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $phase0Adb devices -l
& $phase0Adb -s emulator-5554 shell am get-current-user
# 사용자 ID는 매번 확인. 아래 10은 이번 세션의 운전자 ID.
& $phase0Adb -s emulator-5554 install --user 10 -r app\build\outputs\apk\debug\app-debug.apk
& $phase0Adb -s emulator-5554 shell pm path --user 10 com.surpride.app
& $phase0Adb -s emulator-5554 shell cmd package resolve-activity --brief --user 10 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER com.surpride.app
& $phase0Adb -s emulator-5554 shell am start --user 10 -W -n com.surpride.app/.MainActivity
& $phase0Adb -s emulator-5554 shell pidof com.surpride.app
& $phase0Adb -s emulator-5554 shell dumpsys activity activities | Select-String 'com.surpride.app|mResumedActivity'
& $phase0Adb -s emulator-5554 logcat -b crash -d -t 150
# 표시된 화면을 직접 확인한 후 증거 저장. Windows PowerShell의 바이너리 > 리다이렉션은 피한다.
& $phase0Adb -s emulator-5554 shell screencap -p /data/local/tmp/surpride-phase0.png
& $phase0Adb -s emulator-5554 pull /data/local/tmp/surpride-phase0.png docs/phase0/evidence/surpride-phase0.png
```

`adb install`의 Success, `am start`의 Status: ok, 실행 PID/foreground, 실제 앱 화면을 각각 확인해야 한다. 다른 사용자·장치의 설치 결과를 혼동하지 않는다. Logcat 공유 시 다른 앱 정보와 개인정보를 제거한다.

### SDK 확인 우선순위

- Playground 프로젝트 생성 여부 및 각 SDK 실제 접근 상태 확인.
- 로컬 Gradle에서 Pleos Maven 해석 확인 후 사용할 버전을 고정. 최신 가이드와 2.1.0 baseline 차이를 해결.
- Vehicle capability → 1회 read → 구독 → 상태 변경 → 구독 해제·release 순으로 Virtual Console에서 확인.
- Navi 초기화 연결 성공 → 정보 조회 → 정차 상태 경로 요청 → 완료/오류/도착 이벤트 확인. Kotlin named arguments로 좌표 전달하고 3.0.5의 위도·경도 순서 변경에 주의.
- 목적지 이름·주소·지도 끝점·음성·알림·클러스터/HUD·최근 목적지·Gleo 질의까지 노출 표를 작성. 공식 차단 수단이 없는 표면은 보장 범위에서 제외.

## 10. Phase 1 진행 가능 여부

**READY_WITH_LIMITATIONS.** 사용자 수동 검증으로 Assemble Project 빌드와 Pleos Connect v2.1.0(API 34)의 MainActivity·요청 문구 표시를 확인했으므로 최소 앱 실행 기반은 확보되었다. Codex의 외부 Gradle 캐시 접근 차단은 환경 제약으로 분리하며 Phase 1 진행 차단 사유에서 제외한다.

Phase 1의 Mock 개발과 단계적인 SDK 통합 검증을 진행할 수 있다. 단, Vehicle SDK/NaviHelper의 의존성 해석·접근 권한·서비스 연결·실제 API 호출은 여전히 미검증이다. SDK 버전 호환성, 주행 UI 정책 및 목적지 완전 비공개 가능성도 확인되지 않았으므로 연동 성공이나 미스터리 내비게이션 구현 가능성을 보장하지 않는다.

통합 순서:

1. 기본 빌드·실행·시작 문구 표시는 사용자 수동 검증으로 완료. 재현성을 위해 Run Configuration을 확인하고 필요 시 별도 CLI 빌드/테스트 로그·스크린샷·크래시 기록을 추가 수집한다.
2. 앱 고유의 작은 `VehicleStateSource`, `NavigationGateway` 경계를 먼저 정의. SDK 객체를 UI에 직접 노출하지 않고 Mock으로 상태/이벤트 흐름을 구현. Phase 0에는 코드로 추가하지 않음.
3. 접근 확인된 Vehicle 읽기부터 연결: `Vehicle(context)` → `initialize()` → `getFuel()`/`getEvBattery()` → 공식 capability/read → callback. 사용 종료 시 같은 listener/area로 구독 해제 후 `release()`. 차량 제어는 범위 제외.
4. `CarUxRestrictionsManager`로 현재 제한과 변화를 반영. 주행 허용 UI를 도입하기 전 카테고리/DO/정책을 검증.
5. NaviHelper 3.0.5 의존성·최소 권한 추가 후 위 초기화 계약대로 연결. 목적지 없이 조회부터 검증하고, 정차 상태의 명시적 테스트로 requestRoute 및 이벤트 계약 확인.
6. 비공개 노출 검증·공식 답변 후에만 미스터리 안내 방식 결정. STT/TTS/FusedLocation/Gleo는 접근이 확보된 기능만 후순위로 연결.

Mock 계약 제안(아래 이름은 SURPRIDE 내부 설계이며 Pleos API가 아님):

| 경계 | 최소 데이터/동작 | 검증 시나리오 |
| --- | --- | --- |
| VehicleStateSource | fuelPercent, batteryPercent, rangeMeters, parkingState와 각 값의 unavailable/unsupported/denied/stale 상태 | ICE/EV/하이브리드, null, 권한 거부, 저잔량, 시간 경과, 연결 끊김, 주차→주행 |
| NavigationGateway | connect, requestDestination, cancel, 이벤트: connected/searching/guiding/arrived/error/disconnected | 요청 수락 후 탐색 실패, timeout, 취소 후 늦은 이벤트, 중복 도착, 재연결, 좌표 순서 |
| GuidanceViewState | UI에 공개할 TBT/잔여시간만 전달, 목적지 상세는 별도 reveal 상태에서 제공 | 도착 전 UI/음성 입력/로그에 실제 이름·주소 미포함, 도착 공개는 한 번만 발생 |

단위 테스트는 Mock 이벤트를 수동 시계로 재생해 UI 상태 전이를 검증한다. Mock의 성공을 SDK 성공으로 기록하지 않는다. 실제 에뮬레이터 테스트에서는 callback 계약, 값의 단위/지원 여부, permission 오류, lifecycle 구독 정리를 별도로 검증한다. UI 내부 비공개 테스트와 OEM 시스템 전체 비공개 테스트 역시 분리한다.
