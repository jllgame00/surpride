# SURPRIDE

**기분과 여유 시간을 입력하면, 목적지를 고르는 부담을 줄이고 도착 순간의 발견을 제안하는 차량용 드라이브 앱입니다.**

2026 현대자동차그룹 차량용 앱 개발 공모전 · **팀명: 압구정현대아파트 / 앱명: 서프라이드 / 서비스 영문 브랜드: SURPRIDE**

Android Automotive / Pleos Connect용 정차 프로토타입입니다. [GitHub 저장소](https://github.com/jllgame00/surpride)

## 서비스 핵심 가치와 주요 기능

- **장소 검색 대신 오늘의 조건에서 출발**: 기분·시간·장소 선호·동행 조건을 선택하거나 한국어 문장으로 입력하고, 해석된 조건을 확인·수정합니다.
- **목적지와 경로를 함께 고려**: 왕복 시간과 취향에 맞는 코스를 제안하고, 도착 순간 목적지를 공개하는 경험을 구성합니다.
- **체험을 다음 추천으로 연결**: 목적지 공개 후 평가를 저장하고 기록을 다시 확인합니다. 이미 체험한 DEMO 코스는 다음 추천에서 제외합니다.

제출 Release APK에서는 **서버·API 키 없이 정차 DEMO 전체 흐름**을 실행할 수 있습니다. 실제 도로 API와 지도·GPS Replay는 별도 개발용 Debug 검증 범위입니다.

## 소개 영상 및 Release APK 다운로드

| 최종 제출물 | 링크 | 내용 |
| --- | --- | --- |
| 소개 영상 | [압구정현대아파트_서프라이드_demo_110s.mp4](압구정현대아파트_서프라이드_demo_110s.mp4) | 110초 · 1920×1080 · 무자막 · 최신 무채색 UI · 실제 Kakao 경로 기반 GPS Replay 포함 |
| Release APK | [압구정현대아파트_서프라이드_release.apk](apk/압구정현대아파트_서프라이드_release.apk) | 기존 Release Keystore로 서명한 정차 DEMO 제출본 · 약 22.24 MiB |

**두 파일 모두 원격 `main`에 업로드되어 있습니다.** 2026-11-15에 실제 파일명·존재 여부와 로컬 파일의 Git blob 해시 일치를 확인했습니다. GitHub 파일 페이지의 **Download raw**로 내려받아 영상을 재생하거나 APK를 설치할 수 있습니다.

영상의 GPS Replay는 실제 Kakao Directions API 경로 위에서 위치·거리·회전 안내·OSM 카메라 추적을 보여 주는 **정차 에뮬레이터의 Debug Preview**입니다. 공식 Host ACTIVE 시연이 아닙니다. 뒤에 이어지는 DEMO 목적지 공개·평가는 별도의 모의 체험이며, Replay 경로의 실제 도착이나 전체 경로 연속 완주를 증명하지 않습니다. 앱 내부 글자와 DEBUG PREVIEW 표시는 남아 있고, 하단 설명 자막은 없습니다.

APK SHA-256:

```text
0728796d2523525e13f49308e40a7a4545ff67c8c223d6eb1117a3ec7cdf6f18
```

## 실제 구현 및 검증 범위

| 기능 | 현재 구현·확인 범위 | 실행 조건 |
| --- | --- | --- |
| 조건 입력 | 기분·시간·장소·동행 선택, 규칙 기반 한국어 조건 추출·검토·부분 수정 | 메인 Release/Debug. 자유 대화형 LLM은 사용하지 않음 |
| DEMO 추천·모의 주행 | 가상 코스 추천 → 모의 주행·도착 → 목적지 공개 → 평가 저장 | 지원되는 정차 Pleos 환경. 실제 장소 방문 인증은 아님 |
| 평가·기록 | DEMO 체험 기록과 LIVE 평가 기록을 분리해 목록·상세 표시 | 제출 Release에서 DEMO 저장·재실행·기록 복원 확인 |
| 실제 경로 조회 | Kakao Directions API 기반 개발용 경로 조회, 목적지·경로 통합 평가 | 메인 Debug + 로컬 프록시·키·단기 인증. 공개 출발지·서울 후보 5곳 사용 |
| OSM 지도·GPS Replay | 실제 Kakao geometry 위 위치 매칭·거리/회전 안내·카메라 추적 | 별도 PoC Debug Preview. 메인 DEMO에서 자동 실행하지 않음 |
| 추천·내비게이션 계약 | 추천·응답 파싱, 재탐색·TTS·복귀·복원·ownership 관련 계약 회귀 | JVM/backend 및 Debug 계측 검사. 공식 Host 활성 안내 E2E와 구분 |

UI는 무채색 밝은/어두운 화면, 큰 글자, 단계별 조건 선택, 자연어 핵심 요약, 5점 평가, 기록 목록·상세를 제공합니다. D-pad 포커스·확대 글자·CarIME 접근성을 보완했습니다. 지도에는 경로의 의미색과 OSM 저작권 표기를 유지합니다. **개발 진단 화면은 제출 Release에 노출되지 않습니다.**

## 현재 제한사항과 후속 개발 계획

| 항목 | 현재 제한 | 후속 개발·검증 계획 |
| --- | --- | --- |
| `HOST_ACTIVE = BLOCKED` | 공식 Host 활성 안내와 정상 종료를 보장하는 E2E 미완료 | Host 연동·종료·복귀를 포함한 연속 검증 |
| `DESTINATION_PRIVACY = PARTIAL` | 공개 전 앱의 이름·좌표 노출은 제한하나 Host·지도·알림·복원 전체의 비공개는 미보장. OSM 도로·POI 라벨이 보일 수 있음 | 공개 전후 노출 경로 점검. 위치·회전·도로 안전 정보는 유지 |
| `STT = PARTIAL` | SDK·제어 연결은 구현했으나 실제 음성의 최종 인식 결과 수신 미검증 | 실제 음성 입력·권한·오류·복구 흐름 검증 |
| 실제 검증 경관 경유지 **0곳** | 서울 후보 5곳과 API 응답은 경관 품질·차량 진입·주차·운영 여부를 보증하지 않음 | 경유지 현장 조사와 진입·주차 정보 검증 |
| Release LIVE 프록시 미지원 | 제출 Release에서는 개발용 HTTP·인증 경로 비활성 | 운영용 HTTPS·사용자 인증·비용 제한·개인정보 정책 설계 후 별도 검증 |
| 실차·상용 배포 미검증 | 검증 범위는 정차 에뮬레이터의 DEMO와 개발용 Preview | 실차 환경·실제 주행·도착 연속 검증 및 배포 적합성 확인 |

`CACHE_POLICY`와 `DATA_RETENTION`도 PARTIAL입니다. 실제 지리 기반 감정 지도와 실도로 방문 인증은 구현 완료 범위에 포함하지 않습니다. JNA 5.13.0의 `x86_64/libjnidispatch.so`에는 기존 16KB ELF 정렬 경고가 남아 있습니다. **APK ZIP 정렬 PASS와 ELF 16KB 페이지 호환성은 별개**이며, 16KB 페이지 환경은 미검증입니다.

## 개발 환경 및 Pleos Connect 버전

| 구분 | 검증 기준 |
| --- | --- |
| 실행 환경 | **Pleos Connect Emulator 2.1.0 / Android Automotive API 34 (Android 14) / Android user10 / x86_64** |
| 정차·화면 | P / 0km/h, 원본 에뮬레이터 **2560×1440**, 기본 density 160 / font 1.0 |
| 제출 영상 | 앱 영역을 발췌·축소해 **1920×1080**으로 편집. 에뮬레이터 원본 해상도와 구분 |
| 확대 UI 검사 | density 200 / font 1.3 및 density 160 / font 2.0 |
| 빌드 환경 | Windows, JDK 25, Gradle wrapper 9.6.0, AGP 9.5.1, compileSdk 37, SDK Build Tools 36.0.0, Java/Kotlin 소스 타깃 11 |
| 앱 설정 | `com.surpride.app`, versionCode 1 / versionName 1.0, minSdk 28 / targetSdk 37. PoC minSdk 29 / targetSdk 34 |
| Pleos SDK | Vehicle 2.3.2 / NaviHelper 3.0.5 / SpeechToText 3.3.1, PoC TextToSpeech 3.3.1 / AndroidX Car App 1.7.0 |
| 서버 개발 환경 | Python 3.13 / FastAPI 개발 전용 프록시 |

실행 OS의 API 34와 빌드 compileSdk 37은 서로 다른 기준입니다. Automotive 기능을 요구하며 일반 스마트폰용 APK가 아닙니다. GPS는 Android 위치 provider를 사용합니다. Pleos Maven 저장소는 `settings.gradle.kts`에 설정되어 있고, 최초 빌드에는 의존성 다운로드를 위한 네트워크가 필요합니다.

## Release APK 설치·DEMO 실행 방법

APK를 위 링크에서 내려받고 프로젝트 루트의 `apk/` 경로에 둡니다. 해시를 확인한 뒤 **지원되는 정차 Pleos AVD와 user10**을 준비합니다. 아래 `emulator-5560`은 Release 검증에 사용한 serial입니다. 다른 시험 환경에서는 준비한 AVD의 실제 serial로 바꾸고 대상을 확인하세요.

```powershell
Get-FileHash -LiteralPath 'apk/압구정현대아파트_서프라이드_release.apk' -Algorithm SHA256
adb -s emulator-5560 shell pm list users
adb -s emulator-5560 install --user 10 -r 'apk/압구정현대아파트_서프라이드_release.apk'
adb -s emulator-5560 shell am start --user 10 -W -n com.surpride.app/.MainActivity
```

기기가 없거나 설치가 실패하면 중단합니다. 기존 Debug 앱과 서명이 충돌하면 **기존 앱 삭제·데이터 초기화 대신 별도 시험 AVD를 사용합니다.** UX 상태가 미확인되거나 주행 제한이 활성화되면 입력이 차단됩니다. `Status: ok`만으로 성공을 판정하지 말고 실제 시작 화면과 정차 상태를 확인하세요.

검증한 DEMO 실행 순서:

1. 시작 화면에서 **드라이브 준비 → 드라이브 조건 선택**.
2. **모험·캠핑 → 근교 나들이·3시간·왕복 → 혼자**를 선택하고 **비밀 코스 준비**.
3. **가상 코스 체험 시작 → 다음 예시 → 도착 시뮬레이션 → 도착한 목적지 공개**.
4. **4점 → 평가와 체험 기록 저장** 후 앱을 재실행하고 **기분 기록**에서 복원을 확인.

검증 샘플은 가상 코스 ‘별빛 캠프’, 예시 총 57분입니다. 반복 시연에서 이미 체험한 코스가 제외되면 장소 제한을 해제하거나 다른 조건을 선택합니다. 기존 기록을 지울 필요는 없습니다. 서버·Kakao 키·별도 PoC APK 없이 이 흐름을 실행할 수 있습니다.

## 소스 빌드 및 개발자용 API 설정

프로젝트 루트에서 실행합니다. SDK 경로는 Git에 올리지 않는 `local.properties` 또는 로컬 Android 환경에 설정합니다.

```powershell
.\scripts\build-codex.ps1 -Test
.\scripts\build-nav-host-poc.ps1 -Test
```

스크립트는 프로젝트 내부 `.gradle/codex-user-home`과 Android home을 사용합니다. JDK를 찾지 못하면 `-JdkHome`으로 지정합니다. 메인 Debug APK는 `app/build/outputs/apk/debug/app-debug.apk`, PoC Debug APK는 `nav-host-poc/build/outputs/apk/debug/nav-host-poc-debug.apk`에 생성됩니다. 제출 APK 재현에는 별도의 Release 빌드·기존 키 서명이 필요하며, 서명키·비밀번호는 저장소에 포함하지 않습니다.

실제 Kakao 경로 조회는 **개발용 Debug에서만** 사용합니다. [backend 실행 안내](backend/README.md)와 [환경변수 예시](backend/.env.example)를 참고하되 기존 가상환경·자격 파일은 보존합니다.

```powershell
if (-not (Test-Path backend/.venv)) { python -m venv --without-pip backend/.venv }
python -m pip --python backend/.venv/Scripts/python.exe install -r backend/requirements.txt
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
if (-not (Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
# 개인 편집기에서 backend/.env의 KAKAO_REST_API_KEY를 설정합니다.
backend/.venv/Scripts/python.exe -m backend.dev serve --mode live
```

별도 터미널에서, 준비한 정차 시험 AVD를 명시해 Debug APK를 설치·연결합니다. 아래 serial도 환경에 맞춰 바꿉니다. Release와 서명이 충돌하면 별도 AVD를 사용합니다.

```powershell
adb -s emulator-5556 install --user 10 -r app/build/outputs/apk/debug/app-debug.apk
backend/.venv/Scripts/python.exe -m backend.dev provision-android --adb adb --serial emulator-5556
adb -s emulator-5556 shell am start --user 10 -W -n com.surpride.app/.MainActivity
```

Debug에서 **화면 표시 → 개발 도구 표시 → 실제 장소·경로 검증**으로 진입하고, 조건·시간을 설정한 뒤 **서버 프록시로 경로 평가**를 실행합니다. 응답 출처 `LIVE_API`/`MOCK`을 구분하며 API 오류를 MOCK 성공으로 대체하지 않습니다. 공개 시험 출발지와 진입·주차 `UNKNOWN` 표기를 유지합니다.

Kakao 키는 서버에만 두고 단기 proxy token은 `run-as` stdin으로 전달합니다. 서버는 `127.0.0.1:8765`, 에뮬레이터는 `10.0.2.2:8765`를 사용합니다. **개발용 loopback 서버를 인터넷에 공개하거나 실제 사용자 위치 전송에 사용하지 않습니다.** 키·토큰·서명키·원시 위치 로그·개인정보를 문서, 명령 인수, Git에 넣지 않습니다. 서버 재시작 후에는 다시 프로비저닝하고, 시험 종료 시 `backend/.venv/Scripts/python.exe -m backend.dev stop`으로 종료합니다. Release는 개발용 프록시 설정을 읽지 않습니다.

GPS Replay는 별도 PoC Debug 시험입니다. `backend.nav_poc --request-public-route`는 외부 API 호출과 private 경로 파일 쓰기를, `backend.active_replay`는 공식 에뮬레이터 GPS 변경을 수행합니다. 각 도구에 `--serial`을 명시하고 대상·기존 데이터·GPS 복원 절차를 확인한 뒤 실행합니다. 대상 오류 시 다른 기기로 전환하지 않습니다. Debug 전용 `com.surpride.navhostpoc/.ReplayPreviewActivity`의 **Preview 종료**로 종료하며, 도구 이름의 `active_replay`는 Host ACTIVE 성공을 의미하지 않습니다. 공식 Host 활성 안내 시연은 현재 지원하지 않습니다.

## 기술 검증 결과

아래는 **2026-11-15 Release hotfix 검증 기록** 기준입니다. README 정리 과정에서 테스트·에뮬레이터 실행을 새로 수행한 결과가 아닙니다.

| 검증 항목 | 최신 결과 | 해석 범위 |
| --- | --- | --- |
| JVM/backend | 앱 JVM 234 + PoC JVM 309 + backend 136 = **679 PASS**, 실패·오류·skip 0 | 추천·내비게이션 계약 등을 포함한 해당 hotfix 회귀 |
| Android 계측 | **고유 42 PASS**, 실패·무시 0 | 격리 AVD의 Debug 검사. 중단 후 재개한 완료 43회 중 중복 1회를 제외한 수치 |
| 서명·정렬·설치 | 기존 Release 인증서 일치, **apksigner v2 PASS / ZIP 정렬 PASS / API 34·user10 설치 PASS** | 현재 제출 APK와 실제 설치본의 SHA-256 일치 |
| 서명 Release 실제 정차 DEMO | **전체 흐름 PASS**, 평가 저장·재실행·기록 복원 PASS, 개발 진단 화면 비노출 | 검증 구간 **Crash 0건 / ANR 0건**. 실차·상용 배포 검증은 포함하지 않음 |
| 빌드·정적 검사 | 빌드 PASS, Lint 오류 0 / 경고 49, DEX 접근 검사 회귀 PASS | 기존 실패 APK 거부·수정 APK 통과. ELF 16KB 경고는 위 제한사항 참조 |
| 제출 APK 보안 검사 | 설정된 키·대표 비밀 패턴·Git 작성자 이메일·키 파일 검출 0 | 검사한 값과 패턴 범위의 결과 |

시작 시 `IllegalAccessError`는 Kotlin facade를 최적화 범위에서 제외하는 hotfix로 완화했고 AndroidX·kotlinx 최적화는 유지했습니다. 컴파일러 자체를 수정했다고 주장하지 않습니다. [최신 hotfix 검증 보고서](docs/submission/2026-hmg/evidence/release-startup-hotfix-20261115.json)는 파일명 변경·업로드 전에 작성되어 팀명·파일명·Git 상태 메타데이터가 과거 값입니다. 현재 제출 정보는 이 README 상단과 원격 `main`을 기준으로 합니다.

과거 **Phase 7C의 고유 45개 계측 검사**는 다른 시점의 근거이며 위 42개와 합산하지 않습니다. GPS Replay 부분 검사와 반복 실행도 최신 총계에 더하지 않습니다. 이 검사들은 공식 Host ACTIVE나 실차 안전성을 증명하지 않습니다.

## 프로젝트 구조

```text
app/                         정차 UI, 조건 해석, 추천, DEMO, 평가·기록
nav-host-poc/                독립 Host 연동 실험, OSM 지도·GPS·안내 Preview
backend/                     FastAPI 개발용 경로 프록시, 공개 시험 Replay 도구
scripts/                     PowerShell 빌드 도구, Release DEX 접근 검사
docs/submission/2026-hmg/     제출 관련 문서와 시점별 검증 근거
apk/                         최종 서명 Release APK
압구정현대아파트_서프라이드_demo_110s.mp4  최종 소개 영상
```

과거 제출 체크리스트·영상·개발 이력은 당시 기록입니다. 현재 제출물은 상단의 APK·영상 두 파일이며, 로컬 전용 캡처·제작 자료·Git 제외 문서는 다운로드 링크로 제공하지 않습니다.
