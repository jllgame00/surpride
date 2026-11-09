# Phase 3A 구현 계획

기준 HEAD 75cca983dd754847731211392afc37ca15b0bc5a / main / clean. 사용자 Phase 3A 명세에 따라 현재 프로젝트 내부에서만 작업하고 커밋·Push하지 않는다.

기존 앱 RouteProvider/실경로 평가/SDK는 보존한다. 프록시의 기존 v1 응답을 유지하면서 include_guidance 요청에만 도로 구간·공식 회전 코드·누적 안내 거리·도로 인덱스를 추가한다. 원문 guide.name/guidance는 목적지 노출 가능성이 있어 UI에 전달하지 않는다. 오류/미지원 정보를 직진으로 추정하지 않는다.

별도 nav-host-poc에 최소 순수 모델/JSON 계약/재현 가능한 좌표 시뮬레이터와 NavigationTemplate 화면을 추가한다. 원래 정적 PoC는 유지한다. 기존 프록시를 사용하는 host-side 명시적 요청 1건을 통해 공개 테스트 경로를 private cache에 stdin으로 전달한다. PoC에는 API 키나 프록시 토큰/네트워크 권한을 넣지 않는다. 복잡한 앱 간 통합은 미룬다.

- [x] backend guides/roads 유효성·오류·개인정보 제거 테스트 RED→GREEN.
- [x] PoC 경로/진행/일시정지·도착·재시작·비공개·잘못된 데이터 테스트 RED→GREEN.
- [x] 기존 정적 화면에 동적 실험 진입, 정차 UX gating, 제한된 템플릿 갱신과 Surface의 주변 경로 표시.
- [x] 표준 앱89/Backend29 회귀 + 신규 테스트, PoC 빌드 스크립트의 선택 테스트 옵션.
- [x] 실제 공개 경로 확보·private 임시 공급, Pleos NavigationTemplate 거리/회전/ETA/Surface 동적 갱신과 종료 증거.
- [x] 라이선스·포커스/지도/비공개 범위를 분리한 결과 문서 및 읽기 전용 검토.

실행 기록: 실제8도로9안내122점(중복 제거115),7437m1278초. geometry 단위에 따른 끝점 노출과 Surface 복귀 문제를 검토 후 수정했다. STOPPED에서 템플릿 타입 변경 시 호스트 BackFlowViolation이 발생해 동일 Screen 타입 유지로 수정/재검증. CarContext 가상 display UX와 물리display0 UX를 구분해 공식 API application context로 조회한다. 남은 native ETA clipping/첫 strip action만 표시/라이선스·활성 경로 정책을 제한으로 기록한다.

정차 에뮬레이터에서만 명시적 시작. 위치/OEM 경로 수정, 실제 NavigationManager 시작/Trip·음성/클러스터 안내, 포커스 소유권 요청 없음. 시뮬레이션의 거리·ETA는 보간한 개발용 모델이며 GPS 안내 성공으로 보고하지 않는다. 상용 지도 표시/geometry 이용 허가는 공개 API 접근과 구분하여 미확인으로 기록한다.
