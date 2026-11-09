# Phase 3B-4 구현·검증 계획

시작: main / 21854a9aacc9462c06baa82ddbbe61080210b154 / clean. 3B-3의 지도·소유권·위치·private handoff·DEMO와 증거를 유지한다. 기존 테스트 223개를 최종 회귀한다. 커밋/Push 없음.

1. 기존 ActiveMysteryScreen에 현재 경로 교체 기능을 추가한다. 확인된 OFF_ROUTE만 재탐색하고 같은 내부 목적지로 요청한다. 후보 오차에서는 정확한 TBT/음성을 중지하되 확인 전 재요청하지 않는다. Loading Trip과 loading RoutingInfo를 사용하며 소유권/GPS 구독은 유지한다.
2. 제한된 proxy client와 취소·generation·cooldown·상한 coordinator를 별도 routing 파일에 둔다. 서버 API key는 이동하지 않는다. 기존 private URI에 만료되는 개발용 proxy credential만 전달한다. 요청 실패/위치 상실/종료 시 stale callback을 폐기한다.
3. 음성은 공식 Android TTS/AudioFocusRequest를 사용하고 기본 OFF다. 내비 소유권과 오디오 포커스가 모두 있을 때만 정해진 회전·거리 문구를 발화한다. 목적지 이름/주소는 발화 입력에 넣지 않는다. Pleos 공식 권고에 맞는 무음 pre-roll을 검토한다. 요청·engine 완료·실제 청취를 구분한다.
4. 실제 출발 시각과 설정 예산/귀가 시각·체류·여유·복귀 추정값을 세션 범위로 관리한다. 별도 API 조회로 복귀 추정값을 갱신하되 자동 목적지 변경은 없다. 도착 후 명시적 복귀 선택 → 실제 경로 요청 → 새 명시적 활성 세션 → 복귀 완료 순서로 기존 위치·지도·TBT·TTS를 재사용한다.
5. 외부 작업 종료·재연결·지도 resize/상실·음성/소유권 중단을 처리하고 정차 AVD에서 정상/이탈/실패/복구/도착/복귀를 검증한다. 장시간 검증은 실제 수행한 시간만 보고한다.
6. main metadata/예산, routing, voice는 독립 파일 범위에서 병렬 개발한다. 공용 ActiveScreen/ownership/Manifest/Gradle/ADB/backend는 root가 순서대로 통합한다. 신규 테스트 RED→GREEN, 기존 회귀, 독립 코드 리뷰, 실제 화면과 metadata 증거를 확보한다.

실험은 Pleos 정차 AVD와 공식 API에 한정한다. 기존 활성화의 개발 가정은 앱마켓 승인과 구분한다. 미확인 시스템 채널은 계속 UNVERIFIED로 남긴다.
