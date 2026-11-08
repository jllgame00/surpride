# Phase 1A 실행 계획

사용자 요청에 따라 현재 루트와 main에서 진행하며 커밋·Push는 하지 않는다. 시작 HEAD: e8e1925e627eb24cbec51f76c7b7253fc9230a6d, 작업 트리 깨끗함.

## 설계

- Vehicle 2.3.2 공식 의존성을 추가하고 CAR_ENERGY/CAR_DRIVE 조회 권한만 선언한다. 기존 빌드 스크립트·도구 버전은 유지한다.
- 공개 API로 확인된 SOC, 연료 %, 누적 주행거리의 capability → read → subscribe를 호출한다. 문서에서 제외된 gear/range 메서드를 바이너리에 존재한다는 이유로 사용하지 않는다. 해당 항목은 미지원/미확인으로 표시한다.
- VehicleState와 VehicleDataSource 인터페이스, 순수 Kotlin 세션 상태 관리, PleosVehicleDataSource SDK 어댑터를 분리한다. 테스트용 FakeVehicleDataSource는 test 소스에만 둔다.
- 초기화 반환만으로 연결 성공이라 표시하지 않는다. 실제 callback을 받은 경우에만 응답 확인으로 표시한다. 권한 거부, 미지원, null, 오류, 응답 시간 초과를 구분한다.
- onStart에서 생성/연결하고 onStop에서 모든 listener 해제 후 release한다. 지연 callback은 세션 토큰으로 차단한다.
- 기존 시작 문구를 보존하고 debug APK에만 조회 상태·수동 새로고침/구독 중지 패널을 표시한다. Mock 값 자동 대체와 차량 제어는 하지 않는다.

## 검증 순서

1. SDK 의존성 실제 해석 및 classes.jar 공개 시그니처 확인 (완료).
2. 실패/미지원/null/정상값/늦은 callback/구독 해제/재시작에 대한 순수 Kotlin 테스트를 먼저 작성한다.
3. 데이터 소스와 debug 패널 구현 후 Phase 0B 표준 빌드·테스트를 실행한다.
4. Pleos user/서비스 버전을 확인하고 설치·조회·콜백·중지/재시작·화면·Logcat 증거를 확보한다.
5. Virtual Console 접근이 가능하면 시뮬레이터 UI에서 변경을 검증하고 원복한다. 현재 브라우저 호스트 불가 및 네이티브 실행 승인 시간 초과로 UI 접근 제한이 있다. 미공개 HTTP/VHAL 명령으로 우회하지 않는다.
6. 기존 Phase 0/0B 문서와 스크립트를 보존하고 VEHICLE_SDK_VALIDATION.md에 실제 결과와 제한을 기록한다.
