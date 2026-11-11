# GitHub와 Release APK 제출 준비도

2026-11-11 · **공식 GitHub 가산점 묶음 NOT_READY / APK NOT_READY**. 소스 저장소는 열람 가능하나 실행 가능한 Release 패키지가 준비되지 않았다. 서명이나 실제 실행을 확인하지 않은 APK를 제출용 release로 이름만 바꾸지 않는다.

## 1. 저장소 상태

- 시작 `main / 40ca62877e60deb353e476648c9b08ab558a90fc`, 작업 트리 clean. 이전 보고서의 과거 HEAD를 현재로 가정하지 않았다.
- 연결된 GitHub 도구의 read-only metadata에서 **`jllgame00/surpride`는 현재 Public**임을 확인했다. Private을 Public으로 바꾼 것이 아니다. 심사용 계정 초대·권한 변경·push를 하지 않았다.
- 공개 GitHub REST로 확인한 main SHA는 시작 local HEAD와 일치했다. `git ls-remote`는 이 Windows 환경의 schannel credential 오류로 실패해 공개 API로 대체했다. 성공했다고 숨기지 않는다.
- root README가 없어 이번에 소개·환경·빌드/실행·테스트·APK/영상 위치를 작성했다. 새 README와 제출 자료는 아직 commit/push되지 않았으므로 원격 저장소에 반영됐다고 주장하지 않는다.
- 공식 제출은 소스와 APK를 함께 요구한다. 따라서 Public 소스 URL 자체가 존재해도 준비된 실행 APK 묶음이라고 판정하지 않는다. [공식 GitHub 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission).

## 2. Debug와 Release 조사

제품 코드·Gradle·Manifest·기존 테스트를 수정하지 않고 조립만 시도했다.

| 항목 | 메인 앱 | Navigation PoC |
|---|---|---|
| 패키지 | `com.surpride.app`, version1/1.0 | `com.surpride.navhostpoc`, version1/0.1-host-feasibility |
| 요구 환경 | Automotive, min28/target37 | Automotive + templates Host, min29/target34 |
| Debug | 기존 서명 유효, Android Debug 인증서. 기존 개발 검증 산출물 | 기존 서명 유효, Android Debug 인증서. 독립 Host 실험 |
| Release 컴파일 | 이전 단계 Kotlin 컴파일 성공 | 이전 단계 Kotlin 컴파일 성공 |
| 이번 Release 조립 | **성공**, `app-release-unsigned.apk` 생성 | **실패**, fatal Lint `Instantiatable` |
| Release 서명 | **없음**, apksigner 검증 실패 | 유효한 제출 산출물 없음, signing 설정도 준비 안 됨 |
| Release 실제 실행 | 미실행·미검증. 미서명 산출물은 정상 설치용이 아님 | 미실행·미검증 |
| 실제 프록시 | release `ProxyConfiguration.load`는 null | refresh 설정도 local127.0.0.1/10.0.2.2:8765 개발 계약 |

### 빌드 근거

1. 두 모듈 `assembleRelease --offline`: 미캐시된 기존 Lint `com.android.tools.lint:lint-gradle:32.5.1` 때문에 중단됐다. 이를 앱 소스 오류로 단정하지 않았다.
2. 기존 의존성을 정상 다운로드하는 온라인 빌드, `--no-configuration-cache`: PoC `nav-host-poc/src/main/AndroidManifest.xml:29`, **CarAppActivity must extend android.app.Activity [Instantiatable]**로 중단됐다. lint를 disable하거나 baseline으로 숨기지 않았다. SDK 분석 문제/실제 구성 원인은 미확정이며 이번에 고치지 않았다.
3. 메인만 `:app:assembleRelease --no-configuration-cache`: **11초 / BUILD SUCCESSFUL**. `app/build/outputs/apk/release/app-release-unsigned.apk`, 20,286,940B를 생성했다. 이 분리 실행으로 메인 실패와 PoC 실패를 혼동하지 않았다.
4. SDK36 apksigner로 기존 debug2개와 새 release1개를 검사했다. Debug는 서명 유효·Android Debug이고 메인 Release는 미서명이다. `aapt2 dump badging`으로 메인 패키지/버전/launcher/target37을 확인했다. 빌드·서명 요약은 [기계 판독 증거](evidence/release-security-summary.json)에 있다.

## 3. 심사자가 실행할 수 있는 범위

메인 단독은 지원되는 정차 Pleos 환경에서 DEMO·조건 입력·규칙 해석·모의 도착/평가·개념 기록을 제공한다. UX 상태가 미확인되면 안전하게 조작이 차단될 수 있다. 이론상 화면 코드가 있다고 Release 실행을 확인한 것은 아니다. 일반 스마트폰 설치·순정 AAOS의 모든 서비스와 호환된다고 보증하지 않는다.

