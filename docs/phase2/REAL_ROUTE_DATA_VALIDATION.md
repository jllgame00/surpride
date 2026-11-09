# SURPRIDE Phase 2B-1 — 실제 장소·자동차 경로 데이터 검증

검증일: 2026-11-09. **판정: READY_WITH_LIMITATIONS**.

실제 공개 장소 5개와 에뮬레이터 기기 위치 스냅샷 수신을 확인했다. 자동차 경로는 인증키·서버 프록시가 없어 **실제 호출 0건, 실제 시간·거리 수신 미검증**이다. 공식 응답 계약을 구현하고 합성 Mock으로 추천 제약을 검증했다. 실제 도로 추천이나 길 안내 완료를 의미하지 않는다.

## 1. 시작 상태와 API 선정

- 시작/종료 브랜치 `main`, HEAD `3e036d75c90010e5470696aab2d867214dd2a6d7`. 시작 작업 트리 clean. 이번 변경은 미커밋 상태로 남겼다. 커밋·Push·원격 변경 없음.
- 기존 58개 테스트, DestinationEngine, DEMO 카탈로그 7곳·저장 형식·전체 흐름, Vehicle 2.3.2, NaviHelper 3.0.5, nav-host-poc, Phase 0B 스크립트를 보존했다.
- 인증 설정 존재 여부만 점검했다. `KAKAO_REST_API_KEY`, `KAKAO_MOBILITY_API_KEY`, `TOUR_API_KEY`, `SURPRIDE_ROUTE_PROXY_URL` 모두 없음. 키 값·개인 위치를 기록하지 않았다.

