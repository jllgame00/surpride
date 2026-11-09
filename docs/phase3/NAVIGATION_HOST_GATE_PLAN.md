# Phase 3B-2.5 실행 계획

기준: main / 279c44407f865b2ba038d37d8d10282f02f215d4 / clean. 사용자 명세에 따라 현재 workspace에서 직접 구현·검증하고 끝에 독립 읽기 전용 검토를 받는다. 커밋/Push하지 않는다.

설계: 기존 OwnershipController와 PrivacyTripFactory를 감싸는 NavigationOwnershipAdapter 및 AndroidX 구현을 추가한다. manager는 승인된 request 시점까지 조회하지 않는다. 현재 기본 gate는 정책/권한 미확인으로 잠기며 감사 화면은 gate만 읽는다. 기존 FocusPort를 주입받고 실제 CarAppFocusManager 바인딩은 이번 범위에서 실행하지 않는다. 테스트에서만 공식 TestNavigationManager와 합성 focus를 연결한다.

대안: 실제 활성화/목적지 없는 Trip 전송을 지금 시도하면 미확인 정책과 충돌한다. 문서만 추가하면 실제 연동 adapter의 정리·오류 계약을 검증하지 못한다. 따라서 잠긴 실연동 코드 + 공식 테스트 호스트 + 비활성 UI를 선택한다. 새 SDK/권한/의존성은 추가하지 않는다.

- [x] 공식 NavigationManager/Trip/등록 정책과 실제1.7.0 계약 재감사. next Step ETA와 최종 ETA를 구분.
- [x] RED→GREEN: 기본 gate/일회성 사용자 승인/명시 요청/정차/OEM 충돌, lazy manager 조회 및 중복 방지.
- [x] RED→GREEN: 공식 callback/시작/update/종료, 모든 중지 시 주입한 안내 중단 hook, 늦은 callback·실패·재연결·앱 background.
- [x] 목적지 없는 Trip의 직렬화·Maneuver/estimate/공개 전환 회귀 및 새 Adapter 테스트.
- [x] 비활성 감사 UI에 잠긴 Adapter gate 표시. 실제 activation/Trip 전송은 하지 않음.
- [x] 기존170개+신규 테스트/Gradle/main·PoC/FastAPI 회귀 및 안전한 ADB 설치/화면/crash 검증.
- [x] 공식/정적/FakeHost/실제host 결과·채널 상태·활성 실험 절차·사무국 문의 문안을 NAVIGATION_HOST_INTEGRATION_GATE.md에 기록.
- [x] 독립 검토, 공백/민감정보/종료 Git 상태 확인.

검토 초점: 승인 없이 SDK 조회/콜백 등록하지 않기; 오래된 callback이 새 세션 안내를 중단하지 않기; 안내 중단 hook 실패에도 SDK 정리를 시도하고 오류를 유지하기; callback/manager를 해제·재연결할 때 재사용하지 않기; 공개 요청 거부를 새 목적지 전송으로 바꾸지 않기.
