# SURPRIDE Phase 1A — Vehicle SDK 검증

확인일: 2026-11-09. 현재 판정은 **READY_WITH_LIMITATIONS**다. 실제 Pleos 서비스에서 배터리 SOC와 누적 주행거리를 읽었고 갱신 콜백을 수신했다. 연료는 미지원이며, 기어·정차/주행·주행 가능 거리는 공개 SDK 조회 계약을 확인하지 못해 호출하지 않았다. 이 문서의 실제 SDK 결과와 테스트 Fake 결과는 별개다.

## 환경·버전·권한

- 시작: `main`, HEAD `e8e1925e627eb24cbec51f76c7b7253fc9230a6d`, 작업 트리 깨끗함. 커밋·Push·원격 변경 없음.
- 기존 Wrapper 9.6.0 / AGP 9.5.1 / JBR 25 / min-target-compile SDK 28-37-37 및 Phase 0B 스크립트 유지.
- 공식 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`, 의존성 `ai.pleos.playground:Vehicle:2.3.2`. 실제 AAR/POM/classes.jar와 전이 의존성 해석 및 빌드 성공. SDK 바이너리는 무시되는 로컬 Gradle 캐시에만 저장.
- 에뮬레이터: Pleos Connect v2.1.0, ADB `emulator-5554`, Android API 34, 운전자 사용자 10. Vehicle Service `2.3.22`, versionCode 74.
- [최신 공식 샘플](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app)은 SDK 2.3.2 / Service 2.3.22 / Pleos v2.1 Android 14를 기준으로 한다. 예전 릴리스의 2.3.1 안내 때문에 SDK를 낮추지 않았다. AAR Manifest의 최소 서비스는 2.3.13 / code 65이며 현재 서비스는 그 이상이다. 버전 비교만으로 모든 기능 지원을 주장하지 않는다.
- 수동 추가한 권한은 `pleos.car.permission.CAR_ENERGY`, `pleos.car.permission.CAR_DRIVE`뿐이다. 실제 설치본에서 둘 다 granted=true를 확인했다. SDK AAR이 병합한 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`도 있다. `CONTROL_*`, `PRIVATE_*` 권한이나 차량 set API는 추가하지 않았다.

[공식 의존성](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup), [도메인별 읽기 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission).

## 사용한 공개 API와 실제 조회 결과

| 항목 | 실제 호출 | 에뮬레이터 결과 |
| --- | --- | --- |
| 배터리 SOC | getEvBattery → checkEvBatteryStateOfChargeCapabilities → getEvBatteryStateOfCharge → register/unregisterEvBatteryStateOfCharge | Capability READ, **100%**, AVAILABLE, 실제 갱신 콜백 수신 |
| 연료 | getFuel → checkFuelPercentCapabilities | **ERROR_NOT_SUPPORTED_PROPERTY**, UNSUPPORTED. capability 실패 후 read/subscribe 호출하지 않음 |
| 누적 주행거리 | getOdometer → checkCurrentDistanceDrivenCapabilities → getCurrentDistanceDriven → register/unregisterDistanceDriven | Capability READ, **원시값 0.0**, AVAILABLE, 실제 갱신 콜백 수신 |
| 주행 가능 거리 | 호출하지 않음 | NOT_QUERIED. 공개 참조에서 읽기 계약을 확인하지 못함 |
| 기어 | 호출하지 않음 | NOT_QUERIED. 공식 샘플이 Gear를 공개 카탈로그에서 제외 |
| 정차/주행 | 판정하지 않음 | NOT_QUERIED. 기어·속도를 읽지 않았으므로 속도 0이나 브레이크만으로 정차라고 가정하지 않음 |

