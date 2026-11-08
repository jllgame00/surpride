# SURPRIDE Phase 0B 자동 빌드·실행 검증

확인일: 2026-11-09. Phase 0의 사용자 수동 빌드·실행 검증 기록은 `REPORT.md`에 그대로 보존한다. 이 문서는 Codex가 수행한 자동 검증만 기록한다. 앱 기능과 SDK 의존성은 변경하지 않는다.

## 목표와 검증 순서

1. Git·빌드 환경과 기존 차단 지점을 확인한다.
2. 프로젝트 전용 Gradle User Home에서 쓰기·파일 잠금·Wrapper 다운로드를 검증한다.
3. APK 빌드와 기존 단위 테스트를 실행한다.
4. 성공하면 환경을 복원하는 반복 실행 스크립트를 만들고 검증한다.
5. 빌드 성공 후에만 Pleos 대상 확인·APK 설치·MainActivity 실행·화면·크래시를 검증한다.
6. 최종 결과와 표준 명령을 기록한다. 커밋·Push·캐시 삭제·전역 설정 변경은 하지 않는다.

## 초기 확인

- 브랜치 `main`, HEAD `40994529689d44280b01011a228cd36267fba31a`; 시작 시 작업 트리 깨끗함.
- Wrapper 9.6.0, AGP 9.5.1, min/target/compile SDK 28/37/37 유지.
- JBR 25.0.4 사용, daemon JVM criteria 25 유지. 별도 Kotlin Android 플러그인 없이 AGP 내장 Kotlin 사용.
- Android SDK는 기존 `local.properties` 사용. API 34와 37.0, Build Tools 36.0.0 설치 확인. 개인 PC의 실제 절대경로는 이 문서에 기록하지 않는다.
- 초기 프로세스의 `GRADLE_USER_HOME`, `JAVA_HOME`은 미설정 상태였다. 기본 캐시는 사용자 홈의 `.gradle`이다.
- ADB 최초 조회: `emulator-5554`, `Connect_on_x86_64_emulator` online. 이 결과만으로 새 APK 실행 성공을 판단하지 않는다.

## 최초 실패 원인과 격리

Phase 0 로그에서 Wrapper가 사용자 홈 `.gradle/wrapper/dists/.../gradle-9.6.0-bin.zip.lck`를 열 때 `AccessDeniedException`이 발생했다. 프로젝트 구성·의존성 해석 이전의 외부 캐시 쓰기 제한이었다. Android Studio 수동 빌드 성공과 모순되지 않는다.

이번에는 현재 프로세스에 한해 `GRADLE_USER_HOME=<PROJECT>/.gradle/codex-user-home`을 설정했다. 외부 캐시를 복사·삭제·수정하거나 전역 환경변수·권한을 바꾸지 않았다. `.gradle/`은 기존 `.gitignore`에 의해 무시되므로 ignore 수정은 필요 없다.

- 프로젝트 내부 쓰기와 배타적 파일 잠금/해제를 검증했다.
- 기존 Wrapper의 `--version`으로 Gradle 9.6.0 배포본을 100% 다운로드·추출하고 실행했다. 기존 SHA-256 설정도 유지했다.
- Wrapper 출력의 Kotlin 2.3.21은 Gradle DSL용 내장 Kotlin 버전이며, 앱 Kotlin compiler 버전으로 혼동하지 않는다.

## 추가로 확인한 차단 지점과 최종 해결책

초기 `assembleDebug`는 성공했으나 서명·Kotlin 작업 일부가 기존 Android Studio 산출물을 재사용했다. 단위 테스트의 첫 Kotlin 컴파일에서는 사용자 홈의 로컬 Kotlin daemon marker 쓰기가 거부되어 fallback 컴파일이 발생했다. 이어 `--rerun-tasks`로 모든 작업을 실행하자 `:app:validateSigningDebug`가 사용자 홈 `.android`의 debug keystore 쓰기 검사에서 실패했다. 이 실패는 네트워크·JDK 버전·Android SDK 버전 문제가 아니며, 첫 증분 빌드 성공만으로 완전한 격리를 검증할 수 없음을 보여준다.