| 용도 | 공식 API / 선정 근거 | 이번 구현·검증 |
| --- | --- | --- |
| 자동차 경로 | [Kakao Mobility Directions](https://developers.kakaomobility.com/guide/navi-api/directions): 초·미터, 현재 교통, 도로 geometry 제공 | 다음 단계 공급자로 조건부 선정. 공식 JSON 파서·주입형 transport 계약만 구현, 실 HTTP transport 미구성 |
| 장소 검색 | [카카오맵 REST API](https://developers.kakao.com/docs/ko/kakaomap/rest-api): 키워드/카테고리 검색, 이름·좌표·출처 URL | 검색 결과 파서와 합성 fixture 테스트. 인증 요청은 실행하지 않음 |
| 관광 정보 보강 | [한국관광공사 국문 관광정보 서비스_GW](https://www.data.go.kr/data/15101578/openapi.do?recommendDataYn=Y) | 조사만 수행. API 클라이언트·파서 미구현. 자동차 경로 공급자가 아님 |
| 초기 장소 | [서울시 야경명소 정보](https://data.seoul.go.kr/dataList/OA-22579/S/1/datasetView.do), 서울특별시 문화정책과 / 서울문화포털 | 공개 이름·대표 좌표·분류 5개를 정적 카탈로그로 구축. 인증 API 응답 수신이라고 간주하지 않음 |

## 2. 인증·비용·라이선스와 보안

공식 문서 확인 시점 기준이며 계정 콘솔·최신 약관에서 최종 확인해야 한다.

- Directions: REST API 키를 `Authorization: KakaoAK …` 헤더로 전송한다. [공식 가격표](https://developers.kakaomobility.com/price/)는 자동차 길찾기 일일 10,000건, 무료 쿼터 초과 월간 1,000,000건 이하 구간 건당 8원으로 안내한다. 공통 월간 쿼터도 적용되며 무제한 무료가 아니다. 과금 활성화·계정 등록은 수행하지 않았다.
- 장소 검색: REST API 키 및 카카오맵 활성화 필요. [쿼터/요금](https://developers.kakao.com/docs/ko/getting-started/quota)은 키워드·카테고리 각각 일간 100,000건, 추가 호출 2원/건을 안내한다. **개발자 계정에서 처음 활성화한 앱에만 무료 쿼터**가 제공된다. [사용 설정](https://developers.kakao.com/docs/ko/kakaomap/common)을 확인해야 한다.
- TourAPI: 활용신청/인증키 필요, 무료, 개발 자동승인/운영 심의승인, 개발계정 신청 가능 트래픽 1,000으로 게시되어 있다. 적용 시간 단위·실제 승인량·증설은 계정에서 확인한다. 이미지에는 공공누리 1/3유형이 섞이므로 개별 조건 확인이 필요하다. 이번에는 이미지를 사용하지 않았다.
- [카카오 운영 정책](https://developers.kakao.com/terms/ko/site-policies)의 캐시 목적·최신성 제한을 확인했다. 장기 저장, geometry 재배포, 독립 차량용 안내/목적지 비공개 서비스의 허용 여부·표시 의무·공급자별 캐시 TTL은 **확정하지 못했다**. 일반 웹/앱 API 제공이 Pleos 앱 심사 승인이나 독립 내비게이션 사용 허가를 의미하지 않는다. 상용 적용 전 공급자와 Pleos 양쪽 확인이 필요하다.
- 장소 카탈로그는 서울특별시 **공공누리 1유형 출처표시** 조건을 표시한다. 자체 기분 태그/점수는 SURPRIDE의 편집 가정이며 공식 추천 평가가 아니다.

키를 Gradle/BuildConfig/리소스/소스에 넣지 않았다. 앱에 새 네트워크 transport나 INTERNET 권한을 추가하지 않았다. 개발 PC 환경변수는 서버/개발 도구에서만 읽는 방식이 가능하지만 환경변수를 APK 빌드 상수로 옮기면 비밀이 보호되지 않는다. 배포 구조는 **앱 → 인증·제한된 HTTPS 서버 프록시 → 공급자**를 권장한다. 이번에는 서버를 구축하지 않았다.

향후 프록시는 공급자 호스트 allowlist, 서버 측 키, 사용자 인증·호출 제한·timeout·요금 상한을 적용하고 요청 URL/좌표/응답 전문을 로깅하지 않아야 한다. 앱으로 공급자 키를 반환하지 않는다. 경로/위치 데이터는 메모리만 사용하며 영구 캐시 정책은 별도 합의한다. 코드의 5분 신선도 검사와 1분 요청 시각 차이 허용은 **앱 내부 검증 기준**이며 공급자의 저장 허가가 아니다.

## 3. 실제 장소 5개

시범 지역은 서울이다. 아래 좌표는 출처의 대표 위치이며 자동차 진입점·주차장·안전한 정차 지점이 아니다. 차량 진입 검증 전 실도로 추천에 사용하지 않는다. 출처 데이터셋 갱신 표시는 2026-11-08, 개별 시설 운영의 현재성을 보장하지 않는다.

| 장소 | 경도 / 위도 (WGS84) | 출처 분류 | 자체 기분 태그 |
| --- | --- | --- | --- |
| 용양봉저정 공원 | 126.958588093172 / 37.5118003827004 | 공원/광장 | 힐링·위로 |
| 사육신공원 | 126.949411135441 / 37.5134579901091 | 공원/광장 | 생각정리·위로 |
| 이촌한강공원 | 126.970850893942 / 37.517820588578 | 공공시설 | 힐링·신남 |
| 광화문광장(광화문) | 126.976787992565 / 37.5716228232824 | 공원/광장 | 설렘·신남 |
| 하늘공원(월드컵공원내) | 126.886318397942 / 37.5690794796181 | 공원/광장 | 모험·생각정리 |

출처는 위 서울시 데이터셋이며 원본 행 번호 3/15/13/21/22를 ID에 보존했다. 주차·운영시간·접근성은 전부 `UNKNOWN`. 공원/야경/물가 태그는 장소 수준 정보이며 경로 경관이나 아이·반려동물 입장 보장이 아니다. 동행 적합성도 미검증이어서 현재 평가기는 혼자 조건만 허용하고, 다른 동행 조건을 자동 완화하지 않는다.

## 4. 경로 계약과 추천 연결

- `GeoPoint(longitude, latitude)`는 유한 값과 WGS84 범위를 검사한다. 개발 출발지는 카탈로그의 광화문 공개 대표 좌표이며 현재 위치로 표시하지 않는다. 출발지와 같은 대표 좌표의 후보는 제외한다.
- Directions 공식 요청은 `GET https://apis-navi.kakaomobility.com/v1/directions`, `origin=경도,위도`, `destination=경도,위도`. 향후 `priority=RECOMMEND` 사용을 기본으로 검토한다. 최단 시간이 최고의 드라이브라고 가정하지 않는다. 도로 통제·안전 정보를 끄는 옵션을 사용하지 않는다.
- `KakaoRouteParser`: HTTP 상태와 `routes[0].result_code`를 분리한다. `summary.duration` 초, `distance` 미터, `sections[].roads[].vertexes` 경도/위도 쌍을 해석한다. `summary=true`의 roads 생략도 허용한다. 빈/잘못된 JSON, 누락 필드, 음수·소수·과대 시간, 좌표·geometry 오류를 거부한다. 전체 응답을 저장/로그 출력하지 않는다.
- [공식 결과 코드](https://developers.kakaomobility.com/guide/navi-api/reference.html)에 따라 1은 경로 없음, 101~104는 도로 선택/지점 문제, 105~107은 통행 장애로 구분한다. HTTP 401/403 AUTH, 429 RATE_LIMIT, I/O NETWORK, 기타 API, INVALID_RESPONSE, NOT_CONFIGURED를 구별한다. 공급자 메시지 원문은 화면에 전달하지 않는다.
- `RouteRecommendationEngine`은 가용 시간/귀가 마감 중 더 짧은 예산을 적용한다. 최근 방문·필수 장소·동행 조건을 먼저 확인하고, **출발→목적지와 목적지→출발을 각각 조회**한다. 편도는 복귀를 요청하지 않는다. 한쪽 실패 시 왕복 추천하지 않는다.
- 총 시간 = 편도 초 + 복귀 초(왕복 시) + 체류분×60 + 여유분×60. 분 표시는 합계에서 올림하며, 599초+601초처럼 구간별 올림 때문에 예산 내 후보가 제외되지 않도록 테스트했다. 귀가 마감의 남은 분은 보수적으로 내림한다.
- 체류 20/40분, 안전 여유 10분은 사용자에게 표시되는 **계획 가정**이며 API 사실이 아니다. 원래 DestinationEngine은 수정하지 않았다. 시간 제약을 통과한 후보에 대해 기존 기분·장소·최근 방문·알려진 계절/날씨·검증 range 규칙과 선호 점수 계산을 재사용한다. Mock 거리는 차량 range와 비교하지 않으며 SOC로 range를 추정하지 않는다.
- LIVE_API/MOCK 출처를 섞은 왕복은 거부한다. 단위 테스트의 LIVE_API fixture는 코드 분기 검증일 뿐 실 호출 증거가 아니다. 현재 앱은 Unconfigured/Mock만 사용한다. 복귀 값이 실 API로 연결되더라도 현재 시점 교통 조회를 미래 귀가 시각의 확정 예측으로 표현하면 안 된다.

## 5. 위치 API와 UI

`LocationProvider`, `AndroidLocationProvider`, `DevelopmentLocationProvider`를 분리했다. [Android 전경 위치 권한](https://developer.android.com/develop/sensors-and-location/location/permissions) 및 [LocationManager](https://developer.android.com/reference/android/location/LocationManager) 공개 API만 사용한다. 배경 위치/서비스/구독/차량 제어는 없다.

Pleos API 34에서 GPS/network/location feature와 위치 모드 활성화를 확인했다. 최초 `PERMISSION_DENIED` → 정차 검증 화면의 선택 버튼 → 일반 시스템 대화상자 ‘앱 사용 중에만 허용’으로 COARSE/FINE 권한 허용 → 최초 `NO_FRESH_FIX` → 나중에 신선한 기기 위치 스냅샷 `DEVICE` 수신을 확인했다. 60초 이내·accuracy ≤100m 조건을 통과한 값이다. 위성 GNSS 원천, 연속 업데이트, 실차 정확도는 미검증이다. `geo fix`, 강제 권한, AppOps 변경을 사용하지 않았다. 개인 좌표를 출력·저장·추천에 사용하지 않았다.

기존 DEMO 홈의 **‘실제 장소 · 경로 검증’**으로 내부 Data Lab에 진입한다. 기존 DEMO의 설정·저장·모의 주행·공개·기분 지도 흐름은 그대로 유지한다.

- REAL DATA 연결 확인: 서버 미구성 상태를 로컬에서 확인해 `NOT_CONFIGURED`, 추천 생성 안 됨 표시. 실제 HTTP 연결/인증을 시도했다는 뜻이 아니다.
- 명시적 MOCK 경로 평가: 실제 장소 + 합성 시간/거리. 예시 왕복 9분+13분+체류20분+여유10분=52분, 8.4km. 해당 값은 지리/교통 기반 계산이 아니다.
- 목적지 공개 전 이름·좌표를 UI projection에 전달하지 않는다. ‘선정 장소 공개’ 후에만 이름·대표 좌표·출처를 보여준다. APK의 공개 카탈로그 자체를 암호학적으로 숨기는 기능은 아니다.
- 오류 시 자동 DEMO 전환 없음. 검증용 조건 변경은 별도 메모리 세션에만 적용한다. 가용 시간 버튼은 기존 귀가 시각 제한을 명시적으로 대체한다. 기존 MVP 선택·기록은 변경하지 않는다.
- 정차 UX 제한/상태 불명 시 조작을 잠근다. 위치 권한 요청도 정차 화면의 선택 동작에 한정한다. 화면 재구성은 ViewModel로 임시 상태를 유지하지만 프로세스 종료 후 실데이터 검증 세션은 초기화한다. 실제 위치/경로를 Bundle·DEMO 파일에 넣지 않는다.

## 6. 테스트·빌드·Pleos 결과

표준 명령을 유지했다.

```powershell
.\scripts\build-codex.ps1 -Test
adb -s emulator-5554 install --user 10 -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start --user 10 -W -n com.surpride.app/.MainActivity
```

- 최종 `assembleDebug` / `testDebugUnitTest`: **BUILD SUCCESSFUL, 78개 통과(기존58 + 신규20), 실패/오류0**. [집계·APK SHA-256](evidence/real-route/test-summary.txt).
- 신규 테스트: 장소 출처/UNKNOWN, 잘못된 좌표, 공식 JSON 스키마·단위·geometry, 인증/제한/네트워크/경로 없음, 비대칭 왕복·편도·복귀 실패, 초 단위 경계/귀가 마감, 최근 방문·조건 제외, 미구성 실패, Mock/range 분리, 시각·출처 혼합 거부, 공개 전 projection 비밀 유지, 시간 선택 시 마감 대체.
- 개발 중 없는 신규 클래스 참조로 RED 확인, UI의 Companion 이름 충돌 컴파일 오류 수정, 비대칭 올림 경계 테스트의 실제 assertion 실패를 확인한 뒤 수정했다. 이를 환경 장애나 최종 성공과 혼동하지 않는다.
- Pleos `emulator-5554` / 사용자10 / API34: 최종 APK 설치 `Success`, MainActivity COLD 시작 `Status: ok`, DEMO→Data Lab 전환·복귀 확인. 화면의 P/0 km/h 정차 상태에서 수행했다. [설치·시작 관찰 요약](evidence/real-route/install-start.txt).
- REAL 미구성 오류, Mock 비공개/공개, 30분 초과 제외, 위치 상태, 기존 MVP 조건 보존을 UI 계층과 스크린샷으로 확인했다. [UI 검증](evidence/real-route/ui-checks.txt).
- 기존 Vehicle RESPONDING, NaviHelper CONNECTED, OEM 활성 경로 false. 현재 앱 PID의 crash buffer FATAL EXCEPTION 0, 앱 exit-info ANR 항목 0. [런타임 확인](evidence/real-route/runtime-checks.txt). 장시간 안정성/모든 Crash·ANR 부재 보장이 아니다. 실제 주행·길 안내 요청을 하지 않았다.
- [실경로 미구성](evidence/real-route/real-not-configured.png), [Mock 비공개](evidence/real-route/mock-secret.png), [장소 공개](evidence/real-route/mock-revealed.png), [예산 초과](evidence/real-route/time-exceeded.png), [기기 위치 상태](evidence/real-route/gps-status.png).

최종 화면은 기존 MainActivity에 두었다. 전체 기존 SDK 시나리오 재실행, nav-host-poc 재설치, 차량 주행 UX 전환, 모든 화면 재구성/프로세스 복원 조합은 이번 단계에서 재검증하지 않았다.

## 7. 변경 파일

- 수정: `app/build.gradle.kts`(JVM 파서 테스트용 org.json 20250517만 추가), `app/src/main/AndroidManifest.xml`(선택 전경 위치 권한·비공개 Activity), `experience/ExperienceActivity.kt`(Data Lab 진입 버튼).
- 생성: `realdata/RouteModels.kt`, `SeoulPilotCatalog.kt`, `KakaoParsers.kt`, `RouteRecommendationEngine.kt`, `MockRouteProvider.kt`, `LocationProvider.kt`, `RealRouteActivity.kt`, `test/.../realdata/RealRouteTest.kt`.
- 문서/증거: 본 문서, `REAL_ROUTE_IMPLEMENTATION_PLAN.md`, `evidence/real-route/`의 스크린샷·검증 요약. 프로젝트 로컬 `.gradle`의 실행 로그·UI 검증 보조 파일은 Git 제외 상태다.
- Gradle/AGP/Kotlin/SDK/Wrapper 버전, 기존 Vehicle/NaviHelper 구현, nav-host-poc, Phase 0~2A 증거 변경 없음. [org.json 공식 릴리스](https://github.com/stleary/JSON-java/releases/tag/20250517)는 호스트 단위 테스트에서만 사용하며 APK 런타임은 Android org.json을 사용한다.
- 종료 상태: 기존 파일 수정 3개, 신규 파일 19개(코드/테스트8, 문서2, 증거9). `git diff --check` 통과. 저장소의 LF→CRLF 안내는 확인했으며 줄바꿈 일괄 변환을 수행하지 않았다.

## 8. 경로 경험 데이터와 Phase 2B-2

도로 geometry/속도/교통량만으로 ‘즐거움’이나 ‘경관’을 산출하지 않았다. 다음 데이터가 별도로 필요하다: 합법적으로 이용할 수 있는 수변/숲/전망/경관도로 구간 자료, 도로 등급·급커브/경사·주정차 제한·통제, 체류 및 접근 가능한 주차장/진입점, 조도·날씨·계절, 안전성과 경관에 대한 검증된 이용 평가. 데이터 출처·구간 연결·갱신 주기·라이선스가 확보된 후 설명 가능한 점수로 검토한다.

Phase 2B-2 권장 순서:

1. 공급자 계정/권한·자동차 서비스 사용·geometry 표시/저장 조건을 확인하고 서버 측 키를 준비한다. Pleos 심사와 독립 안내 허가는 별도 확인한다.
2. 최소 인증 프록시를 연결한다. `RouteProvider`의 blocking HTTP는 작업 스레드/취소 가능한 비동기로 구현하고, 합성 transport에 LIVE_API를 붙이지 않는다. 응답의 요청 지점 일치·단위·시각·quota 오류를 실제 소량 호출로 검증한다.
3. 장소 대표 좌표를 **검증된 차량 진입점/주차장**으로 보강하고 차량 이용 불가 후보를 제외한다. 운영시간·접근성·동행 조건을 확인한다.
4. 전경 위치 버튼/권한·fresh fix 정책을 실제 기기별로 검증하고, 명시적 사용자 선택 시에만 실제 출발지로 연결한다. 현재는 개발 고정 좌표다.
5. 실제 편도/복귀 응답, 미래 출발/귀가 시각, 체류·안전 여유, 교통 변화/갱신·실패 취소를 검증한다. 경로 요청의 최신성·동시성·쿼터 상한을 관리한다.
6. 실제 데이터 검증 후 별도의 실제 코스 상태/저장 계약을 설계한다. 현재 DemoStateStore에 실장소 ID나 위치를 넣지 않는다. NavigationTemplate·TBT·이탈/재탐색·도착·음성·활성 경로 소유권 구현은 이후 단계다.

**남은 핵심 제한:** 실제 자동차 경로 API/프록시·차량 진입점·현재 운영 정보·실제 경로 즐거움·독립 안내와 목적지 외부 채널 비공개는 미검증이다. 실장소 5개와 위치 API 접근이 확인됐으므로 제한을 명시한 데이터 연동 시연은 가능하지만 ‘실제 도로 추천 완료’로 표시하지 않는다.
