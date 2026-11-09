# Pleos 지도 재사용 공개 API 조사

확인일: 2026-11-09. 범위: 공식 개발 문서와 프로젝트에 내려받은 **NaviHelper 3.0.5 / navi-data 3.0.4 AAR의 공개 클래스·메서드 시그니처**. OEM 앱의 내부 코드·저장소·지도 화면을 추출하거나 조사한 결과가 아니다. 이 문서는 실행 성공이나 상용 승인 증거를 대신하지 않는다.

## 판정과 적용 결정

**NOT_EXPOSED — 확인한 공개 NaviHelper 3.0.5 계약에는 Pleos 기본 지도를 자체 화면에 임베드할 MapView, 지도 Surface/렌더러, raster/vector tile 공급 API가 노출되지 않는다.** 공식 문서는 이 SDK를 기본 Navigation 앱에 요청을 전달하고 정보·상태 이벤트를 받는 통합 API로 설명한다. 별도 파트너용 지도 SDK·OEM 계약·향후 버전의 제공 여부는 **UNVERIFIED**다. 플랫폼 전체에서 기술적으로 불가능하다는 판정은 아니다. [NaviHelper 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), [공개 메서드 목록](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/entry)

이번 PoC는 아래 세 기존 파일의 OSM raster 지도·Canvas 렌더링을 재사용하는 구성을 선택한다. Pleos 지도를 복사하거나 Google/MapLibre SDK를 새로 연결하는 작업은 이 조사에서 수행하지 않았다.

## NaviContainer, 화면 요청, Surface의 차이

| 항목 | 확인한 계약 | 이 계약으로 얻지 못하는 것 |
| --- | --- | --- |
| Pleos `NaviContainer` | 내비 앱을 위한 시스템 고정 표시 영역. `APP_MAPS` 분류로 해당 앱을 식별한다. | 가져다 쓰는 기본 지도 View·tile 공급자라는 보장 |
| `requestNavigationScreen(request)` | 기본 Navigation 앱에 `Foreground`, `MoveToCurrentLocationOnMap`, `OverviewRoute` 등의 화면 명령 전달 | 앱 소유 지도 View/Surface 반환, tile·지도 이미지 반환 |
| AndroidX `SurfaceCallback` | 호스트가 앱에 그릴 Surface와 visible/stable 영역을 제공한다. 앱이 Canvas 또는 View를 렌더링한다. | 호스트/OEM의 기존 지도 그림이나 지도 데이터의 자동 제공 |

