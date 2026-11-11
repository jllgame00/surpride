# SURPRIDE

기분과 시간에서 출발해, 도착 순간의 발견으로 이어지는 차량용 드라이브 앱입니다.

Android Automotive / Pleos Connect용 정차 프로토타입입니다. 기분·시간·풍경 선호·동행 조건을 확인하고, 도로 경로와 목적지를 함께 평가합니다. 목적지 공개와 평가·체험 기록까지 연결하는 경험을 개발하고 있습니다.

## 현재 실행 범위

| 기능 | 범위 |
|---|---|
| 정차 입력·체험 | 기분/시간/취향 설정, 규칙 기반 자연어 해석·부분 수정, DEMO 코스·모의 도착·공개·평가 |
| 실제 데이터 | 개발용 공개 출발지·서울 후보 5곳, Kakao Directions 프록시 연동, 경로·목적지 통합 평가, LIVE 평가 기반 최근 후보 제외 |
| 내비게이션 PoC | OSM·GPS matching·회전 안내·재탐색·TTS·복귀·복원 코드 및 계약 회귀. 별도 APK/공식 Host 필요 |
| 기록 | DEMO 기분 개념도와 LIVE 평가 기록을 분리. 실제 지리 기반 감정 지도·실도로 방문 인증은 아님 |

**HOST_ACTIVE=BLOCKED.** 공식 Host에서 정상 종료 조작을 보장하는 활성 안내 E2E는 완료되지 않았습니다. DESTINATION_PRIVACY / STT / CACHE_POLICY / DATA_RETENTION은 PARTIAL입니다. 실제 검증 경관 경유지는 0곳이며, 자유 대화형 LLM·실차·상용 운영은 구현 완료로 주장하지 않습니다. 위치·회전·도로 안전 정보를 숨기는 방식으로 비공개를 구현하지 않습니다.

## 구조

```text
app/             정차 UI, 추천, 자연어 조건, 기록, PoC private handoff
nav-host-poc/    독립 AndroidX Car App Host 실험, 지도/GPS/안내/ownership
backend/         FastAPI 개발 전용 loopback 인증 프록시, 공개 시험 replay 도구
scripts/         workspace Gradle/JDK 설정을 사용하는 PowerShell 빌드 도구
docs/submission/2026-hmg/  예선 자료·110초 소개 편집본·제출 보류 사유
```

## 빌드 환경

현재 저장소 검증 환경은 Windows, **JDK 25**, Gradle wrapper **9.6.0**, AGP **9.5.1**, Android compileSdk **37**, SDK Build Tools **36.0.0**입니다. Java/Kotlin 소스 타깃은 11입니다. 메인 minSdk28/target37, PoC minSdk29/target34이며 Automotive 기능을 요구합니다. 일반 스마트폰용 APK가 아닙니다.

