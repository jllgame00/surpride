# Phase 6B-6 — 개발 도구·테스트 대역 정리

**판정: READY_WITH_LIMITATIONS.** A15/A16 코드 정리와 이번 JVM/서버 675개 검사·두 APK 빌드가 통과했다. 지정해 조회한 `emulator-5556`은 연결되어 있지 않아 native 계측 31개는 이번에 재실행하지 않았다. 기존 B5의 31개 통과 이력을 이번 실행 결과에 합쳐 새 전체 PASS라고 주장하지 않는다.

| 유지하는 상태 | 결과 |
|---|---|
| HOST_ACTIVE | BLOCKED |
| DESTINATION_PRIVACY | PARTIAL |
| CACHE_POLICY | PARTIAL |
| DATA_RETENTION | PARTIAL |
| 실차·상용 배포 | 미검증·미승인 |

## 1. Git·기준 보존

- 시작/종료: `main / d68a89b3b2f74934877f21a8db0630ebcee8c71d`. 시작 status는 clean이었다. 요청서의 `9f63500` 이후 B5 변경은 `d68a89b fix(core): improve route performance and data retention`으로 이미 커밋되어 있었다.
- 이번 작업에서는 commit·push·staging·branch 전환·이력 변경·reset·clean을 하지 않았다. 기존 추적 파일 SHA-256을 시작에 고정했다.
- `.gitignore`는 시작 bytes 그대로다. **`.gitignore:59: /docs/phase*/`** 때문에 새 `docs/phase6/B6_TOOLING_CLEANUP.md`와 `docs/phase6/evidence/b6/`가 ignore된다. 기존 감사 문서와 B5 보고서는 덮어쓰지 않았다. 신규 테스트 source 3개는 ignore되지 않는다.
- 참조: [6A 감사](CODEBASE_AUDIT.md), [개선 계획 A15/A16](REFACTOR_PLAN.md), [위험 등록부](RELEASE_RISK_REGISTER.md), [B5 결과](B5_PERFORMANCE_PRIVACY.md). 과거 위험 집계·판정은 그 시점의 기록으로 유지한다.

## 2. A15 호출부와 이동

| 클래스 | 기존 위치 | 새 위치 | 실제 사용 |
|---|---|---|---|
| `UnconfiguredRouteProvider` | app main `realdata/RouteModels.kt` | app test `realdata/UnconfiguredRouteProvider.kt` | `RealRouteTest` 2곳 |
| `FakeLocationProvider` | PoC main `tracking/PositionProvider.kt` | PoC test `tracking/FakeLocationProvider.kt` | `LocationSessionTest` 2곳, `ActiveGuidanceIntegrationTest` 1곳, `SessionRestorationIntegrationTest` 1곳 |

main/debug/release/androidTest 및 전체 source/config 참조를 다시 확인했다. 두 클래스의 **제품 호출 0, native 계측 호출 0**이다. Gradle에 별도 flavor/custom sourceSet 주입은 없고 표준 debug/release·src/test 구성을 사용한다.

기존 package·가시성·구현 본문을 유지하고 두 클래스만 src/test로 분리했다. 시작 HEAD의 본문과 이동 후 본문을 개행 정규화하여 대조했다. `RouteProvider`, `PositionProvider`, `PositionSession`, 실제 GPS/API 구현, `PositionSource.FAKE`의 안전 분기 및 다른 Mock/Adapter/Port를 변경하지 않았다. 자동 fallback도 추가하지 않았다.

기존 JVM 테스트가 이동된 Fake로 통과했다. 양쪽 제품 debug APK의 모든 DEX에서 해당 두 class descriptor가 없음을 확인했다. 계측 APK 빌드와 두 모듈 release Kotlin 컴파일도 성공해 다른 variant의 제품 참조 누락을 확인했다. APK 크기·성능 개선 수치는 측정하거나 주장하지 않는다.

## 3. A16 serial 계약

네 도구 모두 `--serial emulator-5556`을 지원한다.

- `backend.dev provision-android --adb adb --serial emulator-5556`
- `backend.nav_poc --adb adb --serial emulator-5556 --request-public-route`
- `backend.gps_replay --adb adb --serial emulator-5556 --public-route-prefix ...`
- `backend.active_replay --adb adb --serial emulator-5556 --public-session ...`

위 목록은 옵션 형식이며 이번에 실제 provisioning/API/GPS 명령을 실행한 기록이 아니다. 공개 경로 opt-in·정차·안전 종료 조건은 그대로다. Host ACTIVE 차단을 해제하는 절차도 아니다.

### 선택·실패 규칙

