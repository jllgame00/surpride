# Phase 5C 시나리오 정적·단위 계약 대응

이 문서는 `nav-host-poc/src/main`, 해당 모듈의 기존 테스트 소스, `docs/phase5/UI_UX_IMPLEMENTATION.md`를 읽어 작성한 정적 감사다. 테스트 메서드의 존재와 검사하는 계약을 기록하며, 실행 결과나 실화면 합격을 판정하지 않는다. ADB·에뮬레이터·빌드·테스트·프로세스 조작은 수행하지 않았다.

표의 테스트 클래스는 `nav-host-poc/src/test/java/com/surpride/navhostpoc/` 아래에 있다. `ActiveMysteryScreen.kt`와 `NavigationFooter.kt` 등의 제품 파일은 `nav-host-poc/src/main/java/com/surpride/navhostpoc/` 기준이다.

| 시나리오 | 확인할 수 있는 정적·단위 계약 | 남은 실화면 검증 |
|---|---|---|
| A 정상 추천→안내→공개→평가 | 내비게이션 부분은 H/I의 공개·도착 계약과 대응한다. `ActiveMysteryScreen.returnResult()`는 공개된 outbound 세션에서 안내를 종료하고 결과 Activity를 연다. 추천·평가 저장을 포함한 앱 전체 테스트는 이 감사 범위 밖이다. | 정상 흐름 전체의 화면 전이, 평가 저장 및 기록 연결 |
| B 조건 변경→재추천 | 앱 조건·추천 영역은 이번 내비게이션 정적 감사 범위 밖이다. 이 문서에서 확인된 계약을 주장하지 않는다. | 조건 변경 반영과 재추천의 전체 화면 흐름 |
| C 자연어 오류 수정 | 앱 입력 영역은 이번 감사 범위 밖이다. 이 문서에서 확인된 계약을 주장하지 않는다. | 오류 표시·수정·재검토·적용의 전체 화면 흐름 |
| D 경로 실패 복구 | `routing/ProxyRouteRefreshClientTest.safeErrorCodesAreBoundedAndNoRawServerMessageEscapes`, `timeoutAndNetworkExceptionsNeverEscapeTheirMessages`는 실패 응답의 제한된 오류 표현을 검사한다. `NavigationFooterTest.reroutingAndFailureHavePriorityOverOrdinaryPrivacyLine`는 실패 문구 우선순위를 검사한다. 앱의 최초 경로 준비 전체는 별도 범위다. | 최초 준비 실패 및 재요청, 실패 후 다시 시작하는 화면 흐름 |
| E GPS 상실 | `tracking/RouteMatcherTest.staleInaccurateFutureAndOutOfOrderSamplesHaveNoGuidance`, `NavigationFooterTest.uncertainPositionDoesNotShowOldExactEtaOrRoad`, `routing/RerouteCoordinatorTest.gpsLossCancelsWithoutRetryUntilReliableFixReturnsAndLateFailureCannotClobberNewRequest`. `ActiveMysteryScreen.positionUnavailable()`는 loading Trip·회전 안내 보류를 수행하며 tick은 장기 상실 시 안내를 종료한다. | 상실 직후 보류, 장기 상실 종료, 복구 후 명시적 재시작. 타이머를 포함한 Screen 통합 검사는 확인하지 못함 |
| F 재탐색 성공 | `routing/RerouteCoordinatorTest.successIsDeliveredOnceAndCooldownPreventsImmediateDuplicateRefresh`, `ownership/LoadingTripTest.reroutingLoadingClearsOldTurnsAndKeepsExistingHostOwnership`, `RoutePacketReplacementTest.legacyPacketKeepsOriginalDepartureAcrossRerouteAndReturn`. 새 경로는 신선한 현재 위치와 일치해야 설치된다. | loading 상태에서 새 지도·회전·도로명으로 바뀌는 실제 화면 |
| G 재탐색 실패 | `routing/RerouteCoordinatorTest.transientFailureRetriesOnceAfterCooldownThenRequiresExplicitRetry`, `authenticationFailureDoesNotBecomeAutomaticRetryAfterGpsReturns`, `NavigationFooterTest.reroutingAndFailureHavePriorityOverOrdinaryPrivacyLine`. 최종 실패 시 Screen은 안내를 종료하고 직접 재시도를 요구한다. | 서버 실패 중 상태 문구, 안내 종료, 사용자 재시도 전이 |
| H 즉시 공개 | `NavigationFooterTest.explicitDisclosureChangesOnlyIdentityLine`, `ownership/TripPrivacyContractTest.identityCannotLeakViaRoadOrCueAndRevealRequiresExplicitRequest`, `ownership/NavigationOwnershipAdapterTest.hiddenTripAndExplicitRevealUseOfficialManagerAndHostStopStopsAllGuidance`. `reveal()` 자체는 활성 Screen을 종료하거나 다른 Screen을 push하지 않는다. | 활성 상태에서 공개 후 안내·위치 갱신이 유지되는 실제 화면 |
| I 정차 확인 공개 | `tracking/ArrivalGateTest.singleEndpointFixDoesNotArriveAndConfirmationIsRequired`, `movingUnknownParkedAndDistantEndpointNeverQualify`, `lossStaleOrOffRouteInvalidatesCandidate`, `NavigationControlMenuTest.arrivalAndReturnNeedTheirOwnVerifiedEligibility`. 도착 후보에는 반복된 신선한 정확 위치·종점 근접·정차 조건이 필요하며 직접 확인 후 안내 종료·공개한다. | 후보 표시, 정차 확인 조작, 종료·공개·평가 진입의 화면 전이 |
| J 복귀 | `RoutePacketReplacementTest.legacyPacketKeepsOriginalDepartureAcrossRerouteAndReturn`, `returntrip/ReturnBudgetTest.returningLegCanUseCurrentReturnEstimateWithoutRepeatingStay`, `NavigationFooterTest.returnRiskIsAdvisoryAndReturnCompletionIsDistinct`, `completedReturnDoesNotOfferUnavailableReviewOrAnotherReturn`. 복귀 요청은 준비만 수행하고 별도 시작이 필요하다. 복귀 도착 확인은 안내를 종료한다. | 복귀 준비→명시적 시작→활성 안내→정차 확인→완료의 전체 화면 흐름 |
| K 안내 취소 | `ownership/OwnershipControllerTest.stopBeforeGrantRejectsLateGrantAndReleaseRequiresReconnect`, `ownership/NavigationOwnershipAdapterTest.pendingStopAndDenialAlsoStopGuidanceWithoutCallingNavigationStarted`, `NavigationFooterTest.revealedNameCannotHideExplicitStopStatus`. 활성 메뉴의 종료는 소유권을 종료하고 위치·음성·요청 정리 hook을 호출한다. | 종료 조작 뒤 지도·상태·안내가 중단되는 화면과 재진입 |
| L Host 종료 | `ownership/OwnershipControllerTest.hostStopEndsBeforeAbandonAndNoMoreTripPublication`, `ownership/NavigationOwnershipAdapterTest.hiddenTripAndExplicitRevealUseOfficialManagerAndHostStopStopsAllGuidance`, `ownership/TestNavigationManagerContractTest.controllerPublishesHiddenThenExplicitRevealAndStopsThroughOfficialCallback`. 공식 `onStopNavigation()`이 종료 처리에 연결된다. | 실제 Host 종료 콜백 후 화면 상태와 재시작 조건 |
| M 프로세스 재시작 | 직접적인 `ActiveMysteryScreen` 프로세스 재생성 테스트는 확인하지 못했다. 관련 계약은 `ownership/NavigationOwnershipAdapterTest.reconnectGetsFreshManagerAndOldStopCallbackCannotStopNewSession`, `tracking/LocationSessionTest.fakeSubscriptionIsUniqueAndLateCallbacksAreIgnoredAfterPauseOrRestart`. 생성 시 packet을 로드하지만 공개·시작 승인은 초기화되며 자동 활성화하지 않는다. | 실제 프로세스 종료·재생성 후 준비 화면, 비공개 상태, 자동 안내 없음 |
| N 서버 미연결 | D/G의 제한된 오류 표현과 재시도 계약이 관련된다. `routing/ProxyRouteRefreshClientTest.cancellationSuppressesQueuedWorkerAndQueuedDelivery`는 취소 후 늦은 전달 억제를 검사한다. | 미연결 중 최초 준비·재탐색·복귀 준비 각각의 오류와 복구 화면 |
| O 위치 권한 없음 | `tracking/LocationSessionTest.permissionFailureAndProviderFailureAreDistinctAndNoCoordinatesAreLogged`는 이벤트 구분과 위치 문자열 비노출을 검사한다. `AndroidPositionProvider`는 미허가와 정확 위치 부족을 구별하며 `ActiveMysteryScreen.maybeStart()`는 정확 위치 권한이 없으면 시작을 차단한다. 실제 플랫폼 권한 대화상자를 검사하는 테스트는 아니다. | 거부·대략 위치만 허용·정확 위치 허용 뒤의 실제 화면과 시작 차단/재시도 |