실제 API 추천은 **debug-only** local 프록시 설정·단기 인증·개발 공개 출발지에 의존한다. Release는 이를 읽지 않으며, 명시적 실제 API 요청은 NOT_CONFIGURED로 종료한다. API 키는 APK에 넣지 않는다. 운영 서버·HTTPS·사용자 인증을 새로 공개 배포해 해결하지 않았다.

LIVE OSM/GPS 안내는 별도 PoC, 공식 AndroidX Host 및 Navigation Focus가 필요하다. 현재 Host 정상 종료 조작이 보장되지 않아 ACTIVE 시연은 BLOCKED다. 메인 APK 하나로 모든 기능을 실행할 수 있다고 설명하면 안 된다.

### 설치 안내의 조건

현재 unsigned APK를 심사자에게 설치하라고 안내하지 않는다. 서명한 새 산출물·지원 정차 AVD·패키지 호환을 검증한 뒤 다음 항목을 확정해야 한다.

- 공식 지원 Pleos 환경·SDK/권한·user10·명시 serial, 사용할 단독 DEMO/LIVE 범위.
- debug 기존 설치와 signing identity가 다른 경우 `-r` 업데이트가 실패할 수 있음. **기존 데이터 삭제·앱 제거로 우회하지 않고 별도 시험 환경을 사람이 준비**.
- 임의 설치·자동 안내 시작 없음. 공식 종료 경로가 없는 Host ACTIVE 조작은 금지.
- 앱 실행→DEMO/추천→평가→종료까지 실제 서명 Release를 검증하고 그 버전·hash·절차 기록.

## 4. 보안·개인정보 검사

이번 read-only 로컬 전체 참조 검사: **31 commits / 1,650 objects / 903 blobs / 264,636,069 bytes**. 알려진 로컬 구성 비밀값의 UTF-8/UTF-16LE와 GitHub/AWS/Google/Kakao 인증/PEM 대표 패턴을 검사했다. **이력 일치0**, APK3개의 압축 해제 entry 일치0이다. 실제 값·원문·개인 연락처를 결과에 적지 않았다.

현재 코드 저장소에서 작성된 기획서/영상 원본을 찾지 못했고, Git Office container도0개였다. 공식 빈 양식만 `.gradle/phase6-c/`에서 read-only 조사했다. 작성된 신청서·서명·동의서를 공개 제출 경로에 만들지 않았다. 새 영상의 6개 고유 앱 캡처는 별도 육안 확인했다.

**검사 한계:** 알려지지 않은 비밀 전체 탐지, unreachable/삭제된 원격 객체, 모든 이미지·영상·PDF OCR, 원격 미취득 참조의 완전 조사는 아니다. 현재 약300개 PNG 전체의 개인정보·권리 검토 완료도 아니다. Git 작성자 이메일은2종 존재하며 원문은 출력하지 않았다. 참가 서류/공유 링크에 개발자 신원이 드러나지 않도록 하라는 공식 양식의 주의와 함께 최종 공개 범위를 검토해야 한다. 이력을 자동 수정하지 않았다.

최종 공개 승인 전에는 새 자료뿐 아니라 **이미 Public인 저장소의 기존 자료**도 검토해야 한다. literal0이 비공개 완전성이나 무조건 공개 가능의 증거가 아니다. 고위험 비밀이 발견되면 원문 공유 없이 회전/폐기·노출 범위를 확인하는 별도 대응이 필요하다. [스캔 범위 요약](evidence/release-security-summary.json).

## 5. 선택적 추적·배포 계획

`docs/submission/2026-hmg/`는 기존 `/docs/phase*/` 규칙에 해당하지 않는다. 새 Markdown5개·README·검토한 MP4/SRT·비식별 요약만 선별적으로 추적할 계획이다. 자동 stage/commit/push는 하지 않는다. 기존 Phase6 증거 일부는 ignore되므로 필요한 비식별 결과만 이 제출 디렉터리에 요약했다. 기존 캡처300개·raw route·credential·개인 원본을 통째로 force-add하지 않는다.

APK는 아직 제출 경로에 복사하지 않았다. 일반 `*.apk` ignore는 유지했다. 준비 완료 후에만 정확한 signed APK 한 파일의 예외/명시적 추적 또는 승인된 GitHub Releases를 선택하고 공식 파일명·경로·hash를 확정한다. 서명키·동의서·연락처 원본은 어떤 선택에도 포함하지 않는다. 구체적 심사용 account는 공식 안내에서 확인하지 못했으며 임의 초대를 하지 않는다.
