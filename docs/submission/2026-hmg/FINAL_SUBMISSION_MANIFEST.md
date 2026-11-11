# SURPRIDE 예선 제출 목록과 최종 게이트

2026-11-11 · 현재는 **준비 자료 작성 완료**, **실제 접수 패키지는 NOT_READY**다. 공식 양식 작성·전원 서명이 없고, 선택 자료별 준비도가 다르다. 이 manifest가 접수 완료·메일 발송·외부 업로드 영수증은 아니다.

## 제출물별 판정

| 제출물 | 상태 | 실제 준비된 것 | 남은 필수 작업 |
|---|---|---|---|
| 기획서·동의서 | **NOT_READY** | 공식 양식 구조/규정 확인, 개인정보 없는 본문 제안 | 공식 원본에 작성, 구성원 확인, 동의·서명·날짜·대표 발송/CC |
| 시연 영상 | **READY_WITH_LIMITATIONS** | 로컬110초 H.264 MP4·SRT·장면 원장 | 사람 최종 시청·내용 승인, 첨부 또는 접근 가능한 외부 영상 URL 확정 |
| GitHub 묶음 | **NOT_READY** | 현재 Public 소스 접근 확인, 새 README·실행 안내 로컬 작성 | 새 자료 선별 반영·공개 개인정보/권리 검토, 실행 가능한 Release APK 포함 조건 충족 |
| APK | **NOT_READY** | 메인 unsigned Release 조립·서명 검사, PoC 실패 원인 기록 | 서명·PoC Lint·Release 환경 의존성 해결 및 실제 정차 실행 검증 |