## 추가 지도 실패 계약

타일 실패는 원래 A–O의 별도 항목이 아니지만 지도 검증에 관련된다. `map/TileStoreTest.missingTileFailureIsExplicitAndNeverReturnsFabricatedMapData`와 `expiredTileIsConditionallyRevalidatedAndRetainedOnServerFailure`는 누락 타일 및 저장 타일의 서버 실패 계약을 검사한다. `map/RoadMapRenderer.kt`는 전체 로드 실패와 오래된 저장 지도 상태 문구를 구성한다. 실제 실패 타일 화면, 문구 가독성 및 안내와의 동시 표시는 별도 실화면 검증이 필요하다.

## 실화면 조작 경로

1. 활성 지도에서 **종료·조작**을 선택한다. 같은 Screen의 공식 메뉴이므로 이 메뉴 진입 자체는 Screen의 `onStop`을 유발하지 않는다.
2. 도착 후보가 충족되면 **정차·도착 확인**을 선택한다. 안내 종료·공개 후 지도에 돌아간다. 후보 미충족 시 이 항목은 제공되지 않는다.
3. 도착 후 **안내 준비 → 안내 종료 후 SURPRIDE 평가·기록**으로 결과 화면에 진입한다. 해당 outbound 세션이 공개되고 필요한 세션 식별자가 있어야 항목이 제공된다.
4. 복귀는 도착 후 **안내 준비 → 출발지 복귀 경로 준비 · 자동 시작 없음**을 선택한다. 준비 완료 후 **안내 준비 → 다른 안내·Phone Projection 종료 확인 후 시작**을 별도로 선택한다.
5. 복귀 활성 지도에서 **종료·조작 → 정차·도착 확인**을 선택하면 안내 종료·복귀 완료 상태가 된다.
6. 활성 중 공개 유지 검증은 **종료·조작 → 목적지 즉시 공개**, 사용자 종료 검증은 **종료·조작 → 안내 종료**로 수행한다.

`docs/phase5/UI_UX_IMPLEMENTATION.md` §12의 실화면 추가 검증 요구는 이 대응표로 해소되지 않는다. 시스템 전체 목적지 비공개와 실차 안전성도 이 문서에서 판정하지 않는다.
