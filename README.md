# SURPRIDE

**기분과 여유 시간을 입력하면, 목적지를 고르는 부담을 줄이고 도착 순간의 발견을 제안하는 차량용 드라이브 앱입니다.**

Android Automotive / Pleos Connect용 정차 프로토타입입니다. 장소 검색 대신 **기분·시간·풍경 선호·동행 조건**에서 시작하고, **목적지와 도로 경로를 함께 평가**합니다. 왕복 시간 조건, 도착 후 목적지 공개·평가, 다음 추천에 반영하는 기록으로 짧은 드라이브 경험을 연결합니다. 목적지 공개·평가는 현재 DEMO와 개별 LIVE 결과 화면 검증 범위이며, 실제 주행 전체 흐름의 완성을 뜻하지 않습니다.

심사 자료: [서명 Release APK](apk/압구정현대아파트_서프라이드_release.apk) · [최신 무자막 110초 소개 영상](압구정현대아파트_서프라이드_demo_110s.mp4) · [실제 경로 GPS Replay](docs/submission/2026-hmg/assets/SURPRIDE_live_route_replay.mp4). **정차 DEMO용 Release APK는 서명·설치·전체 체험·기록 복원 검증 완료입니다.**

## 최상위 제출 파일

| 항목             | 위치·상태                                                                                                                                                   |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 최신 무자막 영상 | [압구정현대아파트\_서프라이드\_demo_110s.mp4](압구정현대아파트_서프라이드_demo_110s.mp4) — 루트에 배치 완료, 아직 미커밋·미업로드                           |
| APK 제출 폴더    | [apk/](apk/) — 검증된 signed Release APK와 기존 `.gitkeep` 보존                                                                                             |
| 최종 APK         | [apk/압구정현대아파트\_서프라이드\_release.apk](apk/압구정현대아파트_서프라이드_release.apk) — 사용자 확인 최종 팀명 **SURPRIDE**, 정차 DEMO 실행 검증 완료 |

루트 영상은 기존 assets 무자막 원본을 재인코딩 없이 복사했습니다. **110.000초 / 1920×1080 / H.264 / 30fps / 3,300프레임 / 무음 / 3,387,657 bytes (약 3.23 MiB)**입니다. 자막 스트림0개, 전체 디코딩 오류0개, 모든 프레임의 하단 자막 영역이 비어 있음을 재확인했습니다. 앱 내부 글자·장면 제목·DEBUG PREVIEW와 검증 제한 표시는 유지한 **하단 설명 자막 없는 버전**입니다. 자막 포함 v2로 대체하지 않았습니다.

원본·루트 사본의 SHA-256은 동일합니다.

```text
cb7a072b307ccdf85eb125e6b6711769faed22615a0037d2bcab098ce66a85cb
```

GitHub 반영 후 위 영상 파일 페이지에서 **Download raw**로 내려받아 MP4 플레이어로 재생할 수 있습니다. 현재 링크는 로컬 파일 존재를 검증한 상대 경로이며 원격 업로드 완료를 뜻하지 않습니다. assets의 동일 무자막 원본과 제작 자료는 보존하고, 동일 MP4의 중복 Git 추적을 피하도록 **assets 원본만 `.gitignore`에 지정**했습니다.

## 현재 실행 범위

