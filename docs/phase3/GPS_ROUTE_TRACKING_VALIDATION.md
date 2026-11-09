# Phase 3B-1 GPS 경로 추적 검증

검증일: 2026-11-09. **판정: READY — 정차 에뮬레이터 개발용 위치 추적 범위.** 실제 GPS 기반 도로 안내·실차 안전성·마켓 정책 승인을 뜻하지 않는다.

동일 요청 재검증: 구현과 최초 증거가 이미 `c62d574`에 포함된 상태에서 다시 확인했다. 1~10절은 최초 실행 기록이며, 이번 작업의 Git 상태·신규 검증 범위는 11절에 별도로 기록한다.

## 1. 기준과 변경 범위

시작: `main`, HEAD `fe42f3d9bed86d70ed444eefaa5ae632361a4870`, working tree clean. 종료에도 브랜치/HEAD를 유지하며 수정3개·신규27개(문서/증거 포함)다. 커밋·Push·원격 변경은 하지 않았다. 기존 main app, Vehicle 2.3.2, NaviHelper 3.0.5, Phase 3A 재생 화면, 빌드 스크립트와 검증 자료를 보존했다. 의존성/Gradle/SDK 버전 변경은 없다.

기존 앱의 추천용 LocationProvider와 결합하지 않고, 독립 PoC 안에 `PositionProvider` 계약을 두었다. `AndroidPositionProvider`와 `FakeLocationProvider`를 명시적으로 구별한다. 경로는 기존 GuidanceParser/RouteGeometry/RouteProgress를 재사용한다.

## 2. A — 실제 에뮬레이터 위치 수신

