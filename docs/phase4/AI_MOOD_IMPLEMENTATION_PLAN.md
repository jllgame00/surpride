# Phase 4A 구현·검증 계획

기준: main / 8501bd55d201ed625c2d1da17711b8d60cb32ffb / clean. 기존 306개 테스트와 Phase 0~3B-4 코드·검증 자료 보존. 현재 프로젝트 루트에서만 수정하며 커밋·Push·새 worktree·전면 재작성 없음.

사용자 상세 명세와 자율 구현 지시에 따라 다음 선택으로 진행한다. Gleo는 App Schema/Action/Callback과 승인 상태를 조사하되 자유 대화 LLM API로 가정하지 않는다. 외부 LLM·등록 credential 존재 여부만 검사하고 값은 출력하지 않는다. 없으면 서버에 대화를 보내지 않는 RULE_BASED를 구현한다. Pleos STT는 실제 공개 SDK 계약·일반 권한·ON_DEVICE 지원을 검증한 뒤 최소 연동한다.

1. **기준 감사/회귀**: Git·기존 DrivePreferences·DestinationEngine·RouteProvider·TTS·FastAPI 확인. 표준 Android/PoC 빌드와 backend 42개 회귀. 구현 중 새 테스트는 실패를 확인한 뒤 최소 코드 작성.
2. **순수 조건 해석**: moodinput/core에 nullable PreferencePatch, MoodInterpretation, RuleBasedMoodInterpreter, 엄격 JSON codec, 안전한 준비 힌트. 원문은 로그/영구 저장 없이 세션 메모리에만 둔다. 6기분·시간/시각·동의어·부정·충돌·모호함·수정 테스트. 임의 장소/좌표/명령은 schema에서 거부한다.
3. **기존 엔진 연결**: DrivePreferences 끝에 기본값 emptySet의 excludedCategories 추가. DEMO/LIVE에서 부정 조건 필터링. 기존 binary snapshot은 구형 형식을 읽으며 확장 필드만 뒤에 추가한다. private intent·실제 추천에도 조건을 전달. 기존 선택 UI와 기록 유지.
4. **정차 입력 UI**: 기존 디자인의 MoodInputActivity를 Activity Result로 연다. 현재 DrivePreferences를 입력 기준으로 표시, 해석 제안과 UNKNOWN/문제 표시, 일부 조건 수정, 사용자 확인 후만 적용. 본 화면은 경로 탐색/안내를 시작하지 않는다. 기존 선택 입력·DEMO/LIVE 진입 보존.
5. **공식 STT**: moodinput/voice의 테스트 가능한 상태 컨트롤러와 Pleos ON_DEVICE adapter. 명시적 음성 시작·취소, partial/final, 무음/오류/15초 timeout, 늦은 callback 폐기, onStop release. 실제 음성 인식과 텍스트 예시는 별도 기록.
6. **검증/보고**: 모든 회귀·신규 테스트, 독립 코드 리뷰, 정차 Pleos 설치·자연어/수정/추천/준비/기존 지도/도착/기록 검증. docs/phase4/AI_MOOD_PREFERENCES.md와 민감정보 없는 증거. AI_PROVIDER/STT 및 미확인 채널 구분.

검토 초점: 시간 부정/자정 경계, 수정 시 PET/CHILD 같은 기존 제약 유실, JSON bool/number 강제 변환, 정차 상태 변경 중 늦은 음성 결과, 목적지/원문 외부 전송 및 저장 방지. 이 항목은 소유 작업의 테스트와 최종 리뷰에서 확인한다.

역할: 순수 parser/codec와 STT 계약·컨트롤러는 독립 파일 범위에서 병렬 작업한다. 공용 모델·엔진·codec·UI·Manifest·Gradle·ADB·backend 운영은 root가 순차 통합한다. 공용 Gradle/에뮬레이터는 동시에 조작하지 않는다. 추가 설계 승인/커밋 절차 대신 사용자 자율 구현 지시를 따른다.
