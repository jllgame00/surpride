# Phase 4B Drive Experience Implementation Plan

**Goal:** 기존 목적지 추천에 근거가 있는 복수 자동차 경로 평가를 추가하고 정차 Pleos 흐름으로 연결한다.
**Architecture:** 기존 DestinationEngine/RouteProvider를 재사용한다. optional RouteOptions(priority, 최대 2 경유지) 계약으로 프록시를 확장하고, 후보 사전 필터 → 실제 경로 조회 → 시간/접근 제약 → 목적지·경로 별도 평가를 수행한다. 경관은 근거가 확인된 구간/경유지만 가산하며 INFERRED/UNKNOWN을 나쁜 품질로 취급하지 않는다.
**Tech:** 기존 Kotlin/AndroidX/Pleos, FastAPI/httpx, Kakao Directions. 새 SDK 없음.
**Spec:** 사용자 Phase 4B 상세 명세.

## 제약과 결정

- 시작 main / 5782ce8e4c48a086e20fca1ced7f398b9ea38c3b / clean. 현재 루트에서 작업, 커밋·Push·worktree 전환 없음.
- 선택지: 무근거 geometry 기반 경관 추정은 제외. 검증 경유지 기반의 소수 조합 + 실제 RECOMMEND/TIME/DISTANCE 비교를 택한다. 경관 근거/진입점이 부족하면 경유 후보를 차단하고 일반 경로와 부족 사유를 제공한다.
- 자동차 Directions 공식 파라미터만 사용한다. 기본 RouteProvider의 기존 2점 계약과 기본 동작은 호환 유지.
- LIVE 응답 geometry를 새 조회 없이 준비 패킷에 전달하여 선택한 경로를 다른 기본 경로로 교체하지 않는다. 재탐색 시 남은 경유지 보존/미지원 중단을 명시한다.
- 가중치는 초기 제품 가설이다. 추정 경관 점수·안전 위험 보상·곡률 재미 점수 없음. 현재 데이터만으로 주행 경관을 보장하지 않는다.
- STT는 callback aggregate·공식 마이크 경로만 제한 진단. 원문/오디오/키/개인 좌표 로그 없음.

## Review Focus

1. 경유지의 관광 대표점과 차량 진입점 혼동: UNKNOWN 진입은 실제 경유 생성 차단.
2. 같은 priority 이름만 다른 동일 geometry: 중복 제거하고 복수 경로 검증으로 과장하지 않음.
3. 새 경로 선택 뒤 기본 경로로 재조회하는 회귀: 선택 options/geometry/guide 계약을 끝까지 유지.
4. 중간 경유의 도착 가이드로 최종 공개: 최종 종점과 경유 통과를 별도 검증.
5. 왕복/교통 갱신/지연 응답: 최종 시각에 예산·신선도 재검사, 제한 호출·취소.

## 실행 항목

- [ ] 기존 363 테스트, Git/모델/프록시/내비 연결 감사.
- [ ] backend optional priority/waypoints 및 응답 request_options: 실패 테스트 → 구현 → pytest 회귀.
- [ ] 출처/checkedDate/독립 신뢰 필드가 있는 경관 경유 메타데이터. 공식 source와 진입점 미확인 후보 구분.
- [ ] Android RouteOptions/option provider/응답 검증과 Experience engine 테스트 먼저 작성. 기존 DestinationEngine 점수 재사용, 최대 목적지 3/경유1/요청12 등 작은 상한, 동일 경로 제거.
- [ ] 별도 DestinationScore/RouteExperienceAssessment/weight/비공개 이유·비교 결과. 70분 일반 vs 86분 경유 예시와 초과 사례는 SYNTHETIC으로 명시.
- [ ] UI에서 최적화 비교 선택, 기존 DEMO/선택/자연어 유지. 선정 경로를 기존 private handoff로 전달.
- [ ] PoC에서 경유 geometry/가이드·통과와 재탐색 계약 감사. 지원하지 못한 활성 경유는 차단/명시.
- [ ] STT aggregate 단위 테스트와 한정 runtime 진단, 선택·텍스트 fallback 유지.
- [ ] full Android/PoC/FastAPI, review, ADB 정차 설치/경로 비교/지도/공개/기록, Crash/ANR, privacy 채널 한계.
- [ ] DRIVE_EXPERIENCE_OPTIMIZATION.md에 실제/합성/미검증·호출수·정책·스크린샷과 최종 판정 기록.

독립 작업인 서버 계약, 공식 경관 출처 조사, STT 진단은 담당 파일을 분리해 병렬 수행하고, 공유 Gradle/ADB/통합 모델/UI는 root만 다룬다. 사용자 자율 진행 지시와 커밋 금지가 추가 승인/커밋 절차보다 우선한다.

## 실행 결과

- 완료: Git·기존 구조 감사, 서버 옵션·guidance 계약, 연구 메타데이터/차단, 통합 엔진·점수·시간/호출 제한, 기존 UI·정확한 선정 경로 handoff, STT 집계·1회 정차 진단, 전체 회귀·독립 리뷰 및 두 결함 수정, 실제 복수 경로 집계와 정차 지도/도착 공개/평가 기록.
- 부분 완료: 실제 운영 가능한 경관 경유는 0곳. 경유 geometry/1000 통과/재탐색 차단은 합성 계약만 검증했고 LIVE gate는 CLOSED다. STT 마이크 창 접근 승인 timeout으로 설정을 바꾸지 않았으며 final은 미수신이다.
- 최종: app187 + PoC175 + backend90 = 452 PASS. 실제 API 집계는2unique경로, 앱의 다른 시점 재조회는1unique/2duplicate. 실제/합성을 보고서에서 분리했다.
- 검증 후 코드 변경 없음. main/HEAD 유지·미커밋, 서버 종료·focus 해제. 최종 보고서: DRIVE_EXPERIENCE_OPTIMIZATION.md.