1. `dev.adb_serial` 한 함수에서 `emulator-` 뒤 4~5자리 숫자·1024~65534의 짝수 port만 허용한다. 이는 이 프로젝트 도구의 입력 제한이다. invalid 형식/port는 ADB와 자격 파일 읽기 전 일정한 오류로 거부한다.
2. 기존 CLI와 `dev.provision(adb)` 호환을 위해 생략 기본값은 **고정 emulator-5554**다. `ANDROID_SERIAL`·연결 목록·첫 기기를 사용하지 않는다. README는 새 검증에서 serial 명시를 권장한다.
3. 쓰기·바이트 검증·경로 읽기·model 확인·공식 console GPS 입력 모두 동일 `-s serial`을 사용한다. 다른 대상 재시도·discovery·자동 설치·데이터 초기화 기능은 추가하지 않았다.
4. 없는/offline 기기나 첫 명령 실패는 중단한다. target을 바꿔 계속하지 않는다. PoC 도구는 외부 API 요청 **전** 지정 기기의 `run-as com.surpride.navhostpoc --user 10 id -u`로 user10 UID를 확인한다.
5. 메인은 `com.surpride.app`, PoC/replay는 `com.surpride.navhostpoc`, user는 **10 고정**이다. `run-as`가 debug 패키지·사용자 접근을 검사한다. replay의 기존 Pleos model·공개 경로/세션·범위/신선도 검사를 보존했다. 모든 경로에 별도의 foreground user 검증이 있다고 과장하지 않는다.

### 인증정보·출력

토큰은 기존 private session에서 읽어 `run-as ... sh -c 'cat > ...'`의 **stdin**으로만 전달한다. argv·stdout·stderr에 토큰을 싣지 않는다. subprocess stdout/stderr는 capture하고 실패 시 원문을 출력하지 않는다. PoC HTTP 실패도 신뢰하지 않는 `error` 본문 대신 상태 코드만 표시한다. 성공 결과의 serial은 민감정보 없는 대상 확인값이다.

기존 `test_dev.py`는 변경하지 않았으며 `provision(adb)`의 원래 두 ADB 호출·stdin·바이트 검증이 그대로 통과한다. 새 serial의 쓰기/검증도 같은 기기로 전달됨을 검사했다. 실제 credential을 provision하거나 API를 요청하지 않았다.

## 4. 신규 회귀·실패 재현

`backend/tests/test_adb_serial.py`에 **30개** 검사를 추가했다. subprocess·HTTP는 mock이고 session도 메모리 대역이다.

- 네 CLI의 명시 serial 성공, 모든 ADB 호출의 동일 serial, 자동 검색/설치/clear 없음.
- 네 CLI의 기존 고정 기본값과 `ANDROID_SERIAL` 무시.
- 잘못된 형식 입력의 조기 거부, 잘못된 console port의 직접 provision 거부.
- 없는/offline 대상 첫 실패에서 중단, 다른 기기로 fallback 없음, PoC는 API 요청도 0회.
- 쓰기 뒤 바이트 검증 실패에서 retarget 없음.
- stdin 자격 전달, argv/stdout/stderr 원문 비노출, 악의적 proxy 오류 본문 비출력.

수정 전 최초 27개를 실행해 **11 실패 / 16 통과**를 확인했다. 주 원인은 기존 CLI의 `--serial` 미지원이다. 수정 후 port 경계 3개를 추가해 신규 30개와 기존 서버 106개가 모두 통과했다.

최초 임시 디렉터리 fixture는 제한 환경에서 PermissionError가 발생했다. 이는 제품 결함 RED로 집계하지 않았다. 디스크 임시 session을 만들 필요가 없어 기존 `test_dev`와 같은 메모리 session 대역으로 바꾼 뒤 정식 RED/GREEN을 실행했다. 권한 변경·우회는 하지 않았다.

## 5. 검사 집계와 빌드

| 영역 | B5 기존 | 신규 | 이번 실행 PASS | 별도 이전 이력 |
|---|---:|---:|---:|---|
| Android JVM | 231 | 0 | 231 | — |
| Navigation PoC JVM | 308 | 0 | 308 | — |
| FastAPI/Python | 106 | 30 | 136 | — |
| Android 계측 | 31 | 0 | **미실행** | B5 앱28 + PoC3 PASS |
| 합계 | 676 | 30 | **675** | 계측31은 중복·신규 PASS로 계산하지 않음 |

보존한 전체 고유 검사 목록은 **706개**다. 이번 실제 통과 675개와 이전 계측 31개를 구분한다. 기존 검사 파일은 삭제·수정·skip·비활성화하지 않았다. 이동한 Fake 두 파일은 helper이며 신규 테스트 개수에 포함하지 않는다.

실행:

```text
gradlew.bat -PnavHostPoc=true :app:testDebugUnitTest :nav-host-poc:testDebugUnitTest
  :app:assembleDebug :app:assembleDebugAndroidTest
  :nav-host-poc:assembleDebug :nav-host-poc:assembleDebugAndroidTest
  :app:compileReleaseKotlin :nav-host-poc:compileReleaseKotlin
  --offline --no-daemon --console=plain -Pkotlin.compiler.execution.strategy=in-process
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
```

프로젝트의 기존 workspace GRADLE_USER_HOME/ANDROID_USER_HOME을 사용했다. Gradle은 **BUILD SUCCESSFUL, 2m12s**, JVM failure/error/skip은 0이다. 서버 **136 PASS**, 기존 Starlette/httpx deprecation 경고 1개가 있다. SDK deprecated/JDK native access 경고를 없애려 새 SDK를 추가하지 않았다.

