# Phase 3B-3 실제 지도·활성 미스터리 내비게이션 계획

시작: main / ae83519b7a28058f6c9053665e01bf9c4799fdb3 / clean. 이전 중단된3B-3은 읽기 조사만 했으며 이번 시작 시 변경/실행 중인 작업은 발견되지 않았다. 사용자 최신 명세가 우선하며 정차 AVD의 명시적 활성 실험을 승인된 범위로 처리한다. 공모전 허용은 개발 가정이고 공식 승인 완료로 표시하지 않는다. 커밋/Push/전면 재구성 없음.

구성: 기존 PoC에 별도 ActiveMysteryScreen을 추가한다. AndroidCarFocusPort + 기존 NavigationOwnershipAdapter, RouteMatcher/PrivacyTripFactory를 재사용한다. OSM 공식 raster tiles의 화면 요청만 이용하는 Canvas 배경지도와 Follow Camera를 준비하고 제공 정책·표시 의무·캐시를 준수한다. geometry를 basemap이라고 부르지 않는다. 기존 앱에서 선택한 실경로를 일회성 URI 권한으로 PoC에 전달하고 결과도 일회성으로 가져와 LIVE 기록으로 분리한다. API key는 기존 server에만 둔다.

- [ ] 공식 지도/Focus/Navigation/Surface 계약과 제공업체 조건 조사. 부적합한 소스는 격리.
- [ ] 별도 파일로 지도 카메라/렌더러와 Native Focus/OEM preflight를 병렬 준비. 공용 파일/Gradle/에뮬레이터 조작은 root만 수행.
- [ ] 순수 도착/안내 상태·개인정보/카메라/소유권 테스트 RED→GREEN 후 화면 연결.
- [ ] 실제 경로 private 전달, main MVP 진입/준비/명시 시작→소유권→GPS→도착·정차 확인→공개/평가/기록 연결. DEMO 보존.
- [ ] 실제배경 지도/현재점/Follow/적응 전방 경로/Trip·TBT·자체 ETA·중지/공개 UI.
- [ ] 기존182개 전체+신규 테스트 및 표준 빌드. 실제 권한 오류는 우회하지 않고 해당 기능 중단.
- [ ] AVD/OEM route/Phone Projection 확인 후 수동 승인으로 실제 시작·지도·GPS 재현·공개·종료·채널 노출/Crash 관찰.
- [ ] ACTIVE_MYSTERY_NAVIGATION.md에 기능별 완료/부분/미구현·지도 라이선스·SDK 수용·스크린샷·후속 기록.
- [ ] 최종 독립 리뷰/민감정보·diff/종료상태 확인.

검토 초점: 초기/상실 위치의 거짓 안내 금지; 타 내비 포커스 침해 방지; 추정 ETA와 공급자/host ETA 구분; 카메라/타일 실패를 단색 성공으로 포장하지 않기; 도착 단일fix로 확정하지 않기; 기본 CLOSED/일회성 승인과 사용자 실제 시작을 유지하기.

REVISED 재개: 이 계획 이후 생성된 미커밋 코드를 그대로 재사용했다. Pleos 기본 지도 우선 조사를 추가했고 공개 NaviHelper 범위 NOT_EXPOSED를 확인한 뒤 OSM 구현을 유지했다. 위 체크 항목은 작업 시작 시점의 계획이며, 최종 완료/부분/미검증 상태와 근거는 ACTIVE_MYSTERY_NAVIGATION.md를 기준으로 한다. 실제 지도·Focus·목적지 없는 Trip·GPS 갱신·공개/종료·별도 평가/기분 지도 연결을 AVD에서 확인했다. 시스템 채널 전체 비공개와 정식 배포 승인은 미완료다.
