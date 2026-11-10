# Phase 5C Validation Plan

**Goal:** Phase 5B 디자인을 유지하며 정차 Pleos AVD에서 결함을 재현하고 최소 수정한다.

**Architecture:** 기존 Native View/XML, 공통 SurprideUi, 공식 Car App Template/Surface를 사용한다. 제품 엔진과 저장 계약을 보존하고 실제 실행·합성 테스트·과거 증거를 구분한다.

**Spec:** 사용자 Phase 5C 요청 및 UI_UX_IMPLEMENTATION.md.

## 제약

- main / 43c659442ea7894f2ff3835eb4c1f3843d5b204f, 시작 clean. 커밋·Push·reset·clean 금지.
- 기존 481개 테스트·기록·before/after 증거 보존. 새 의존성·브랜드·Host 내부 조작 없음.
- DESTINATION_PRIVACY=PARTIAL, STT=PARTIAL 유지. 실차 검증 없음.
- 원본 AVD가 쓰기 제한되면 작업 폴더의 검증용 복사본을 사용하고 이를 보고한다.

## 실행 순서

- [x] 1. 기준선: 기존 app196/PoC195/backend90 테스트와 두 APK 빌드를 재실행하고 로그·XML 집계 보관.
- [x] 2. 실행 환경: Pleos API34, 사용자, 실제 해상도/density/글자/시스템 UI/IME/접근성 서비스 확인. 원본 데이터 보존.
- [x] 3. 화면 점검: 시작→조건→시간→문장 입력/오류/수정/취소→준비→공개/평가→기분 지도. 큰 글자·좁은/넓은 창·IME·Back·재생성·포커스 점검.
- [x] 4. 내비게이션: 실제 지도/회전, 재탐색/GPS/실패/중단/즉시 공개/도착/복귀와 공식 Host 계약 점검. 실행하지 못한 항목은 미검증으로 남김.
- [x] 5. 결함: 호출부·저장·접근성 영향 조사→재현 테스트 RED→최소 수정→GREEN. 변경 파일은 재현 결과에 따라 기록.
- [x] 6. 최종 검증: 전체 회귀·새 검사·두 APK 설치/실행·Crash/ANR·설정 복원·원본 보존 확인.
- [x] 7. docs/phase5/evidence/final/에 실제 PNG와 비식별 요약 저장. UI_UX_FINAL_VALIDATION.md에 요청 13항목과 시나리오 A–O의 검증 수준·남은 gate 기록.

## 중점 실패 조건

- 큰 글자/IME가 화면 본문 높이를 소비해 입력·CTA가 사라지는지.
- 선택 후 전체 View 재생성으로 D-pad/접근성 포커스가 소실되는지.
- 화면 재생성 중 입력·오류·미검토 상태가 바뀌거나 승인되지 않은 조건이 적용되는지.
- 지도/호스트 전환에서 GPS·포커스·안내 소유권이 중복되거나 남는지.
- 실패 상태를 성공으로 표현하거나 공개 전 장소 식별정보를 보내는지.

최종 시각 만족도는 VISUAL_ACCEPTANCE=PENDING_USER_REVIEW로 남긴다.

## ?? ?? ?? ? 2026-11-10

- ??481? ?? JVM/backend482?, ?? ??10?(3?? ??) ??. ? APK ???5556 ????? ??.
- ? ???CarIME??? ?????? ?? ?? ?? ??. ??? ?? ??/?? ??, ???/??/??? ??.
- ? ??? ?? ??? ????? ?? ??? ???. ?? TalkBack, ?? ?? ???, Host ?? ??, ?? ??/?? ??, ?? ?? ?? ?? ??? ??? ?? ???? PASS? ???? ???.
- ?? READY_WITH_LIMITATIONS / VISUAL_ACCEPTANCE=PENDING_USER_REVIEW / DESTINATION_PRIVACY=PARTIAL. ??? [?? ???](UI_UX_FINAL_VALIDATION.md).