두 debug APK와 두 계측 APK 빌드 성공. release Kotlin 컴파일도 성공했다. 새 APK는 설치·실행하지 않았다. 제품 APK 해시·DEX 확인 및 집계는 [검증 증거](evidence/b6/verification.json)에 있다.

### 실제 개발 환경 경계

user가 언급한 `emulator-5556`에 명시적으로 `adb -s emulator-5556 get-state`만 실행했으나 **device not found**였다. 연결 기기를 검색해 다른 대상으로 바꾸지 않았다. 대상 serial 확인 질문을 제시했지만 별도 지정이 없으므로 기기 선택을 추정하지 않았다. 실제 인증정보 전달·GPS 변경·설치·앱 실행·기록/데이터 변경은 0회다. 계측31 전체 재실행은 남은 개발 환경 제한이다.

## 6. 기존 Phase 6B 보존

| 기존 계약 | 이번 확인 |
|---|---|
| A01/A02 회전 거리·geometry 연결성 | PoC 전체308 및 기존 guidance/matcher 동등성 회귀 통과, 해당 제품 파일 무변경 |
| A03/A06 원자 저장·공개/도착/복귀 복원 | 기존 persistence·Screen 회귀 통과, 제품 구현 무변경 |
| A04/A05 lifecycle/ownership | 기존 Screen stale callback·cleanup 회귀 통과, 실제 Host ACTIVE는 BLOCKED |
| A13 watchdog | 기존 voice 회귀 통과, 제품 voice·Focus 구현 무변경 |
| A07/A08/A14 추천·LIVE 방문 | app 전체231 통과, RouteModels의 Unconfigured 클래스 외 순수 계산·인터페이스 보존 |
| A09 계산 성능 계약 | 기존 frozen matcher/이진 lookup/합성 측정 검사 통과. B5 측정 증거를 새 성능 인증으로 덮어쓰지 않음 |
| A10/A11 캐시·packet cleanup | 기존 cache/retention 회귀 통과, 제품 정책 무변경. native/장기 및 전체 개인정보 제한 유지 |
| A12 HTTP client 수명 | 기존 lifecycle·stream 취소·close 회귀를 포함한 서버136 통과, backend/app.py 무변경 |

신선한 별도 리뷰에서 Critical/Important/Minor는 모두 0이었다. 리뷰는 실제 기기·Host 및 최종 문서의 정확성을 대신 검증하지 않는다. 이번 테스트 성공을 실제 Host 조작·실차 내비 안전 성공으로 해석하지 않는다.

## 7. 변경 파일

수정한 추적 파일:

- `app/src/main/java/com/surpride/app/realdata/RouteModels.kt`
- `nav-host-poc/src/main/java/com/surpride/navhostpoc/tracking/PositionProvider.kt`
- `backend/dev.py`
- `backend/nav_poc.py`
- `backend/gps_replay.py`
- `backend/active_replay.py`
- `backend/README.md`

신규 source/helper:

- `app/src/test/java/com/surpride/app/realdata/UnconfiguredRouteProvider.kt`
- `nav-host-poc/src/test/java/com/surpride/navhostpoc/tracking/FakeLocationProvider.kt`
- `backend/tests/test_adb_serial.py`

신규 문서·증거: 이 보고서와 `docs/phase6/evidence/b6/`. ignore 규칙은 위 1절과 같으며 임의로 제거하거나 강제 add하지 않았다.

## 8. 남은 위험과 Phase 6C

| 등급 | 현재 남는 문제·제한 |
|---|---|
| P0 | 이번 좁은 변경 리뷰에서 새 P0 확인 없음. 전체 제품 재감사·실차 안전 인증을 수행한 결과는 아님 |
| P1 | A04 공식 Host 종료/컨트롤, A05 실제 Binder·Focus/Surface lifecycle E2E 제한. 기존 FocusEvent ANR 원인은 미확정 |
| P2 | A09 Android/장시간 profile, A10 실제 HTTP·native close·disk 장기 정책, A11 off-process 물리 삭제·LIVE 평문/전체 보존, A12 실제 TCP/TLS 운영, A13 실제 음향 등 기존 미검증 범위 |
| P3 | A15 배치 정리 완료. A16 serial 선택/실패/비노출 코드 계약 완료. 선택 AVD에서의 실제 provisioning/GPS 성공은 미검증이며 이번에 실행하지 않음 |

**Phase 6C 제출 준비 진입 권고:** 제한을 명시한 정적·추천·계약 증거 정리에는 진입 가능하다. 지정된 정차 AVD를 준비하면 변경된 APK의 계측31을 다시 실행하고 합계를 갱신해야 한다. 그 전에는 이번 전체706 실행 완료로 발표하지 않는다. ignore된 문서·증거의 추적 방법을 검토하고, 제출 자료가 HOST_ACTIVE BLOCKED와 개인정보/캐시/보존 PARTIAL을 숨기지 않게 한다. 실차·상용 배포 준비 완료 판정은 하지 않는다.

미검증: native 계측 재실행·실제 serial provisioning/GPS·Host ACTIVE·실차·실음향·장시간. 기존 제한을 유지하며 커밋·Push하지 않았다.