기획서만으로도 예선 지원이 가능하므로 선택 APK의 미완성을 숨기거나 잘못된 release를 제출하기보다, 필수 양식과 명확히 표기한 영상에 집중하는 것을 권고한다. [공식 FAQ](https://appdevchallenge.auto-hmg.io/faq).

## 로컬 산출물

| ID | 파일 | 용도·공개 범위 |
|---|---|---|
| DOC-01 | [PRELIMINARY_SUBMISSION_CHECKLIST.md](PRELIMINARY_SUBMISSION_CHECKLIST.md) | 공식 요건·사람의 최종 접수 작업 |
| DOC-02 | [PROPOSAL_IMPLEMENTATION_REVIEW.md](PROPOSAL_IMPLEMENTATION_REVIEW.md) | 공식 양식02에 옮길 본문 초안·기능 상태·평가 논리 |
| DOC-03 | [DEMO_STORYBOARD.md](DEMO_STORYBOARD.md) | 110초 장면·자막·출처·미검증 구분 |
| DOC-04 | [GITHUB_RELEASE_READINESS.md](GITHUB_RELEASE_READINESS.md) | 저장소·서명·빌드·실행·보안 및 보류 근거 |
| DOC-05 | 이 manifest | 제출 여부·경로·최종 게이트 |
| VID-01 | [SURPRIDE_preliminary_prototype_110s.mp4](assets/SURPRIDE_preliminary_prototype_110s.mp4) | 기존 캡처 편집본, 현재 연속 주행/활성 Host 영상 아님 |
| VID-02 | [SURPRIDE_preliminary_prototype_110s.srt](assets/SURPRIDE_preliminary_prototype_110s.srt) | 한국어 자막 원고 |
| EVD-01 | [video-provenance.json](evidence/video-provenance.json) | 원본 경로·hash·발췌 범위·시각 |
| EVD-02 | [release-security-summary.json](evidence/release-security-summary.json) | 실제 빌드·서명·로컬 이력 비식별 검사 결과 |
| EVD-03 | [artifact-manifest.json](evidence/artifact-manifest.json) | 현재 산출물 크기·SHA-256·Git 보존 확인 |
| ROOT | 저장소 root `README.md` | 심사자/개발자 환경·빌드·안전한 실행 범위 |

작성/서명한 접수 원본은 이 목록에 없으며 저장소 밖에서 따로 관리한다. 저장소 내부에서 찾지 못한 원본을 작성 완료로 추정하지 않았다. 공식 빈 양식은 [다운로드](https://appdevchallenge.auto-hmg.io/downloads/contest-application-consent.docx)하여 사람이 작성한다. APK 보류 산출물은 `apk/`에 복사하지 않았고 signed Release 링크도 만들지 않았다.

## 버전·검증 구분

시작 Git은 `main / 40ca62877e60deb353e476648c9b08ab558a90fc`, clean이었다. 원격 main SHA도 같았고 저장소 visibility는 Public이었다. 이번에는 README와 제출 자료만 작성했다. 기존 코드·테스트·Gradle 설정·증거와 ignore는 보존하며 commit/push하지 않는다.

이전 B6 **JVM/서버675 PASS**, 이전 B5 **계측31 PASS**를 각각 승계한다. 전체 검사 목록706을 Phase6C에서 새로 실행했다고 표시하지 않는다. 이번 새 검증은 공식 페이지/빈 양식 조사, Release 조립·서명·static 설정, 로컬 이력/산출물 검사와 영상 렌더·기술/시각 확인이다.

## 접수 직전 순서

1. 공식 양식 본문·참가자·동의·서명 완성. 최종본을 다시 열어 누락/실명 분리를 확인.
2. 영상의 DEMO/개별 캡처/계획 표기를 유지하고 최종 시청. 외부 링크를 쓰면 비로그인·타 환경에서 재생 확인.
3. GitHub/APK는 준비된 것만 선택. 미서명 APK·debug 이름 변경·막힌 Host 시연을 제출 가능한 것으로 적지 않음.
4. 공개 파일을 선별해 사람이 version/추적/링크를 확정. 연락처·서명·키·raw route·로그를 포함하지 않음.
5. [체크리스트](PRELIMINARY_SUBMISSION_CHECKLIST.md)의 공식 절차대로 대표자가 직접 메일 작성·전원 CC·전체 첨부 검사 후 제출. 수정본도 전체 자료 단위로 관리.

이번에 이메일 발송·초대·권한 변경·원격 업로드·서버 배포·차량/AVD 실행을 하지 않았다.

## 12월 PT 전 보완 권고

| 우선 | 보완 | 완료 증거 |
|---|---|---|
| 1 | 공식 Host의 종료 접근과 Focus/Surface/lifecycle, 기존 ANR 원인 | 종료·상실·재연결 반복 측정, 안전하게 종료 가능한 현재 버전 |
| 2 | 실행 가능한 signed Release와 단독/프록시/PoC 지원 범위 | 심사 환경 설치·시작·종료·복원 및 앱/PoC별 버전/서명/hash |
| 3 | 실제 추천→준비→안내→명시 도착→평가/복귀 연결 | 지원 정차 환경의 연속 실행 증거, 실패 시 안전 종료. 실차 인증과 분리 |
| 4 | STT final 수신·TTS 실제 청취·소유권 경합 | 실제 음성 결과와 callback 계약을 구분한 반복 검증 |
| 5 | 목적지 노출 채널·보존/캐시 정책 | Host/지도/알림/복원별 비공개 관측, 삭제/오류/backup·native 종료 |
| 6 | 실제 경관 후보·진입/주차/운영 확인 | 검증한 후보만 승격. 현재 검증 경관 경유지0곳 유지 |
| 7 | 고객·사업 가설 확인 | 인터뷰·정차 베타, 목표/실측을 구분한 만족·재사용 지표 |

본선 완성도 계획이며 자동 실차 시험·안전 제한 해제·상용 공개 배포 지시가 아니다. HOST_ACTIVE BLOCKED, DESTINATION_PRIVACY/STT/CACHE_POLICY/DATA_RETENTION PARTIAL 및 실차·상용 미검증을 유지한다.