| 기능                      | 구현·확인 범위                                                                                                       | 실행 조건·남은 검증                                                                                              |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| 기분 기반 추천·DEMO       | 기분/시간/취향/동행 설정, 가상 코스 추천, 모의 도착·공개·평가                                                        | 메인 Release/Debug 단독, 지원되는 정차 환경. 제출 Release의 저장·재시작·기록 복원 검증 완료. 실제 장소 방문 아님 |
| 자연어 입력               | 한국어 문장에서 규칙 기반으로 조건 추출·검토·부분 수정                                                               | 자유 대화형 LLM 아님. **STT PARTIAL**: SDK·제어 연결은 구현했으나 실제 음성의 최종 인식 결과 수신 검증은 미완료  |
| 실제 Kakao 경로·LIVE 추천 | 개발용 공개 출발지·서울 후보 5곳, Kakao Directions 프록시 연동, 경로·목적지 통합 평가, LIVE 평가 기반 최근 후보 제외 | Debug + 로컬 프록시·키·단기 인증 필요. API 오류를 MOCK 성공으로 대체하지 않음                                    |
| OSM 지도·GPS Replay       | 실제 Kakao geometry 위 GPS matching·거리/회전 안내·카메라 추적을 정차 에뮬레이터에서 촬영                            | 별도 PoC Debug Preview. 공식 Host 활성 안내·실차 주행과 구분                                                     |
| 내비게이션 PoC            | 재탐색·TTS·복귀·복원·ownership 코드 및 계약 회귀                                                                     | 별도 APK/공식 Host 필요. **HOST_ACTIVE BLOCKED**, 현재 ACTIVE 시연 불가                                          |
| 기록                      | DEMO 체험 기록과 LIVE 평가 기록을 분리해 목록·상세 표시                                                              | 실제 지리 기반 감정 지도·실도로 방문 인증은 아님                                                                 |

**Phase 7B/7C 최신 UI**는 무채색 밝은/어두운 화면, 큰 글자와 명확한 주 행동, 단계별 조건 선택, 자연어 핵심 요약, 체크 상태가 구분되는 5점 평가, 기록 목록·상세를 적용했습니다. 조건 하단 버튼 간격·D-pad 포커스·확대 글자·CarIME 접근성을 보완했습니다. 개발 진단은 **Debug에서만 ‘화면 표시 → 개발 도구 표시’**로 켤 수 있으며 제출 Release에서는 표시되지 않습니다. 지도에는 도로·경로의 의미색과 OSM 저작권 표기를 유지합니다.

현재 검증 경계는 다음과 같습니다.

- **HOST_ACTIVE = BLOCKED**: 공식 Host의 활성 안내와 정상 종료 조작을 보장하는 E2E가 미완료입니다. debug Preview의 종료 성공과 별개입니다.
- **DESTINATION_PRIVACY = PARTIAL**: 공개 전 앱의 목적지 이름·좌표 노출을 제한하지만 Host·지도·알림·복원 전체의 비공개를 보증하지 않습니다. OSM 원본 도로/POI 라벨이 보일 수 있으며, 위치·회전·도로 안전 정보는 유지합니다.
- **STT / CACHE_POLICY / DATA_RETENTION = PARTIAL**: 코드·계약 검증과 실제 음성/플랫폼 전체 검증을 구분합니다.
- **실제 검증된 경관 경유지 0곳**: 서울 후보 5곳과 실제 도로 API 응답이 검증된 경관 코스를 뜻하지 않습니다. 경관 품질·차량 진입·주차·운영 여부의 현장 확인은 남아 있습니다.
- **실차·상용 배포 미검증**: 서명 Release의 정차 DEMO는 검증했습니다. 운영 서버, 실제 도로 주행·도착 연속 검증, 실차·상용 배포는 남아 있습니다.

## 구조

```text
app/             정차 UI, 추천, 자연어 조건, 기록, PoC private handoff
nav-host-poc/    독립 AndroidX Car App Host 실험, 지도/GPS/안내/ownership
backend/         FastAPI 개발 전용 loopback 인증 프록시, 공개 시험 replay 도구
scripts/         workspace Gradle/JDK 설정을 사용하는 PowerShell 빌드 도구
docs/submission/2026-hmg/  예선 자료·110초 v2·GPS Replay·제출 보류 근거
```

## 실행 환경과 빌드

실행·UI 검증 기준은 **Pleos Connect Emulator 2.1.0 / Android 14 (API 34) / x86_64 / Android 사용자 10**입니다. **제출 영상과 GPS Replay에서 실제 검증한 녹화 화면 규격은 1920×1080**입니다. 촬영 당시 에뮬레이터의 원본 화면은 2560×1440, 기본 density160/font1.0이며 OEM 계기·시스템 영역과 앱 영역이 함께 표시됩니다. 제출 영상은 앱 영역을 발췌·축소해 1920×1080으로 구성했습니다. 에뮬레이터 자체를 1920×1080으로 설정해 UI 회귀를 수행했다는 뜻은 아닙니다. 정차 **P / 0km/h**에서 검사했고 density200/font1.3 및 density160/font2.0 확대 환경도 확인했습니다.