Pleos Maven 저장소는 `settings.gradle.kts`에 설정되어 있습니다. Vehicle2.3.2 / NaviHelper3.0.5 / SpeechToText3.3.1, PoC TextToSpeech3.3.1 / AndroidX Car App1.7.0을 사용합니다. 실제 GPS 구현은 Android 위치 provider이며 FusedLocation SDK를 사용했다고 주장하지 않습니다. SDK API/권한과 지원 에뮬레이터는 [공식 개발 환경 안내](https://appdevchallenge.auto-hmg.io/guide/development)를 확인하세요. 최초 빌드에는 공식 저장소의 의존성을 내려받을 네트워크가 필요합니다.

```powershell
# SDK 경로는 공개하지 않는 local.properties 또는 로컬 Android 환경에 설정합니다.
.\scripts\build-codex.ps1 -Test
.\scripts\build-nav-host-poc.ps1 -Test
```

스크립트는 프로젝트 안 `.gradle/codex-user-home`과 Android home을 사용합니다. 적합한 JDK를 찾지 못하면 `-JdkHome`을 명시하세요. 서명 키·SDK 설치 경로·비밀 환경파일은 저장소에 추가하지 않습니다.

## 서버와 실제 API

Python3.13 개발 환경을 사용합니다. 기존 가상환경과 자격 파일을 덮어쓰지 마세요. [backend 실행 안내](backend/README.md)에 설치·공개 시험 좌표·오류 정책이 있습니다.

```powershell
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
# backend/.env는 .env.example을 참고해 개인 환경에서 설정합니다. 실제 키를 문서/argv에 적지 않습니다.
backend/.venv/Scripts/python.exe -m backend.dev serve --mode live
# 별도 터미널. 아래 serial은 준비한 정차 시험 AVD에 맞춰 직접 지정합니다.
backend/.venv/Scripts/python.exe -m backend.dev provision-android --adb adb --serial emulator-5556
```

Kakao 키는 서버에만 두고 단기 proxy token은 `run-as` stdin으로 전달합니다. 서버는 `127.0.0.1:8765`, 에뮬레이터는 `10.0.2.2:8765`를 사용합니다. 인터넷 공개 서버가 아닙니다. serial 생략은 기존 고정5554이며 자동 기기 선택이 아닙니다. 없는/실패한 대상에서 다른 기기로 전환하지 않습니다. provisioning·API 요청·GPS replay는 각각 별도 개발 동작이므로 대상 확인 없이 실행하지 마세요.

**Release는 ProxyConfiguration이 null이며 이 개발용 HTTP/인증 경로를 사용할 수 없습니다.** 운영 프록시를 배포하거나 키를 APK에 넣어 우회하지 않습니다.

## 설치와 시연

Debug APK는 `app/build/outputs/apk/debug/app-debug.apk`, PoC는 `nav-host-poc/build/outputs/apk/debug/nav-host-poc-debug.apk`입니다. 지원되는 **정차 Pleos AVD / user10 / 명시 serial**을 준비한 개발자가 기존 서명과 데이터를 확인한 경우에만 설치합니다. 자동 설치 스크립트나 자동 안내 시작은 없습니다.

```powershell
adb -s emulator-5556 install --user 10 -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 shell am start --user 10 -W -n com.surpride.app/.MainActivity
```

위 serial은 예시입니다. 기기가 없으면 멈추세요. 다른 기기 선택·데이터 초기화·서명 충돌을 해결하려는 기존 앱 삭제를 하지 않습니다. UX 제한 상태가 미확인되면 조작이 차단될 수 있습니다. 메인 앱만으로 정차 DEMO·조건 입력·개념 기록을 볼 수 있고, 실제 API는 debug 프록시 설정이 필요합니다. LIVE 지도 안내는 별도 PoC와 Host가 필요하며 **현재 ACTIVE 시연 금지**입니다.

## Release 제출 상태

**APK 제출은 NOT_READY / 보류입니다.** Phase6C에서 메인 Release 조립은 성공했으나 `app-release-unsigned.apk`는 미서명입니다. PoC Release는 Manifest의 CarAppActivity에 대한 Lint `Instantiatable` 오류로 중단됐습니다. Release 서명·실제 실행·프록시/Host 의존성 검증을 완료하기 전에는 debug APK를 release로 바꾸어 제출하지 않습니다. 세부 근거와 심사자 설치 조건은 [GitHub·Release 준비도](docs/submission/2026-hmg/GITHUB_RELEASE_READINESS.md)를 확인하세요.

## 검증과 예선 자료

최근 B6 기록은 JVM/서버 **675 PASS**(앱231 + PoC308 + Python136), 별도로 B5 native 계측31 PASS 이력입니다. 전체 목록706을 이번 단계에서 모두 재실행했다는 뜻이 아닙니다. **Phase6C에서는 제품/테스트/빌드 설정을 변경하지 않아 기존 검사를 재실행하지 않았고 Release 조립만 새로 시도했습니다.**

- [예선 체크리스트](docs/submission/2026-hmg/PRELIMINARY_SUBMISSION_CHECKLIST.md)
- [기획서 본문 제안·구현 대조](docs/submission/2026-hmg/PROPOSAL_IMPLEMENTATION_REVIEW.md)
- [110초 구성·증거 매핑](docs/submission/2026-hmg/DEMO_STORYBOARD.md)
- [로컬 소개 편집본 MP4](docs/submission/2026-hmg/assets/SURPRIDE_preliminary_prototype_110s.mp4) / [자막](docs/submission/2026-hmg/assets/SURPRIDE_preliminary_prototype_110s.srt)
- [최종 제출 목록·보류 항목](docs/submission/2026-hmg/FINAL_SUBMISSION_MANIFEST.md)

영상은 기존 정차 캡처를 편집한 무음 프로토타입 소개이며 연속 주행·현재 활성 Host 성공 영상이 아닙니다. **외부 제출 영상 URL은 아직 없습니다.** 로컬 파일 검토 후 별도로 게시·접근 확인해야 합니다. 서명한 접수양식·팀원 연락처·개인정보 동의서는 이 공개 저장소에 넣지 않습니다.