공개 [Odometer 참조](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/entry)와 [공식 샘플 저장소](https://github.com/Pleos-Playground/pleos-sample-vehicle-app)를 대조했다. 바이너리에 남아 있는 `getGear`, `getRangeRemainingDistance` 등을 공개 API로 간주해 호출하지 않았다. SDK 원시 주행거리의 단위/환산은 이번 검증에서 확정하지 못했으므로 UI에도 km라고 표시하지 않는다. 0.0은 실제 서비스가 반환한 값이며 테스트 기본값으로 대체한 것이 아니다.

`Vehicle(context)` → `initialize()` → capability/read/subscribe → 같은 listener로 unregister → `release()` 순서를 사용한다. `INITIALIZED`는 초기화 호출 반환만 뜻하고 서비스 연결 완료를 뜻하지 않는다. capability/read 응답을 받은 뒤에만 `RESPONDING`으로 표시한다. [공식 초기화·callback 계약](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object).

## 아키텍처와 오류 처리

- `VehicleDataSource`: start/refresh/stop과 공통 상태 제공.
- `PleosVehicleDataSource`: application context, 메인 스레드 callback 직렬화, 10초 timeout, SDK 읽기 어댑터.
- `VehicleSession`: Android와 분리된 상태 관리. 세션/조회/구독별 유효성을 구분해 이전 세션·해제된 구독·시간 초과 요청의 지연 응답을 무시한다. 모든 구독의 정리를 시도한 뒤 release하고 정리 오류 수를 노출한다.
- `VehicleState`: 출처, 연결, capability, 값, 수신 시각, 상태, 콜백 수, 구독 수. 미지원(UNSUPPORTED), 조회하지 않음(NOT_QUERIED), 권한 거부(PERMISSION_DENIED), null(NO_DATA), 일반 오류(ERROR), 응답 시간 초과(TIMEOUT)를 구분한다.
- `FakeVehicleDataSource`: `src/test`에만 존재하며 APK에 포함하거나 실제 데이터의 fallback으로 사용하지 않는다.
- MainActivity는 debug APK에서만 진단 패널을 표시한다. 기존 SURPRIDE 제목과 문구를 보존했다. onStart에서 연결하고 onStop/중지 버튼에서 구독 해제·release한다. 수동 중지 후 값은 마지막 수신 기록이라고 표시한다. 개발 패널은 주행 중 사용 승인이나 최종 UI가 아니다.

SDK 2.3.2의 실제 예외 클래스는 난독화되어 문서의 VehicleException/ErrorCode 타입명과 다르다. 내부 필드/메서드에 reflection으로 접근하지 않았다. 실제 연료 capability가 반환한 표준 `Exception.message`는 정확히 `ERROR_NOT_SUPPORTED_PROPERTY`였다. [공식 오류 코드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code)와 완전히 일치하는 메시지만 분류하고, 알 수 없는 메시지는 ERROR로 유지하며 원문을 UI/로그에 출력하지 않는다. `SecurityException` 및 사전 권한 검사도 별도로 처리한다. 따라서 SDK의 예외 포맷이 달라져도 권한 문제나 미지원을 임의 추정하지 않는다.

## 자동 빌드·테스트

표준 명령:

```powershell
.\scripts\build-codex.ps1 -Test
git diff --check
```

기존 Phase 0B의 프로젝트 전용 Gradle/Android User Home, debug 서명, in-process Kotlin 설정을 그대로 사용했다. 최초 의존성 추가만으로 기존 빌드가 성공했다. 이후 테스트에서 초기화 실패 시 release 누락, 새로고침 중 콜백과 권한 오류의 경합, 해제된 구독의 지연 콜백, 시간 초과 후 늦은 capability, 등록 중 동기 오류 사례를 재현하고 수정했다.

최종 결과: **BUILD SUCCESSFUL, exit 0, 17초**. 단위 테스트는 **15개 통과, 실패/오류/건너뜀 0개**(기존 템플릿 1개 + VehicleSession 14개)다. `git diff --check`도 통과했다. Fake 경계 테스트는 실제 OS 권한 거부나 에뮬레이터 상태 변경의 성공 증거가 아니다. 계측 테스트·release/R8 빌드는 이번 Phase에서 실행하지 않았다.

[최종 빌드·테스트 로그](evidence/build-tests.txt), [테스트 XML 요약](evidence/test-summary.txt), [SDK 해석 로그](evidence/sdk-resolution.txt), [실제 공개 listener 시그니처](evidence/public-sdk-types.txt).

## 실제 설치·실행·콜백·정리

debug APK를 사용자 10에 `adb install --user 10 -r`로 갱신 설치하고 MainActivity를 실행했다. 데이터 삭제·앱 제거·다른 ADB 디바이스 접근은 하지 않았다.

실제 SDK에서 배터리/누적 주행거리 2개 구독이 만들어졌고 초기 2회 이후 반복 콜백이 수신되었다. 중지 버튼으로 SDK listener 두 개의 `removeListener`, `Vehicle.release`, `STOPPED subscriptions=0 cleanupErrors=0` 로그와 패널을 확인했다. 중지 후 나중에 다시 조회한 패널의 콜백 수·값·시각도 같았다. 이는 SDK 구독 해제 호출/앱 반영의 증거이며 별도의 서비스 측 해제 승인 응답을 받은 것은 아니다.

최종 APK는 설치 Success, cold start Status: ok(1000ms)였다. [실제 패널](evidence/live-panel.txt), [화면 캡처](evidence/live-screen.png), [설치·시작 및 APK 해시](evidence/adb-install-start.txt), [중지 패널](evidence/stopped-panel.txt), [재연결 패널](evidence/reconnected-panel.txt), [구독 해제/재연결 검사](evidence/lifecycle.txt), [앱 PID별 Logcat](evidence/vehicle-logcat.txt)을 저장했다. 중지 후 패널 불변과 수동 재연결 시 RESPONDING·구독 2 복귀도 확인했다. 최종 로그에서 listener 해제 2개, release 1회, 정리 오류 0, FATAL/ANR 일치 0개였다. 짧은 시작/중지 구간 결과이며 장시간 안정성 검증으로 확대하지 않는다. 재실행 Intent는 기존 Activity에 전달되었으므로 Activity 재생성이나 실제 onStop 전환 검증으로 주장하지 않는다.

## Virtual Console와 남은 수동 검증

공식 [Virtual Console 설치 안내](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)에 따라 대상 에뮬레이터에 `adb -s emulator-5554 forward tcp:8088 tcp:8088`을 설정했고 `http://127.0.0.1:8088/`의 HTTP 200을 확인했다. 그러나 브라우저 연결 호스트가 없었고 네이티브 Edge 실행은 도구 승인 시간 초과로 끝났다. 차량 상태를 바꾸는 미공개 HTTP/VHAL 명령으로 우회하지 않았다. 검증 후 이번에 추가한 포워딩은 제거했으므로 수동 확인 전에 위 명령을 다시 실행한다.

따라서 **콘솔에서 값을 실제 변경한 뒤 콜백 값이 달라지는 과정은 미검증**이다. 수동 검증 순서:

1. Pleos 에뮬레이터에서 안전하게 주차 상태를 유지하고 `http://127.0.0.1:8088/`의 Emulation Controls를 연다.
2. [공식 콘솔 가이드](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide)의 Energy → Battery SOC에서 현재값을 기록하고 다른 값으로 변경한다. 앱의 SOC·수신 콜백 수·수신 시각이 갱신되는지 확인하고 원복한다.
3. Motion → Odometer 값을 기록·변경해 원시값과 콘솔 단위를 대조하고 원복한다. 임의 환산을 추천 로직에 적용하지 않는다.
4. 앱의 구독 중지를 누른 뒤 콘솔 값을 바꿔도 앱이 변하지 않는지 확인한다. 연결/새로고침 후 최신값을 수신하는지 확인하고 콘솔 상태를 원복한다.
5. 권한 거부/데이터 없음은 현재 Fake 경계 테스트로 검증했다. Pleos 권한은 현재 설치본에 정상 허용되어 있어 실제 OS 거부 시나리오는 실행하지 않았다. 연료 미지원은 실제 capability 응답으로 확인했다.

이번 앱은 차량 set API를 전혀 호출하지 않는다. 위 수동 변경은 가상 차량 콘솔의 시뮬레이션에 한정한다.

## 변경 파일 및 다음 제약

- 수정: `settings.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `MainActivity.kt`, `res/layout/activity_main.xml`, `res/values/strings.xml`.
- 생성: `vehicle/VehicleState.kt`, `VehicleDataSource.kt`, `PleosVehicleDataSource.kt`; test의 `FakeVehicleDataSource.kt`, `VehicleSessionTest.kt`; 본 문서·실행 계획·검증 증거.
- 기존 Phase 0/0B 문서, 스크립트, 도구 버전 및 Android Studio 설정은 수정하지 않았다. SDK 바이너리와 임시 UI XML은 무시되는 `.gradle/`에 둔다. 토큰/키/VIN/위치 정보를 수집하거나 기록하지 않는다.

**READY_WITH_LIMITATIONS:** SDK 의존성·초기화·배터리/누적거리 실제 조회·콜백 수신은 성공했다. 콘솔 상태 변경, 원시 거리 단위, 실제 권한 거부, 주행 UI 정책, 미조회 기어/주행 가능 거리의 공개 AAOS 대안은 다음 검증으로 남는다. NaviHelper는 추가하지 않았다. SDK 데이터가 없음/미지원/미조회인 경우 추천 판단에 임의의 Mock 값이나 정차 상태를 넣어서는 안 된다.