빌드 검증 환경은 Windows, **JDK 25**, Gradle wrapper **9.6.0**, AGP **9.5.1**, Android compileSdk **37**, SDK Build Tools **36.0.0**입니다. 실행 OS의 API34와 빌드 compileSdk37은 별개입니다. Java/Kotlin 소스 타깃은 11입니다. 메인 minSdk28/target37, PoC minSdk29/target34이며 Automotive 기능을 요구합니다. 일반 스마트폰용 APK가 아닙니다.

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

## Debug 설치와 시연

Debug APK는 `app/build/outputs/apk/debug/app-debug.apk`, PoC는 `nav-host-poc/build/outputs/apk/debug/nav-host-poc-debug.apk`입니다. 지원되는 **정차 Pleos AVD / user10 / 명시 serial**을 준비한 개발자가 기존 서명과 데이터를 확인한 경우에만 설치합니다. 자동 설치 스크립트나 자동 안내 시작은 없습니다.

```powershell
adb -s emulator-5556 install --user 10 -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 shell am start --user 10 -W -n com.surpride.app/.MainActivity
```

위 serial은 예시입니다. 기기가 없으면 멈추세요. 다른 기기 선택·데이터 초기화·서명 충돌을 해결하려는 기존 앱 삭제를 하지 않습니다. UX 제한 상태가 미확인되면 조작이 차단될 수 있습니다.

| 시연 모드           | 준비                                                                    | 확인할 흐름                                                                                                                               |
| ------------------- | ----------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| DEMO                | 메인 Debug APK, 정차 Pleos AVD. 서버·API 키 불필요                      | 시작 → 기분·조건 입력 → 가상 코스 준비 → 모의 도착 → 목적지 공개·평가 → 기록                                                              |
| LIVE 추천·경로 평가 | 위 설치 + 앞 절의 live 서버·프로비저닝. 개발 도구 표시 켜기             | ‘실제 장소·경로 검증’ → 검증 조건/시간 설정 → ‘서버 프록시로 경로 평가’. LIVE_API/MOCK 출처 확인. 공개 출발지·진입/주차 UNKNOWN 표기 유지 |
| GPS Replay Preview  | 별도 PoC Debug APK + 신선한 공개 시험 경로 + 명시한 에뮬레이터 GPS 입력 | 아래 녹화 자료로 지도·경로·거리·회전 진행 확인. 메인 DEMO에서 자동 시작하지 않음                                                          |

GPS Replay 개발 도구는 `backend.nav_poc --request-public-route`로 공개 시험 경로를 준비하고 `backend.active_replay`로 공식 에뮬레이터 GPS를 입력합니다. 각 명령에 `--serial`을 지정해야 하며, 실제 외부 API 호출·private 경로 파일 변경·GPS 위치 변경을 수행합니다. Preview 진입점은 Debug 전용 `com.surpride.navhostpoc/.ReplayPreviewActivity`이고 화면의 **‘Preview 종료’**로 종료합니다. 이 도구 이름의 `active_replay`는 Host 활성 안내 성공을 의미하지 않습니다. 대상·기존 데이터·GPS 복원 절차를 확인한 별도 개발 시험으로 실행하며, 재현 조건과 종료·복원 증거는 [GPS Replay 보고서](docs/submission/2026-hmg/assets/SURPRIDE_live_route_replay_REPORT.ko.txt)를 참고하세요. 공식 Host를 사용하는 LIVE 활성 지도 안내는 **현재 ACTIVE 시연 금지**입니다.

## Release APK 다운로드 및 실행

**정차 DEMO용 서명 Release APK 준비 완료 — READY_FOR_PARKED_DEMO.** [SURPRIDE\_서프라이드\_release.apk 다운로드](apk/SURPRIDE_서프라이드_release.apk). 사용자가 수정된 코드에 **기존과 동일한 Release Keystore로 재서명**했고, 이 파일을 실제 Pleos에 설치해 전체 DEMO를 검증했습니다. 원본·설치본·제출 사본의 SHA-256이 모두 같습니다. GitHub 업로드·commit·push는 아직 수행하지 않았습니다.