근거: [Pleos Maps/Navigation 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide), [NavigationScreenRequest 종류](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry), [Android 지도 Surface 계약](https://developer.android.com/training/cars/apps/library/draw-maps).

**`pleos.car.permission.NAVI_CUSTOM_MAP`은 `requestNavigationScreen()`용 권한이다.** 이름만으로 지도 데이터 사용권이나 기본 지도 임베드 권한을 얻었다고 해석하지 않는다. 정보 조회 `get*Info()`는 `NAVI_ROUTE`, 경로 변경은 `NAVI_ROUTE_SEARCH`로 구분된다. [공식 권한 표](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)

## 실제 SDK의 공개 시그니처 증거

프로젝트의 `.gradle/navi-sdk-inspect/classes.jar`와 캐시의 `navi-data-3.0.4/.../classes.jar`에 대해 JDK `jar tf` 및 `javap -public -classpath ... <클래스>`로 확인했다. NaviHelper POM도 `navi-data:3.0.4` 의존성을 선언한다.

```text
NaviHelper.requestNavigationScreen(NavigationScreenRequest): void
NaviHelper.requestTrip(TripRequest): void
NaviHelper.getRouteStateInfo(): void
NaviHelper.getCurrentLocationInfo(): void
NaviHelper.getDestinationInfo(): void
NaviHelper.getTBTInfo(): void
TripInfo: distance, eta, estimatedSocAtDest, energyConsumption, summary
TripSummary: trafficConditions, routePoints
RoutePointInfo: latitude, longitude, poiName, poiId, ratioOnRoute,
                poiType, pointType, settingType
TBTInfo: type, distance, description
RouteOverviewShowInfo: durationMs
```

공개 진입점·리스너에는 지도 View/Surface/bitmap/tile/렌더러를 전달하는 계약이 없다. 위 Trip·POI·TBT 메타데이터를 기본 지도나 전체 도로망·연속 경로 geometry 공급 계약으로 취급하지 않는다. 정보 조회 결과는 별도 이벤트로 전달된다는 점도 공식 가이드와 일치한다. [목적지·정보 조회 안내](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations), [데이터 타입 목록](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/entry)

JVM 공개 시그니처에는 `getInternalApi(): InternalApi`도 존재하며 그 인터페이스의 메서드는 `updateOccupiedRegion(Rect): void` 하나였다. 공식 공개 메서드 목록에는 이 진입점이 없으며, 지도 출력·데이터 반환 계약도 아니다. 이를 호출하거나 미문서화된 경로로 확장하지 않았다.

## 대안 비교

| 선택지 | 확인한 권리·기술 범위 | 이 프로젝트의 판정 |
| --- | --- | --- |
| Google Maps SDK / Navigation SDK | Maps SDK는 Cloud 프로젝트·결제 연결·API key 설정 및 귀속 표시·콘텐츠 저장/추출 조건을 따른다. Navigation SDK는 별도 제품·정책이다. | Pleos AAOS 호스트 Surface에서의 실제 호환성과 프로젝트별 차량 탑재 사용 조건은 **UNVERIFIED**. Android Auto 지원을 Pleos AAOS 검증으로 대체하지 않는다. |
| MapLibre Native | BSD 2-Clause의 지도 렌더러. tile·style·glyph 등의 공급원과 각 이용 조건은 별도다. | 렌더러 라이선스가 지도 데이터 사용권·무료 tile 서비스·Pleos Surface 호환성을 보장하지 않는다. 이번에 도입하지 않았다. |
| OSM 표준 raster + 기존 Canvas | 현재 화면에 필요한 tile만 요청하고 원본 귀속 표시·캐시·서비스 정책을 지키는 구성 | **이번 정차 PoC의 선택**. 대량 배포·오프라인 지도·상용 SLA 승인으로 확대하지 않는다. |

근거: [Google Maps SDK 설정](https://developers.google.com/maps/documentation/android-sdk/get-api-key), [Maps SDK 정책](https://developers.google.com/maps/documentation/android-sdk/policies), [Navigation SDK 정책](https://developers.google.com/maps/documentation/navigation/android-sdk/policies), [MapLibre Native 라이선스](https://github.com/maplibre/maplibre-native/blob/main/LICENSE.md), [MapLibre source 계약](https://maplibre.org/maplibre-style-spec/sources/), [OSMF tile 정책](https://operations.osmfoundation.org/policies/tiles/).

Google의 현재 Navigation SDK 정책 본문은 검색 결과의 과거 요약과 다를 수 있다. 이번에 연 공식 본문을 기준으로 하며, 이 조사로 Google 지도 전체의 AAOS 사용이 금지되었다고 단정하지 않는다.

## 선택한 OSM 재사용 범위

- [FollowCamera.kt](../../nav-host-poc/src/main/java/com/surpride/navhostpoc/map/FollowCamera.kt): 위치 중심 추적·방향·확대율과 현재 화면에 교차하는 tile 계산. 목적지 overview용 tile 사전 수집을 하지 않는 구성.
- [TileStore.kt](../../nav-host-poc/src/main/java/com/surpride/navhostpoc/map/TileStore.kt): HTTPS 표준 endpoint, 앱 식별 User-Agent, 서버 캐시 헤더·조건부 재검증과 헤더 부재 시 7일 캐시를 사용하는 재방문 캐시.
- [RoadMapRenderer.kt](../../nav-host-poc/src/main/java/com/surpride/navhostpoc/map/RoadMapRenderer.kt): 현재 화면 tile 요청, Canvas 지도·국소 경로·현재 위치와 OSM 귀속 표시. 누락·오래된 tile은 별도 상태로 표시.

운영 범위는 사용자에게 보이는 현재 viewport로 제한한다. 지역/도시·여러 zoom의 선다운로드, 경로 전체 사전 수집, offline pack·재배포를 제공하지 않는다. `© OpenStreetMap contributors`와 저작권 안내를 지도에서 읽을 수 있게 유지하고, 캐시를 우회하지 않는다. OSMF 표준 tile 서버는 가용성 보장이나 SLA가 없으며 대규모·오프라인 용도는 허용하는 별도 공급원 또는 자체 호스팅이 필요하다. [OSMF tile 정책](https://operations.osmfoundation.org/policies/tiles/)

이는 소스 구성 검토다. 실제 화면·네트워크·캐시·중단 상태 검증은 메인 실행 보고서의 증거로 판정한다. raster 원문의 도로·일반 지명·POI 이름은 유지되므로 목적지 이름을 직접 그리지 않아도 위치 추론까지 방지했다고 주장할 수 없다.

## 필요한 후속 확인

1. Pleos 담당자: 외부 내비 앱에 제공하는 **공식 기본 지도 임베드 SDK 또는 허용된 Surface/tile 계약**이 별도로 있는가? 있다면 artifact/version, 문서, SDK 접근 프로젝트 등록 절차·서비스 권한·라이선스를 요청한다.
2. Pleos 담당자: `NAVI_CUSTOM_MAP`의 공개 범위인 화면 요청 외에, 파트너 계약에서 허용하는 지도 재사용·목적지 표시 제어가 있는가? 화면 호출 권한과 지도 사용권을 구분해 확인한다.
3. 배포 전에 선택한 지도 공급원의 차량 탑재·귀속 표시·캐시·예상 트래픽 조건을 확정한다. Google을 선택하면 별도 Cloud/결제/API 등록과 해당 AAOS 사용 조건, MapLibre를 선택하면 tile/style/glyph 공급 계약을 함께 확인한다.
