# Phase 3B-2 구현 계획

> 실행: executing-plans로 현재 workspace에서 직접 수행하고 마지막에 읽기 전용 독립 검토를 받는다. 사용자 요청에 따라 커밋/Push/별도 checkout은 하지 않는다.

기준: main / b76331468d725f43e42f84250754dbf002c83439 / clean. 목표는 정책·필드 계약과 테스트 가능한 소유권 모델이며 실제 길 안내 활성화가 아니다.

설계 결정: 기존 PoC 내부 ownership/에 순수 상태 컨트롤러, 목적지 없는 Trip factory를 추가한다. 포커스와 navigation lifecycle을 port로 분리하고 프로덕션 실제 manager에 연결하지 않는다. 목적지 비공개 시 Destination 객체 자체를 생략하고 공개 요청 시에만 실제 identity를 가진 Destination을 만든다. Step/TravelEstimate에 목적지명/주소를 우회 삽입하지 않는다. 기존 GPS/DEMO 화면은 보존한다.

대안 검토: 즉시 실제 focus/navigationStarted를 호출하는 방식은 정책/host 노출 미확인 조건과 충돌한다. 문서만 조사하면 종료/충돌/직렬화 계약을 검증하지 못한다. 따라서 순수 모델 + 같은1.7.0 공식 app-testing/TestNavigationManager + 별도 수동 감사 화면을 선택한다. Robolectric은 테스트 전용으로 사용하며 기존 SDK/AGP 버전은 유지한다.

- [x] 실제1.7.0 builder/serialization/manager bytecode와 공식 정책 확인. API4에서 사용 가능한 메서드만 선정.
- [x] 소유권 테스트 RED→GREEN: 명시적 요청/정차/정책·privacy gate, OEM unknown/active 및 전환 동의, grant/loss/중복/host stop/오류/정리 실패/늦은 callback/release/reconnect.
- [x] Trip contract 테스트 RED→GREEN: Destination 생략, empty Destination 거부, next-step ETA와 destination ETA 구별, 좌표/identity 미전송, 명시적 공개, 잘못된 Maneuver/TravelEstimate, 실제 Bundleable round-trip.
- [x] 공식 TestNavigationManager에서 등록/시작/updateTrip/host stop/종료/중복/실패 및 상태 컨트롤러 port 연결 검증. 실제 CarAppFocusManager 대체라고 주장하지 않음.
- [x] 정차 에뮬레이터 전용 읽기 전용 계약 감사 화면 추가. 실제 manager 획득/포커스 요청/음성 없음.
- [x] main89/PoC27/FastAPI33 회귀 + 신규 테스트, build, ADB 설치/화면/crash 확인.
- [x] 채널별 PASS/LEAK/UNVERIFIED와 공개 전환/좌표 계약, 실제 실험 보류 이유·Phase3B-3 조건을 보고서에 기록.
- [x] 독립 검토, diff/인증·위치정보 검사, 종료 상태 확인.

검토 초점: request 성공과 grant를 혼동하지 않기; callback reentrancy/late grant/부분 실패 때 정리; 공개 모드가 사용자 요청 없이 바뀌지 않기; test host와 실제 host 채널을 혼동하지 않기; ETA를 목적지 metadata 없이 host가 처리했다고 단정하지 않기.