| 항목        | 확인 결과                                                                                                                      |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------ |
| 파일        | `apk/SURPRIDE_서프라이드_release.apk`, **23,325,144 bytes (약22.24 MiB)**                                                      |
| 패키지·버전 | `com.surpride.app`, versionCode1/versionName1.0, minSdk28/target37, Automotive 필수, x86_64 포함                               |
| 서명·정렬   | `apksigner verify` exit0, **v2 PASS**, 기존 Release 인증서 일치·Debug 인증서 아님, `zipalign -c -P 16 4` PASS                  |
| 실제 시험   | 별도 `SURPRIDE_Release_Verification` AVD, **emulator-5560 / Pleos Connect2.1.0 / API34 / user10**, 원본 화면2560×1440          |
| 전체 DEMO   | 시작·무채색 UI·기분/장소/시간 선택·추천·가상 진행·모의 도착·공개·4점 저장·재시작·기록 복원 **PASS**                            |
| 진단·안정성 | 실제 Release의 개발 진단 메뉴 비노출 확인. 검증 구간의 앱 fatal crash0·ANR0, 재시작 후 프로세스 정상 유지                      |
| 지원 범위   | 서버·Kakao 키 없이 정차 DEMO, 규칙 기반 텍스트 조건 정리, DEMO 평가·기록. 실제 장소 방문 인증은 아님                           |
| 기존 제한   | **Release LIVE 프록시 비활성 / HOST_ACTIVE BLOCKED / STT PARTIAL / DESTINATION_PRIVACY PARTIAL** 유지. 실차·상용 배포는 미검증 |

```text
SHA-256  0728796d2523525e13f49308e40a7a4545ff67c8c223d6eb1117a3ec7cdf6f18
```

GitHub 파일 페이지의 **Download raw**로 APK를 내려받고 위 해시를 비교합니다. 지원되는 정차 Pleos AVD와 user10을 준비한 뒤 프로젝트 루트에서 실행합니다. 아래 serial은 이번 검증 대상이며, 다른 환경에서는 준비한 시험 기기의 실제 serial을 명시해야 합니다.

```powershell
adb -s emulator-5560 shell pm list users
adb -s emulator-5560 install --user 10 -r 'apk/SURPRIDE_서프라이드_release.apk'
adb -s emulator-5560 shell am start --user 10 -W -n com.surpride.app/.MainActivity
```

기존 Debug 앱과 서명이 다르면 업데이트가 실패할 수 있습니다. **기존 앱 삭제·데이터 초기화로 해결하지 말고 별도 시험 AVD를 사용하세요.** UX 상태가 미확인되거나 주행 제한이 활성화되면 입력이 차단됩니다. `Status:ok`만으로 실행 성공을 판정하지 않고 실제 시작 화면을 확인합니다.

검증한 체험 순서는 **드라이브 준비 → 드라이브 조건 선택 → 모험·캠핑 → 근교 나들이·3시간·왕복 → 혼자 → 비밀 코스 준비 → 가상 코스 체험 시작 → 다음 예시 → 도착 시뮬레이션 → 도착한 목적지 공개 → 4점 → 평가와 체험 기록 저장 → 앱 재실행 → 기분 기록**입니다. 가상 샘플 ‘별빛 캠프’, 예시 총57분을 확인했습니다. 이미 체험한 코스는 추천에서 제외되므로 반복 시연에서는 장소 제한을 해제하거나 다른 조건을 선택하며, 기록을 지우지 않습니다.

### 시작 크래시 수정과 검증 근거

수정 전 signed Release의 `IllegalAccessError`를 재현하고 동일 mapping으로 retrace했습니다. 최적화된 DEX에서 기본 패키지의 `kg`가 비공개 `kotlin.collections.h`를 상속해 `ProcessLifecycleInitializer.dependencies()` 초기화가 실패했습니다. Kotlin facade를 최적화 범위에서 제외한 최소 수정으로 해결했습니다. **AndroidX·kotlinx 최적화는 유지**하며 앱의 추천·저장·안전 로직, SDK 버전·권한은 변경하지 않았습니다.