최종 `scripts/build-codex.ps1`은 다음을 실행 중에만 적용하고 `finally`에서 호출자의 환경과 작업 디렉터리를 복원한다.

| 설정 | 적용값 / 목적 |
| --- | --- |
| GRADLE_USER_HOME | `<PROJECT>/.gradle/codex-user-home` |
| ANDROID_USER_HOME | `<PROJECT>/.gradle/codex-user-home/android-user-home`; Android 설정/metrics/debug 서명 검사의 쓰기 경로 격리 |
| JAVA_HOME | `-JdkHome`, 기존 JAVA_HOME, 표준 Android Studio JBR 위치, PATH의 java 순으로 설치된 JDK 탐색 |
| Kotlin 실행 | `-Pkotlin.compiler.execution.strategy=in-process`; 외부 Kotlin daemon marker를 사용하지 않음 |
| Gradle daemon | `--no-daemon`; JVM 설정상 생성되는 single-use daemon은 빌드 종료 후 종료 |

[Android 공식 환경변수 문서](https://developer.android.com/tools/variables/)의 `ANDROID_USER_HOME`과 [Kotlin 공식 컴파일 실행 전략](https://kotlinlang.org/docs/compiler-execution-strategy.html)을 사용했다. 앱/프로젝트의 `gradle.properties`, 빌드 파일과 버전은 수정하지 않았다. 초기 검증에서 생성한 Gradle daemon 1개도 이 프로젝트 전용 User Home을 대상으로만 종료했다.

기존 사용자 Android 설정 경로에 debug keystore가 있으면 **내용을 출력하지 않고** 무시되는 프로젝트 전용 Android User Home에 최초 한 번 복사한다. 원본과 로컬 사본의 일치도 확인했다. 원본·기존 로컬 키를 덮어쓰거나 삭제하지 않는다. 이는 설치된 앱의 서명과 데이터 보존을 위한 조치다. 키 파일은 기존 `.gradle/` 및 `*.keystore` ignore 규칙의 대상이며 Git에 추가하지 않았다. 원본 키가 없으면 Android 도구가 로컬 debug 키를 생성하므로 다른 키로 설치된 앱과의 갱신 호환성은 별도 확인해야 한다. 서명 불일치 시 앱을 자동 삭제하지 않는다.

## 실행 명령과 결과

| 단계 | 명령 / 옵션 | 실제 결과 |
| --- | --- | --- |
| Wrapper | 로컬 User Home과 JBR을 적용한 `gradlew.bat --version` | 9.6.0 다운로드·실행 성공 |
| 첫 APK 빌드 | `gradlew.bat :app:assembleDebug --console=plain` | 성공, exit 0, 34개 중 7개 실행 / 27개 재사용 |
| 첫 단위 테스트 | `gradlew.bat :app:testDebugUnitTest --console=plain` | 성공, exit 0; Kotlin daemon 오류 후 fallback 발생 |
| 스크립트 초기 검증 | `scripts/build-codex.ps1 -Test` | 성공, exit 0; 모든 작업이 up-to-date였으므로 새 컴파일 증거로 사용하지 않음 |
| 추가 재컴파일 검사 | `--rerun-tasks --no-daemon -Pkotlin.compiler.execution.strategy=in-process` | `.android` 서명 검사 쓰기 제한으로 실패, exit 1 |
| 최종 격리 재빌드 | `scripts/build-codex.ps1 -Test -RerunTasks` | **BUILD SUCCESSFUL, exit 0, 24초, 41개 작업 모두 실행** |
| 최종 반복 실행 | `app/`에서 스크립트 절대경로로 `-Test` 실행 | **BUILD SUCCESSFUL, exit 0, 6초, configuration cache 재사용 및 호출자 환경/디렉터리 복원 확인** |

최종 재빌드에서는 `validateSigningDebug`, `compileDebugKotlin`, APK 패키징, `compileDebugUnitTestKotlin`, `testDebugUnitTest`를 실제 실행했다. Android User Home 변경에 따라 configuration cache가 자동 갱신되었고 외부 `.android`/Kotlin daemon 오류가 재발하지 않았다. Gradle 배포본, Maven 의존성 및 현재 Android SDK를 통한 빌드에 네트워크·SDK·JDK 차단은 관찰되지 않았다.

단위 테스트 결과: `com.surpride.app.ExampleUnitTest`, **tests=1, failures=0, errors=0, skipped=0**. 기존 템플릿의 `addition_isCorrect` 테스트이며 Vehicle/NaviHelper나 실제 앱 기능 테스트를 의미하지 않는다. 계측 테스트는 이번 Phase에서 실행하지 않았다.

스크립트 계약 검증: `scripts/tests/build-codex.Tests.ps1` 통과. 별도 외부 프로젝트 대신 `.gradle/` 내부 fixture의 wrapper를 사용해 루트 탐색, 공백 포함 경로, 테스트 옵션, 강제 재실행 옵션, Gradle/Android User Home 격리, debug 키 재사용·덮어쓰기 방지, native 종료 코드 37 전달, setup 오류의 종료 코드 1, 성공/실패/미설정 환경과 작업 디렉터리 복원을 확인했다. fixture wrapper 결과는 실제 Gradle 빌드 증거와 구분한다. PowerShell 구문 검사 오류는 0개다.

## Pleos 설치·실제 실행 검증

사용자가 설정한 Pleos Connect v2.1.0 에뮬레이터만 대상으로 했다. ADB로 `emulator-5554`, API 34, `Connect on x86_64 emulator`, Automotive feature, 현재 Android 사용자 `10`을 확인했다. 대상 패키지는 실제 Gradle 설정과 생성된 APK metadata의 `com.surpride.app`으로 확인했다.

최종 격리 재빌드 APK에 대해 수행:

```text
adb -s emulator-5554 install --user 10 -r <PROJECT>/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start --user 10 -W -n com.surpride.app/.MainActivity
adb -s emulator-5554 shell pidof com.surpride.app
```

- 갱신 설치: **Success**. 앱 삭제·데이터 초기화·다른 디바이스 접근 없음.
- 실행: **Status: ok**, **LaunchState: COLD**, MainActivity, TotalTime 403ms / WaitTime 408ms.
- 실행 PID와 운전자 사용자 10의 foreground MainActivity 확인.
- UI hierarchy에서 `SURPRIDE`, `목적지는 몰라도, 가는 길은 즐겁게.` 모두 확인. 화면 캡처도 육안 확인했다.
- 해당 PID의 main/system/crash Logcat에서 캡처한 시작 구간의 FATAL EXCEPTION/Fatal signal/ANR 일치 0개. 장시간 무크래시나 주행 중 UI 허용을 검증한 것은 아니다.

[최종 화면 캡처](evidence/phase0b-screen.png), [설치·실행 및 APK 해시](evidence/phase0b-adb.txt), [앱 PID별 Logcat](evidence/phase0b-logcat.txt).

## 이후 Phase의 표준 빌드 명령

Windows PowerShell에서 실행한다. 루트는 스크립트 위치로 찾으므로 호출자의 현재 디렉터리가 프로젝트 루트가 아니어도 된다.

```powershell
# APK 빌드
.\scripts\build-codex.ps1

# APK 빌드 + 기존 단위 테스트
.\scripts\build-codex.ps1 -Test

# 캐시를 삭제하지 않고 모든 작업을 다시 실행하는 환경 검증
.\scripts\build-codex.ps1 -Test -RerunTasks

# JDK 자동 탐색이 불가능할 때만 실제 설치된 호환 JDK를 지정
.\scripts\build-codex.ps1 -Test -JdkHome '<installed-jdk-directory>'

# 스크립트 자체의 환경 복원/종료 코드 계약 검사
.\scripts\tests\build-codex.Tests.ps1
git diff --check
```

`-Test`를 생략하면 APK 빌드만 수행한다. 스크립트는 native Gradle 실패 코드를 그대로 반환하고 setup 오류에는 1을 반환한다. 배포본과 의존성이 없는 새 캐시의 첫 실행에는 네트워크가 필요하다. SDK는 기존 `local.properties` 또는 Android SDK 환경설정을 사용하며 다른 SDK를 자동 설치하지 않는다.

이후 환경에서 ADB 접근이 차단되면 우회하지 말고 Android Studio에서 같은 debug APK를 Pleos에 실행한다. 수동 ADB 검증 시에도 장치 serial, API/모델, 현재 사용자 ID를 먼저 확인한 뒤 갱신 설치·실행한다. `adb install` Success와 `am start` Status: ok, PID/foreground, 화면을 각각 확인한다.

## 파일·증거 및 보존 확인

생성 파일:

- `scripts/build-codex.ps1`
- `scripts/tests/build-codex.Tests.ps1`
- `docs/phase0/AUTOMATED_BUILD_VALIDATION.md`
- `docs/phase0/evidence/phase0b-assemble.txt`, `phase0b-unit-tests.txt`, `phase0b-script-build.txt`: 초기 성공/경고 기록
- `docs/phase0/evidence/phase0b-recompile.txt`: 추가 서명 검사 실패 기록
- `docs/phase0/evidence/phase0b-isolated-rebuild.txt`: 최종 41개 작업 재실행 성공
- `docs/phase0/evidence/phase0b-repeat.txt`: 최종 설정의 반복 실행·루트 탐색·환경 복원 성공
- `docs/phase0/evidence/phase0b-script-tests.txt`: 스크립트 계약 검사
- `docs/phase0/evidence/phase0b-adb.txt`, `phase0b-logcat.txt`, `phase0b-screen.png`: 최종 APK 실행 증거

로그는 UTF-8로 저장하고 호스트의 사용자 홈·프로젝트 절대경로는 `<USER_HOME>`/`<PROJECT>`로 치환했다. SDK 경로·서명 키·토큰을 로그에 출력하지 않았다. 전체 시스템 Logcat 대신 검증 대상 앱 PID만 저장했다.

기존 `REPORT.md`는 내용과 SHA-256을 유지했다. 앱 소스·Manifest·Gradle/AGP/Kotlin/SDK 버전·Android Studio 설정·원격 저장소를 변경하지 않았다. 종료 시에도 `main`, HEAD `4099452`를 유지하며 이번 파일만 미추적 추가 상태다. staging/commit/push 없음.

## 남은 제약과 최종 판정

**Phase 0B: READY.** 프로젝트 전용 캐시와 Android User Home을 통해 Codex의 전체 재빌드·단위 테스트·Pleos 실제 화면 실행을 모두 검증했다.

외부 사용자 홈에 대한 쓰기 제한 자체는 유지되며, 해결책은 전역 권한 변경이 아닌 호출 단위의 경로 격리다. 전용 캐시와 Android 설정 디렉터리는 유지해야 하며 키를 삭제하면 설치된 앱과 서명이 달라질 수 있다. 다른 JDK/SDK 설치 위치에서는 탐색 설정이 필요할 수 있다. SDK 신규 설치·장시간 실행·주행 UI 정책·Vehicle/NaviHelper 연동·목적지 완전 비공개 가능성은 여전히 별도 검증 대상이다. Phase 0의 `READY_WITH_LIMITATIONS` 및 수동 검증 이력은 원문 그대로 보존한다.