- 대상: Pleos Connect v2.1.0/API34, `emulator-5554`, 사용자10. 실제 차량은 연결/주행하지 않았다.
- 공식 Android `LocationManager.GPS_PROVIDER`, `requestLocationUpdates`/`removeUpdates` 사용. Pleos 전용 비공개 위치 API나 GMS 의존성을 추가하지 않았다. 이는 이 이미지의 Android 위치 API 지원 확인이며 다른 차종/플랫폼 지원 보장이 아니다.
- PoC에 COARSE/FINE만 추가했다. 배경 위치/FGS/특권 권한은 추가하지 않았다. CarContext.requestPermissions → 시스템의 **앱 사용 중에만 허용**을 선택했다. 강제 grant/appops/test-provider는 없다.
- 미허용 상태에서 `PERMISSION_DENIED`, 안내 UNKNOWN을 확인한 후 정상 권한 절차로 precise=true를 수신했다. [미허용 화면](gps-evidence/permission-denied.png), [초기 수명주기 로그](gps-evidence/initial-lifecycle.txt).
- 경도·위도·정확도·UTC 시각·부팅 후 경과 시각을 모델에 담고, speed/bearing/bearing accuracy는 Android has*가 참일 때만 사용한다. 시각 순서/신선도는 [공식 Location 계약](https://developer.android.com/reference/android/location/Location)에 따라 단조 증가 elapsedRealtime 기준이다. wall clock 변화는 진행률에 사용하지 않는다.
- 공식 [emulator console의 geo fix](https://developer.android.com/studio/run/emulator-console)로 공개 테스트 경로의 앞부분만 입력했다. **LocationManager 콜백을 실제 수신했지만 좌표 입력 자체는 재현 데이터**다. Fake 콜백이나 실차 GNSS 측정으로 집계하지 않는다.
- 정확도5m가 수신됐고, 약100→1,578m 진행 및 다음 안내 변경을 관찰했다. 경로 밖 입력에서 OFF_ROUTE_CANDIDATE→OFF_ROUTE, 복귀에서 REJOINING→ON_ROUTE, 약1,628m/다음237m를 확인했다.
- 메뉴 진입/일시정지/다른 Activity 전환 시 UNSUBSCRIBED, 복귀/재시작 시 SUBSCRIBED와 새 콜백을 확인했다. 오래된 lastKnownLocation은 실제 기기 콜백에서도 거부됐다. Home 키만으로는 NaviContainer가 계속 보였으므로 이를 배경 전환 성공으로 세지 않았다. MainActivity 전환으로 구독 해제를 확인했다.
- geo fix 입력을 멈춰도 GNSS가 같은 좌표에 새 시각을 붙여 콜백을 계속 보낼 수 있다. 이를 위치 상실로 오판하지 않는다. 진짜 GNSS 신호 상실/approximate-only/설정에서 GPS 비활성화/권한 사후 철회는 기기에서 미검증이다.

## 3. B — Fake 및 순수 로직 검증

`PositionSession`은 구독 중복을 막고 세대 번호로 해제 후 늦은 콜백을 버린다. Fake가 전달하는 fix는 항상 FAKE다. 자동 테스트에는 구독/해제/재시작, 권한·provider 실패 이벤트, 정확도/NaN/오래됨/미래/순서 역전, 위치 상실과 재획득을 포함한다. Android 프레임워크의 모든 실패를 instrumentation으로 재현한 테스트라는 뜻은 아니다.

교차 구간의 무방향 모호성, 이전 경로와의 연속성, 반대 방향, 큰 점프, 좌표 반복, 이탈/재진입, 다음 안내 변경, 목적지 projection을 합성 경로로 검증했다. 검토에서 재진입이 점프 검사를 우회하고 상실 뒤 오래된 분기 선택을 재사용하는 문제를 발견했다. 세 회귀 테스트의 **실패(27개 중3실패)**를 확인한 뒤 수정하여 전체 통과했다. 최종 읽기 전용 검토에서 해당 Important 문제의 해결을 확인했다.

## 4. C — 실제 도로 geometry 기반 매칭

기존 live 프록시와 일반 debug private cache 공급 절차로 카카오 공개 테스트 경로를 **이번 Phase에서 1회 새 조회**했다. `KAKAO_DIRECTIONS`, 도로13개/안내14개/원문 geometry133점, API 거리7,910m/시간1,332초를 확인했다. 공개 테스트 좌표로만 요청했고 키는 서버 인증에만 사용하여 출력하지 않았다. 좌표 원문은 Git/호스트 증거에 저장하지 않는다.

매칭 절차:

1. 각 유효 polyline 선분에 로컬 평면 투영하여 최근접점/횡방향 편차를 구한다. 누적 Haversine 길이로 geometry 진행거리를 계산한다. 연속 중복 좌표는 기존 RouteGeometry가 제거한다.
2. 정확도 범위 내 후보를 거리, 신뢰 가능한 방위, 이전 진행거리로 평가한다. 비슷한 점수의 서로 다른 순회 구간은 AMBIGUOUS로 둔다. 최초/위치 상실 후/재진입에는 새 획득으로 모호성을 다시 검사한다.
3. 진행률 역행/급변을 검사하고 작은 흔들림은 이전 진행률에 고정한다. 재진입에서도 마지막 정상 위치 이후 시간에 따른 점프/역행 검사를 유지한다. 불확실한 상태는 진행률/회전/ETA를 비운다.
4. geometry 진행 비율을 API 총거리로 환산하고 guide offset과 비교한다. **도로별 정밀 스냅/차선 매칭이 아닌 전체 비율 근사**다. geometry/API 거리 불일치 테스트가 있지만 실제 교차로 회전 시점의 정확도를 보장하지 않는다.

`MatcherConfig`의 개발 가정:

| 항목 | 현재 값과 용도 |
|---|---|
| 신선도/정확도 | 5초 이내, 정확도0초과~50m; 그 밖은 UNKNOWN |
| 경로 회랑 | 정확도×2를15~60m로 제한 |
| 이탈/재진입 | 연속3회/2회 관측 |
| 점프 허용 | 55m/s×정상 fix 간 시간 +25m +정확도×2 |
| 역행/흔들림 | 역행15m 또는 정확도×2 초과 거부; 작은 전진은 deadband |
| 방위 사용 | 속도2m/s 이상, 방위 정확도45° 이내일 때; 120° 초과 반대 방향 거부 |

수치는 안전 인증 기준이 아니다. Android accuracy는 약68% 신뢰 반경이므로 ×2를 보장된95% 확률로 해석하지 않는다. 실험 입력의5m/합성 오차·교차·점프 테스트를 위한 조절 가능한 시작값이며, 실제 환경별 오차 로그의 동의된 수집과 튜닝이 필요하다. 전 구간 선형 탐색이므로 대규모 geometry의 성능 검증/공간 인덱스도 남았다.

## 5. D — NavigationTemplate와 화면 검증

기존 정적 PoC → 실험 선택 → **GPS 경로 추적 · 정차 실험**으로 진입한다. 기존 내부 위치 재생 DEMO와 별도 화면이다. 명시적으로 GPS 시작을 선택하기 전에는 구독하지 않는다. emulator model/product와 공식 CarUxRestrictionsManager의 정차 허용을 모두 확인하며 불명/제한이면 구독·표시를 중단한다. 다중 display/실제 주행 전환은 미검증이다.

화면 타입은 계속 NavigationTemplate이다. ON_ROUTE일 때만 RoutingInfo를 제공하고, UNKNOWN/이탈/역행/점프/일시정지에서는 제거한다. 2초 간격 invalidate 요청 및 Surface visible/stable 영역을 사용한다. 호스트가 요청을 모두 같은 속도로 렌더링한다고 보장하지 않는다.

| 실제 시각 증거 | 확인 사항 |
|---|---|
| [추적](gps-evidence/final-tracking.png) | DEVICE_API, 진행거리/다음 안내/도로명/정확도/국소 선 |
| [안내 변경](gps-evidence/final-turn-changed.png) | 다음 안내가 오른쪽 직진으로 변경 |
| [이탈](gps-evidence/final-off-route.png) | OFF_ROUTE, native 회전 카드 및 ETA 제거 |
| [재진입](gps-evidence/final-rejoined.png) | ON_ROUTE 복귀 |
| [일시정지](gps-evidence/final-paused.png) | UNKNOWN, 구독 중지 및 안내 제거 |
| [재시작](gps-evidence/restarted.png) / [기존 화면 복귀](gps-evidence/static-restored.png) | 새 구독 후 재획득, 기존 정적 Surface 복원 |

기존 ETA 잘림은 OEM 소유 native TravelEstimate 카드의 하단 배치 문제다. GPS 실험 화면에서는 native 목적지 ETA 카드를 넣지 않고 Surface 안전 영역 안에 **API 기록 시간×잔여거리 비율**을 표시한다. 교통 재조회/실시간 ETA가 아니다. 원래 정적/Phase3A 화면을 무리하게 바꾸지 않았으며 모든 host의 잘림을 해결했다고 주장하지 않는다.

최종 목적지 이름/주소/절대 좌표/도착 핀/전체 경로는 제공하지 않는다. 기존 공개용 projection을 공유하며 다음 최대300m 국소 선, 마지막200m 및 실제 끝점 주변 제외 규칙을 유지한다. 도로명이 목적지명을 포함하면 기존 파서가 경로 전체를 거부한다. 도로/안전 정보를 삭제하거나 허위 이름을 넣지 않는다.

도로명·방향·거리와 국소 선을 이어 붙여 장소를 추측할 수 있으므로 완전한 비밀 보장은 아니다. 마지막 영역 숨김의 합성 회귀는 통과했으나 **이번 기기 GPS 재현은 앞부분만** 실행했다. 음성/알림/클러스터/최근 목적지를 모두 비공개로 검증한 결과도 아니다.

## 6. F — 경로 소유권과 호스트 노출 사전 감사

| 계약 | 확인 내용 / 이번 동작 |
|---|---|
| AndroidX NAVIGATION | CarAppService category 및 NAVIGATION_TEMPLATES/ACCESS_SURFACE 유지. 지도 엔진/정식 지도 요건 충족을 뜻하지 않음 |
| Pleos APP_MAPS | Activity/마켓 category와 AndroidX service category는 별개. 기존 선언 보존 |
| CarAppFocusManager | NAVIGATION 포커스 요청/소유권 상실/반납 계약 존재. **요청하지 않음** |
| NavigationManager | 실제 시작은 사용자 동작과 callback 등록 후 navigationStarted, 종료/도착은 navigationEnded, onStopNavigation 수신 시 안내·updateTrip 중지. **이번에는 호출/등록하지 않음** |
| updateTrip/Destination | 목적지·step·estimate가 cluster/HUD 등으로 전달될 수 있음. Trip.Builder는 목적지 생략을 허용하지만 Pleos host의 수용/심사/전체 채널 비공개를 보장하지 않음 |
| 정차/주행 UX | 정차 실험으로 한정. 활성/비활성/배경 실제 안내 허용으로 해석하지 않음 |

근거: [Android Navigation 가이드](https://developer.android.com/training/cars/apps/navigation), [NavigationManager](https://developer.android.com/reference/androidx/car/app/navigation/NavigationManager), [Trip.Builder](https://developer.android.com/reference/androidx/car/app/navigation/model/Trip.Builder), [CarAppFocusManager](https://developer.android.com/reference/android/car/CarAppFocusManager), [Pleos Maps/Navigation 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide). 현재 사용 중인 AndroidX1.7.0 실제 classes.jar의 Trip.Builder bytecode에서도 목적지 최소 개수 검사는 없고 destination/estimate 개수 일치 검사가 있음을 확인했다. **Trip을 실제 host에 보내는 실험은 하지 않았다.**

Pleos는 단일 활성 경로, 명시적 전환, 주행 조작 제한을 요구하며 비활성/배경 안내를 금지한다. 이 정차 개발 표시 실험을 정식 안내로 배포/승인받았다고 해석하지 않는다. OEM 활성 경로 false를 기존 NaviHelper 진단으로 전후 확인했다. 실제 안내/음성/TBT 알림/계기판 출력/포커스 요청/재탐색은 시작하지 않았다. 포커스 경합 및 host의 자동 소유권 처리까지 검증한 것은 아니다. OEM의 보호 기능을 수정하지 않았다.

## 7. 테스트와 실행

- 기존 앱 **89/89**, PoC **27/27(기존11+신규16)**, FastAPI **33/33**: 합계 **149개**, 실패0. main은 `-RerunTasks`로 새 실행했고 PoC 신규 및 회귀 테스트도 실행했다. 단순 UP-TO-DATE 결과만으로 신규 테스트 성공을 선언하지 않았다.
- `build-codex.ps1 -Test -RerunTasks`, `build-nav-host-poc.ps1 -Test`, `pytest backend/tests -q -p no:cacheprovider` 성공. FastAPI TestClient/기존 Car App deprecated API 경고는 남았다. dependency 업그레이드는 하지 않았다.
- debug APK 설치 Success, CarAppActivity COLD 실행(438ms) 및 실제 화면 갱신 확인. 최종 PID31076에서 위치 콜백271건, crash buffer의 FATAL0건/패키지 exit-info의 ANR0건을 관찰했다. 장기 안정성을 보장하는 수치가 아니며 과거 Phase3A 개발 크래시와 구분한다.
- [테스트/해시](gps-evidence/test-summary.txt), [최종 위치·수명주기 로그](gps-evidence/final-lifecycle.txt), [런타임/정리](gps-evidence/runtime-health.txt).
- git diff 및 신규 텍스트 공백 검사, 실제 인증 바이트 미포함 검사(Git-visible 파일/설치된 PoC와 일치하는 APK)를 수행했다. 이번 변경의 공백 오류는0이고 과거 증거 파일13줄의 공백은 보존했다. 임시 private 경로 cache와 호스트 서버 세션을 제거하고 서버를 종료했다. 사용자 `.env`는 보존했다. 기존 파서의1시간 만료 검사는 공급자 캐싱 허가 TTL을 뜻하지 않는다.

## 8. 재실행

```powershell
.\scripts\build-codex.ps1 -Test
.\scripts\build-nav-host-poc.ps1 -Test
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
adb -s emulator-5554 install --user 10 -r nav-host-poc/build/outputs/apk/debug/nav-host-poc-debug.apk
# 별도 터미널: 기존 무공개 로컬 live proxy/환경변수 사용
backend/.venv/Scripts/python.exe -m backend.dev serve --mode live
backend/.venv/Scripts/python.exe -m backend.nav_poc --request-public-route --adb adb
adb -s emulator-5554 shell am start --user 10 -W -n com.surpride.navhostpoc/androidx.car.app.activity.CarAppActivity
# 정차 확인/일반 위치 권한 허용/GPS 시작을 화면에서 선택한 뒤에만 실행
backend/.venv/Scripts/python.exe -m backend.gps_replay --public-route-prefix --adb adb
backend/.venv/Scripts/python.exe -m backend.gps_replay --public-route-prefix --scenario off-route --adb adb
backend/.venv/Scripts/python.exe -m backend.gps_replay --public-route-prefix --scenario rejoin --adb adb
```

입력 도구는 fixed emulator/사용자/PoC cache, 명시적 공개 테스트 endpoint/source/1시간 신선도를 확인한다. 사용자 개인 경로를 재현하지 않는다. geo fix는 에뮬레이터의 공유 위치 입력이므로 OEM 활성 경로가 없는 개발 AVD에서만 사용한다. 원래 위치를 추정하여 복원하지 않았고 AVD는 마지막 공개 테스트 위치에 남는다. 종료 메뉴로 구독 중지/PoC 종료 후 private 경로와 로컬 서버 세션을 정리한다. 사용자 `.env`/기존 cache/설정은 보존한다.

## 9. 변경 파일

- 수정: PoC `AndroidManifest.xml`(일반 위치 권한), `MockNavigationService.kt`(실험 선택), `guidance/GuidanceSimulator.kt`(공유 국소 경로 함수 위임).
- 추가: `GpsTrackingScreen.kt`, `tracking/PositionProvider.kt`, `AndroidPositionProvider.kt`, `RouteMatcher.kt`, `guidance/RouteWindow.kt`.
- 추가 테스트: `tracking/LocationSessionTest.kt`, `RouteMatcherTest.kt`.
- 추가 개발 도구: `backend/gps_replay.py`.
- 문서: 이 보고서, `GPS_ROUTE_TRACKING_PLAN.md`, `gps-evidence/`의 비식별 메타데이터/공개 테스트 화면. 기존 앱/backend API 구현 및 Phase0~3A 자료는 변경하지 않았다.

## 10. E/G — 미검증 위험과 다음 단계

실차 GNSS/터널/도심 반사/평행도로/복잡한 루프/긴 경로 성능, 실제 주행 UX 전환·여러 display, GPS 활성화/권한 사후 철회, 실제 speed/bearing 정확도, GNSS 신호 단절은 남았다. 현재 matcher의 거리·교차로 안내·ETA는 개발 근사다. 위치 상실을 추측한 가짜 진행/도착 이벤트나 자동 재탐색을 발생시키지 않는다.

다음 단계는 (1) 카카오 geometry 표시/저장·독립 내비 이용 허가와 Pleos 정차 PoC/활성 안내 심사 경계 확인, (2) segment/guide 위치와 거리의 정밀 대응·도시 교차/평행 도로 재획득·계측 기반 임계값/성능 개선, (3) 목적지 없는 Trip의 host/cluster/알림 노출을 별도 승인된 실험으로 감사, (4) 포커스 상실·사용자 취소·단일 경로 소유권 계약을 확정한 뒤 재탐색/실제 ETA/음성 설계다. 실제 사용자 위치의 저장·공유는 동의/보관 정책과 함께 별도로 설계해야 한다.

기존 카카오 표시/저장/재배포 허가의 미확인 사항은 유지한다. [카카오 운영 정책](https://developers.kakao.com/terms/ko/site-policies) 및 이전 Phase의 라이선스 조사를 넘어 독립 내비 상용 표시 허가를 새로 확보한 것은 아니다.

## 11. 동일 요청에 대한 재검증

재검증 시작은 `main / c62d574e50125e0928674663b0a7ee5f993c85bf / clean`이다. 위치 Provider, 매칭, GPS 화면과 최초 실경로/DEVICE_API 증거가 이미 들어 있어 구현을 중복 추가하거나 변경하지 않았다. 종료 HEAD도 같으며 보고서1개 수정·증거2개 추가만 있다. 커밋·Push하지 않았다.

- 기존 앱89개·PoC27개·FastAPI33개, **149개를 모두 새로 실행하여 통과**했다. main 및 PoC 각각 assembleDebug와 테스트를 `--rerun-tasks`로 수행했다. 기존133개와 신규16개가 포함된다. PoC는 기존 빌드 스크립트와 같은 JDK/프로젝트 전용 Gradle·Android 홈을 프로세스 안에서만 적용했다.
- 첫 PoC 명령은 PowerShell이 따옴표 없는 dotted `-P` 옵션을 추가 task로 분리하여 실패했다. 문자열 인수 배열로 수정한 뒤41개 task를 실행해 성공했다. SDK/프로젝트 코드·전역 설정을 바꾸지 않았다.
- Pleos/API34/사용자10에서 재빌드 APK 설치 Success, CarAppActivity COLD402ms를 확인했다. 설치 APK와 빌드 APK SHA256이 일치한다. 현재 PID29356 FATAL0건, 패키지 exit-info ANR0건을 관찰했다.
- 이전에 임시 실경로 cache를 제거했으므로 이번에는 [경로 데이터 없음 화면](gps-evidence/revalidation-no-route.png)을 확인했다. GPS 시작 선택에도 구독하지 않고 NO_FIX/NOT_STARTED 및 진행률·회전·ETA UNKNOWN을 유지했다. 정상 종료/SCREEN_DESTROYED도 확인했다.
- **이번 재검증에서는 외부 API 요청 및 geo fix 입력을 추가 실행하지 않았다.** 실제 geometry를 사용한 위치 변화/안내 변경/이탈/재진입의 기기 검증은 앞 절의 기존271개 DEVICE_API 콜백 및 화면 증거에 근거한다. Fake·합성 경로 자동 테스트와 새 기기 위치 재현을 혼동하지 않는다.
- 기존 main의 NaviHelper 연결과 OEM 활성 경로 false를 다시 확인했다. 실제 안내/음성/포커스 요청/경로 전환은 없으며 서버·세션·원시 경로를 새로 만들지 않았다. 실제 차량·정책·지도 라이선스·정밀 매칭의 제한은 유지한다.

이번 명령·테스트 집계·현재 APK hash·기기 로그는 [재검증 증거](gps-evidence/revalidation.txt)에 기록했다. READY는 최초 위치 변화/화면 검증을 보존한 개발 PoC 판정이며, 이번 데이터 없음 실행만으로 GPS 성공을 새로 선언한 것은 아니다.