| 비교                    | DEX 접근 위반      | 실제 실행                                 |
| ----------------------- | ------------------ | ----------------------------------------- |
| 기존 최적화 ON          | 1건 / 8,512클래스  | 시작 실패 재현                            |
| 진단 최적화 OFF         | 0건 / 12,990클래스 | 시작·정차 입력 정상                       |
| ON + Kotlin만 제외      | 0건 / 9,999클래스  | 진단 Release 전체 DEMO 정상               |
| 사용자 동일 키 재서명본 | 0건 / 9,999클래스  | 실제 설치·전체 DEMO·재시작·기록 복원 정상 |

이는 확인된 컴파일 결과를 피하는 **범위를 좁힌 완화책**입니다. R8 자체 또는 정확한 내부 최적화 패스를 수정했다고 주장하지 않습니다. `scripts/check-release-dex-access.py`는 실제 APK의 접근 불가능한 superclass를 검사하며, 기존 실패 APK를 거부하고 수정 APK를 통과시키는 회귀를 확인했습니다.

- [최신 hotfix·서명·실행·테스트 결과](docs/submission/2026-hmg/evidence/release-startup-hotfix-20261115.json)
- 실제 signed APK의 [시작 화면](docs/submission/2026-hmg/evidence/release-hotfix-20261115/start.png) / [재시작 후 복원된 기록](docs/submission/2026-hmg/evidence/release-hotfix-20261115/restored-history.png) — 실제2560×1440 캡처에서 앱 영역만 발췌, OEM 위치 영역 제외
- [이전 실패 signed APK 검증 이력](docs/submission/2026-hmg/evidence/signed-release-validation-20261115.json) / [이전 unsigned 빌드 검사](docs/submission/2026-hmg/evidence/main-release-validation-20261114.json) — 수정 전 기록으로 보존

Native 구성은 기존 의존성 그대로입니다. JNA5.13.0의 `x86_64/libjnidispatch.so`에 기존 Lint 16KB 정렬 경고가 남아 있습니다. APK ZIP 정렬 통과와 ELF 16KB 호환성은 별개이며, 16KB 페이지·실차 환경은 미검증입니다. 서명키·비밀번호·API 키·원시 위치 로그·개인 연락처는 제출 파일에 포함하지 않습니다.

## 검증과 예선 자료

아래 수치는 **단계별 증거**입니다. 이번 hotfix에서는 JVM 전체를 강제 재실행했고, backend·Android 회귀와 실제 최종 서명 APK의 전체 DEMO를 검증했습니다. 진단용 서명과 최종 Release 서명을 구분합니다.

