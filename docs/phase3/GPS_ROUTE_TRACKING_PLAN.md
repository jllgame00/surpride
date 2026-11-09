# Phase 3B-1 구현 계획

기준 main / fe42f3d9bed86d70ed444eefaa5ae632361a4870 / clean. 기존 기능·검증 자료와 사용자 로컬 설정을 보존하고 커밋·Push하지 않는다.

GPS 실험은 nav-host-poc의 별도 화면과 위치 소스로 격리한다. 공식 LocationManager 전경 구독/CarContext 일반 권한 절차를 사용하고 GPS 수신과 Fake를 구별한다. 좌표는 메모리에서만 사용하고 UI/로그/Git에 기록하지 않는다. Phase 3A 시뮬레이터는 유지한다.

매칭은 geometry 위 선분 투영·누적 길이와 후보 방향/연속성을 이용한다. 불확실한 교차점, 낮은 정확도·오래된 값·급격한 점프/역방향·이탈에서는 UNKNOWN 계열 상태로 안내를 비운다. 여러 관측으로 이탈/재진입을 확인하며 임계값은 도시 공개 경로에 대한 개발 가정이지 실차의 정답이 아니다.

- [x] 순수 매칭·위치 구독 세션/Fake·개인정보 테스트 RED→GREEN.
- [x] 공식 Android 위치 adapter 및 권한/생명주기 연결.
- [x] 별도 GPS NavigationTemplate 및 Surface, 상태 불명 시 RoutingInfo 제거. 원래 재생 UI 보존.
- [x] 기존133개 전체 회귀·신규 테스트·표준 빌드.
- [x] 실제 공개 경로 private 공급, 일반 권한 및 공식 geo fix 재현·실제 Android 콜백·진행/이탈/재진입/상실/중지 검증.
- [x] 정책/목적지 노출 감사·증거·최종 읽기 전용 검토 및 보고.

navigationStarted/Ended/updateTrip/포커스 소유권·음성·자동 재탐색 없음. 공식 에뮬레이터 위치 입력은 이번 작업에서 요청된 테스트 범위이며 실도로 주행이 아니다. OEM 활성 경로가 없는 상태에서만 수행한다. 일반 권한 거부/재현 미지원이면 실패를 기록하고 Fake 검증과 구분한다.