| 근거                           | 확인된 결과                                                                                                                                           | 해석 범위                                                                                                      |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| 2026-11-15 hotfix 자동 검사    | 앱 JVM234 + PoC JVM309 + backend136 = **679 PASS**, 실패·오류·skip0. Lint 오류0/경고49                                                                | 최적화 범위 수정 후 새로 실행. DEX 회귀는 기존 실패본 거부·수정본 통과 확인                                    |
| 2026-11-15 Android 회귀        | **고유42 PASS**, 완료43회·중복1회, 실패·무시0. 첫 실행은13개 뒤 가상장치 종료로 중단, 복구 후 남은 클래스30개 통과                                    | 격리 AVD의 Debug 계측 검사. 한 번의 전체42 실행이 끝났다고 표시하지 않음. 재시도 전 설정 파일은 해시 일치 복원 |
| 2026-11-15 최종 signed Release | 동일 Release 인증서·v2·정렬·실제 설치·전체 DEMO·저장·재시작·복원 PASS, 검증 구간 앱 crash0/ANR0                                                       | 제출 파일과 실제 설치본의 SHA-256 일치. 실차/활성 Host 검증과 구분                                             |
| 2026-11-15 수정 전 서명 APK    | v2 서명·user10 설치 PASS였으나 시작 시 IllegalAccessError                                                                                             | 수정 전 실패 이력 보존. 현재 제출 APK와 다른 파일                                                              |
| 2026-11-14 메인 Release 준비   | Release 조립·Lint 성공(오류0/경고49), Debug JVM **234 PASS**, ZIP 정렬 PASS, 서명 FAIL                                                                | unsigned 산출물의 정적 검증. signed Release 설치·DEMO 실행은 UNVERIFIED                                        |
| Phase 7C UI 검증               | 앱 JVM **234** + PoC JVM **308** + backend **136** = **678 PASS**                                                                                     | 해당 단계 전체 JVM/서버 검사. 과거 B6의 675를 대체하는 UI 단계 기록                                            |
| Phase 7C 에뮬레이터 계측       | 메인 **42** + PoC **3** = 고유 **45 PASS**                                                                                                            | 기본 환경 검사. 확대 환경은 각각17개, 합계 **34회 반복**으로 고유 수에 더하지 않음                             |
| Phase 7C 기타                  | 빌드 스크립트 **4검사 그룹 PASS**, Debug 제품·테스트 APK 빌드/설치 해시 확인                                                                          | 서명 Release 실행·실차 검증을 뜻하지 않음                                                                      |
| 2026-11-13 GPS Replay          | backend replay/guidance/serial **55 PASS**, Preview·지도·추적·안내 **83 PASS**, PoC Debug 빌드 성공                                                   | 관련 검사 부분 실행. 위 전체 수에 합산하지 않음                                                                |
| 110초 v2 영상                  | 자막 포함판: 자막16개·장면 출처 대조. 루트 무자막판: 110.000초/3,300프레임, 전체 디코딩 오류0, 자막 스트림0·하단 자막 없음·원본과 SHA-256 일치 재확인 | 영상 제작/파일 검증이며 제품 E2E 검사 수에 포함하지 않음                                                       |

Phase 7C 집계 출처는 로컬 `docs/phase7c/FINAL_UI_POLISH.md`와 `docs/phase7c/evidence/validation-summary.json`입니다. `docs/phase*/`는 현재 Git 제외 대상이므로 GitHub에서 이 원본을 열람할 수 있다고 가정하지 않습니다. GPS Replay와 v2의 공개 예정 근거는 아래 보고서·출처 원장에 있습니다. **전체 목록706을 최신 코드로 모두 통과했다는 표현은 사용하지 않습니다.**

최신 소개 영상은 **Phase 7B/7C 무채색 UI + 실제 GPS Replay 22초 발췌 + 별도 DEMO 공개·평가**로 구성한 110초 무음 편집본입니다. GPS Replay 원본은 약 **55초**, 공개 광화문광장→이촌한강공원 Kakao 경로의 **0→779m 구간**을 재현한 정차 debug Preview입니다. 회전 전 거리 감소·우회전 통과·다음 안내 전환·OSM 카메라 추적을 확인했습니다. 이후 DEMO 공개·평가는 이 실제 경로의 도착 증거가 아닙니다. 두 영상 모두 공식 Host ACTIVE·실제 음성 안내·전체 경로 연속 완주·실차 검증 영상은 아닙니다.

- [최신 무자막 110초 v2 MP4 — 루트 제출본](SURPRIDE_서프라이드_demo_110s.mp4)
- [참고용 자막 포함 v2 MP4](docs/submission/2026-hmg/assets/SURPRIDE_preliminary_prototype_110s_v2.mp4) / [한국어 SRT](docs/submission/2026-hmg/assets/SURPRIDE_preliminary_prototype_110s_v2.srt)
- [v2 제작·검증 보고서](docs/submission/2026-hmg/assets/SURPRIDE_preliminary_prototype_110s_v2_REPORT.ko.txt) / [장면별 출처](docs/submission/2026-hmg/assets/v2_evidence_20261113/provenance.json) / [영상 검증 결과](docs/submission/2026-hmg/assets/v2_evidence_20261113/verification.json)
- [GPS Replay 원본 MP4](docs/submission/2026-hmg/assets/SURPRIDE_live_route_replay.mp4) / [실제 영상 프레임](docs/submission/2026-hmg/assets/SURPRIDE_live_route_replay_frame.png)
- [GPS Replay 보고서](docs/submission/2026-hmg/assets/SURPRIDE_live_route_replay_REPORT.ko.txt) / [출처·검증 원장](docs/submission/2026-hmg/assets/SURPRIDE_live_route_replay.provenance.json)

- [예선 체크리스트](docs/submission/2026-hmg/PRELIMINARY_SUBMISSION_CHECKLIST.md)
- [기획서 본문 제안·구현 대조](docs/submission/2026-hmg/PROPOSAL_IMPLEMENTATION_REVIEW.md)
- [이전 영상 구성·증거 매핑](docs/submission/2026-hmg/DEMO_STORYBOARD.md) — v2 장면 구성은 위 최신 보고서 기준
- [최종 제출 목록·보류 항목](docs/submission/2026-hmg/FINAL_SUBMISSION_MANIFEST.md)

## GitHub 업로드 준비 상태

| 구분                      | 파일·상태                                                                                                                                                                                                            |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 이번에 정리한 업로드 대상 | `apk/압구정현대아파트_서프라이드_release.apk`, 루트 영상, `README.md`, `.gitignore`, `app/build.gradle.kts`, `scripts/check-release-dex-access.py`, 최신 hotfix 보고서·앱 영역 캡처. 아직 stage·commit·push하지 않음 |
| 추가 선별 반영 필요       | 위 링크의 자막 포함 v2·SRT·제작 보고서·GPS Replay·출처 원장은 현재 로컬 미추적 자료. README만 업로드하면 해당 GitHub 링크를 열 수 없음                                                                               |
| 로컬 보존 전용            | assets의 동일 무자막 원본, `app/release/`의 서명 APK·mapping·메타데이터, `.gradle/` 제작·시험 AVD·검사 자료, `docs/phase*/`, 빌드 산출물·환경파일·서명키. Git 제외 유지                                              |
| APK                       | 실제 검증한 `apk/압구정현대아파트_서프라이드_release.apk` 한 파일만 `.gitignore` 예외로 추적 가능. 원본·빌드 출력·진단 APK·서명키는 제외 유지                                                                        |

Git LFS 도구는 설치되어 있으나 현재 `.gitattributes`의 LFS 규칙과 LFS 추적 파일은 없습니다. 루트 영상은 약3.23 MiB이므로 일반 Git/웹 업로드 용량 범위에 들어 LFS가 필요하지 않습니다. GitHub는 웹 업로드 **25 MiB**, 일반 Git 개별 파일 **100 MiB** 제한을 두고, **50 MiB 초과**에는 경고합니다. [GitHub 공식 용량 안내](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github).

GPS Replay 원본은 53,006,970 bytes(약50.55 MiB)로 웹 업로드 한도를 넘으며, 게시하려면 Git 경로를 사용하거나 Releases 등 배포 위치를 확정해야 합니다. 제출 signed APK는 **23,325,144 bytes(약22.24 MiB)**로 웹25 MiB·일반 Git100 MiB 한도 안에 있습니다. 이후 **서명 APK가 100MB를 초과하면 공모전 규칙에 따라 GitHub Releases를 사용**하고 실제 다운로드 URL을 기록합니다. 기존 영상·이력은 삭제하거나 LFS로 일괄 변환하지 않습니다.

**로컬 제출 APK·영상은 준비됐으며 GitHub 업로드는 아직 하지 않았습니다.** 위 상대 링크는 실제 파일 존재를 확인했습니다. 최종 제출 전 필요한 소스·자료를 선별 반영하고 비로그인 환경에서 다운로드·재생·접근을 확인해야 합니다. 기존 UI 미커밋 변경도 APK에 포함되므로 최종 커밋의 소스와 제출 APK를 일치시키세요. 이전 제출 문서의 NOT_READY·테스트 수는 당시 기록이며 현재 상태는 이 README와 최신 hotfix 보고서 기준입니다. 최종 commit hash를 확정해 접수 이메일에 GitHub URL과 함께 기재하는 작업은 남아 있습니다.
