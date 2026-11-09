# SURPRIDE Phase 3A — 실제 경로 기반 동적 안내 PoC

검증일 2026-11-09. **최종 판정: READY_WITH_LIMITATIONS**.

실제 카카오 도로·회전 데이터를 파싱하여 Pleos NavigationTemplate/Surface에서 모의 위치 진행, 거리/도로/회전/ETA 변화, 일시정지·도착·재시작·중단을 확인했다. 실제 GPS 길 안내, 활성 경로 소유권, 지도/안내의 상용 승인 및 모든 채널의 목적지 비공개는 검증하지 않았다. 호스트 네이티브 ETA 카드 잘림도 남아 있다.

## 1. Git·변경 범위

시작은 `main`, HEAD `75cca983dd754847731211392afc37ca15b0bc5a`, clean. 같은 HEAD에 미커밋 변경만 남겼으며 커밋·Push·원격 변경 없음.

- 기존 `app`의 RouteProvider/LiveRouteProvider·추천 엔진·MVP·Vehicle 2.3.2/NaviHelper 3.0.5·설정/저장·Phase 0B 빌드와 이전 검증 자료를 수정하지 않았다.
- 프록시 v1 정상 응답은 보존하고 `include_guidance=true`일 때만 선택형 구조를 추가했다. 기존 Android client는 계속 기존 계약을 사용한다.
- PoC는 기존 별도 패키지 `com.surpride.navhostpoc`에 격리했다. 기존 정적 템플릿/설명도 ‘실험 선택’ 메뉴에서 유지한다. 새로운 네트워크/위치/차량 제어 권한을 PoC에 추가하지 않았다.
- Gradle/AGP/Kotlin/SDK/Car App Library 1.7.0 버전을 변경하지 않았다. PoC에 android.car 선택 라이브러리와 JVM 테스트 의존성만 추가했다.

## 2. 실제 도로 데이터 파싱과 geometry 확보

[Directions 공식 계약](https://developers.kakaomobility.com/guide/navi-api/directions)의 summary=false 응답을 사용했다. roads.vertexes는 경도/위도 쌍, road distance/duration은 미터/초, guide.distance는 이전 안내에서의 거리, road_index는 해당 구간 도로 인덱스다. [공식 회전 코드](https://developers.kakaomobility.com/guide/navi-api/reference.html)를 사용했으며 미지원 코드를 직진으로 바꾸지 않는다.

실제 공개 테스트 경로 1건의 관찰 결과:

| 항목 | 결과 |
| --- | --- |
| 데이터 출처 | KAKAO_DIRECTIONS |
| 도로 구간 / 안내 | 8개 / 9개 |
| geometry | 원본 정규화 122점 → 연결부 중복 제거 후 115점 |
| 거리 / API 예상 시간 | 7,437m / 1,278초 |
| 수신한 안내 코드 | 1, 2, 82, 83, 100, 101 |

이전 Phase의 공개 고정 출발지/목적지 샘플을 재사용했다. 실제 사용자 위치를 요청·전송하지 않았다. 관광지 대표 좌표이며 차량 진입점·주차장 검증값이 아니다. 요청/응답 전문·geometry·최종 좌표를 Git 증거로 남기지 않고 디버그 앱의 private cache에만 공급했다.

`backend/guidance.py`는 도로 순서를 보존하고, section 누적 거리 및 전체 road_index를 만든다. 누락 guides, 비정상 좌표/vertexes, 잘못된 인덱스·거리·순서·크기를 거부한다. guide.name/guidance 원문은 POI·주소가 섞일 수 있어 전달하지 않고 공식 타입/도로명/교통 상태만 보존한다. 이 제한된 모델은 실주행용 전체 안전 정보의 완전성을 보장하지 않는다.

## 3. 모델과 모의 위치 진행

모델은 PoC의 `guidance/`에 두었다. 재사용 가능한 순수 Kotlin이며 Android UI/SDK 호출과 분리했다.

- `RouteGeometry`: 유효 WGS84 좌표, 연결 중복 제거, Haversine 누적 길이, 경로 위 좌표 보간.
- `RouteSegment`: 공식 도로명·미터·초·교통 상태와 구간 geometry.
- `Maneuver`: 공식 코드·누적 거리·도로 인덱스. 구간과 안내의 순서를 검사한다.
- `RouteProgress`: 모의 진행/잔여 거리, 다음 안내까지 거리, 도로 구간, 잔여 시간.
- `NavigationGuidanceState`: READY/RUNNING/PAUSED/ARRIVED/STOPPED와 출처. 목적지 좌표는 private 경로 데이터에만 둔다.

명시적 시작 후에만 타이머가 2초마다 모의 시간10초를 진행한다. 내부 속도는 12m/s이며 차량 속도가 아니다. API 거리 기준 진행 비율을 geometry 위에 보간하고 다음 안내/구간을 갱신한다. 잔여 ETA는 API 총 예상 시간을 잔여 거리 비율로 보간한 **모의 ETA**다. 실시간 교통 재조회·GPS map matching·정확한 구간 속도 모델이 아니다.

메뉴 진입/화면 이탈/UX 제한/상태 불명에서는 일시정지하며 자동 재개하지 않는다. ‘다음 안내’는 재현 가능한 개발용 점프이고 모의 위치만 움직인다. 도착은 내부 이벤트이며 실제 방문이나 OEM 도착 이벤트가 아니다. 화면/프로세스 재시작 시 재생은 처음부터 준비한다.

기존 RouteProvider는 수정하지 않았다. PoC에 앱/SDK를 의존시키는 대신 기존 프록시 계약에 선택형 확장을 적용하고 `backend.nav_poc`가 명시적 로컬 요청 후 stdin으로 private cache에 공급한다. 향후 실제 통합 시 이 모델/파서를 공유 모듈 또는 RouteProvider 확장으로 추출할 수 있으나 이번에는 불필요한 앱 간 아키텍처 확장을 피했다.

## 4. NavigationTemplate 및 Surface 갱신

CarAppService/CarAppActivity/기존 호스트 검증은 그대로다. Car App API 4가 협상됐다. 원래 정적 화면 → 실험 선택 → 실경로 동적 DEMO → 조작 메뉴로 진입하며 선택 화면은 pop하여 스택 깊이를 늘리지 않는다.

실제 확인:

- 수동 시작 전 READY. 시작 뒤 다음 안내 1,335m → 1,215m → 1,095m, 잔여 거리 7,437m → 7,317m → 7,197m, 모의 ETA 1,278초 → 1,258초 → 1,237초로 갱신됐다.
- 다음 안내 점프 후 구간·도로명이 바뀌고 좌회전/우회전이 표시됐다. 실제 차나 시스템 위치는 이동시키지 않았다.
- Surface는 북쪽 기준의 국소 경로 선만 그리며 타일/POI/도로망을 가진 실제 지도가 아니다. visible/stable 영역 안에서 동적 거리/방향/도로명/ETA/교통 상태와 정차 DEMO 문구를 표시한다.
- **호스트 ETA 카드 하단 일부 잘림은 남아 있다.** 앱이 네이티브 카드 배치를 강제로 바꾸지 않고 안전 영역 안에 큰 보조 ETA를 표시했다. 모든 창 크기/글꼴 문제를 해결했다고 주장하지 않는다.
- 호스트가 첫 custom action만 표시해 조작을 표준 ListTemplate 메뉴로 묶었다. 시작/계속·다음 안내·처음 준비·중단·정적 PoC 복귀·종료를 정차 중 사용한다. 메뉴에서는 재생이 일시정지한다.
- 동일 Screen ID는 항상 같은 템플릿 타입으로 refresh한다. invalidate는 2초 간격으로 요청하며 호스트의 병합/제한을 따르고 onGetTemplate 자체에서 재생을 진행하지 않는다. 화면 종료 시 타이머/UX listener/Surface callback을 해제하고 화면을 지운다.

증거: [준비](evidence/ready.png), [동적1](evidence/running-1.png), [동적2](evidence/running-2.png), [안내 변경](evidence/turn-changed.png), [좌회전](evidence/turn-left.png), [우회전](evidence/turn-right.png), [모의 도착](evidence/arrived.png), [다시 준비](evidence/restarted.png), [중단](evidence/stopped.png), [정적 화면 복귀](evidence/static-restored.png).

## 5. 목적지 비공개 범위

확인한 범위는 **이 정차 PoC의 템플릿/Surface**다. 공개용 projection에는 목적지 이름·주소·절대 좌표를 넣지 않고 목적지/도착 핀·전체 경로 overview를 그리지 않는다. 원문 guide POI 이름/안내 문장은 전달하지 않는다. 도로명이 private 목적지명을 포함하면 도로/안전 정보를 임의로 지우지 않고 경로 전체를 거부한다.

Surface는 geometry 누적 길이 기준 다음 최대300m만 표시하고 마지막200m를 제외한다. 실제 목적지/geometry 끝 지점에 약200m 이내로 접근하는 샘플부터 경로 표시를 중단한다. 보고 거리와 geometry 길이가 다를 때도 끝점 노출이 생기지 않도록 회귀 테스트했다. 최종 영역은 도로 선을 생략하되 회전/거리 등 모의 정보는 유지한다.

이는 암호학적 비밀이나 추론 방지가 아니다. 도로명·주변 형태·남은 거리·방향·시각으로 위치를 추측할 수 있다. 여러 모의 시점의 국소 그림을 이어 붙이는 공격도 막지 않는다. 음성/알림/홈/클러스터/최근 목적지까지 비공개라고 검증하지 않았다. 이 PoC는 해당 채널에 새 안내/목적지 메타데이터를 보내지 않는다.

## 6. 정차 UX 및 발생 문제

공식 CarUxRestrictionsManager로 정차용 UI 허용 상태를 확인하며 불명/제한 상태는 잠근다. CarContext는 가상 렌더링 디스플레이를 가리켜 제한 기본값을 반환했다. dumpsys에서 물리 디스플레이0의 `DO:false/UxR:0`을 확인했고, application context로 공식 UX API를 조회하자 false가 수신됐다. 제한을 강제로 false로 만들거나 설정을 변경하지 않았다. 이 연결은 단일 물리 디스플레이인 해당 에뮬레이터에서 확인한 것으로 실제 다중 디스플레이 기기는 별도 검증해야 한다.

개발 중 STOPPED를 같은 Screen의 MessageTemplate으로 바꿨을 때 **BackFlowViolationException/PoC 프로세스 크래시 1건**이 발생했다. 이를 성공으로 집계하지 않았다. STOPPED/UX 불명/데이터 없음에서도 NavigationTemplate을 유지하고 RoutingInfo/TravelEstimate를 제거하도록 수정했다. 최종 빌드에서 중단·복귀를 재검증했다. 과거 크래시 기록과 최종 PID의 관찰 결과를 분리한다.

검토에서 발견된 geometry/API 거리 단위 혼용, 중단 후 안내 메타데이터 잔류, 정적 화면 복귀 시 Surface 등록 문제도 수정하고 테스트/실제 화면으로 확인했다. 데이터 없음/만료/미지원은 안내를 표시하지 않는다.

## 7. 활성 경로 소유권·Pleos 정책

[Pleos 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide)은 APP_MAPS 분류, 단일 활성 경로·명시적 전환, 주행 UX/안전 정보·최소 권한을 요구한다. Inactive/Background 상태의 실제 안내 허가를 이 실험에서 추정하지 않는다. APP_MAPS와 AndroidX NAVIGATION service category는 서로 다른 선언이며 기존 PoC에 유지했다.

[CarAppFocusManager](https://developer.android.com/reference/android/car/CarAppFocusManager)의 소유권 요청/상실 처리는 실제 독립 내비게이션 단계에서 필요하다. 이번에는 focus 요청/포기, NavigationManager.navigationStarted/updateTrip, 실제 음성/TBT notification/클러스터 출력, NaviHelper 요청/취소를 하지 않았다. 시작 전/종료 후 기존 앱에서 OEM 활성 경로 false를 확인하며 이중 실제 안내를 하지 않는다. 다른 앱의 경로 전환에 대한 연속 감시/소유권 경합은 미검증이다.

[Android Navigation 가이드](https://developer.android.com/training/cars/apps/navigation)와 [지도 Surface 계약](https://developer.android.com/training/cars/apps/library/draw-maps)은 템플릿/표시와 메타데이터 계약을 제공할 뿐 경로 엔진/지도/심사 승인을 대신하지 않는다. GPS 안내·onAutoDriveEnabled 기반 정식 제출 시뮬레이션·활성 Trip 제공을 구현한 앱이라고 주장하지 않는다.

## 8. 라이선스·저장·남은 지도 과제

공식 API가 geometry/guides를 반환함을 확인했다. [카카오 운영 정책](https://developers.kakao.com/terms/ko/site-policies)의 권리 고지·캐시 목적/최신성·재배포 제한과 [가격/이용 안내](https://developers.kakaomobility.com/price/)를 확인했지만, **별도 Surface의 독립 내비 지도 표시·상용 이용·geometry 저장/재배포에 대한 개별 허가 범위는 미확인**이다. 정책 적합성을 FEASIBLE/승인으로 승격하지 않는다. 이번은 비공개 개발 에뮬레이터 검증이며 외부 공개/마켓 제출을 하지 않았다.

원시 경로는 Git에 넣지 않고 private cache에만 보관하며 파서에서 1시간 만료를 검사한다. 공급자 캐싱 허가 TTL이라는 뜻은 아니다. 실제 사용자 위치가 아닌 공개 샘플만 썼다. PoC APK에는 키/개발 토큰이 없고 경로 통신 권한도 없다. 향후 개인정보 암호화·보관·HTTPS·지도 표시/출처 조건과 실제 차량 진입점/주차 지점 검증이 필요하다.

실제 지도·GPS·map matching·이탈 판단/재탐색·구간별 ETA·차선/교통 제한/안전 정보 완전성·오프라인·음성·도착 판정·경로 소유권을 별도로 구현해야 한다. 현재 geometry 보간과 안내 거리 매핑은 개발용 근사이며 실제 주행에 사용하면 안 된다.

## 9. 테스트·빌드·에뮬레이터

- 기존 앱 **89개**, FastAPI **33개(기존29+신규4)**, PoC **11개**. 빌드/테스트 상세 및 최종 APK hash는 [테스트 집계](evidence/test-summary.txt).
- 신규 검증: 좌표/vertexes/순서/섹션 누적·인덱스, geometry 거리/보간, 다음 안내/도로 변경, 일시정지·재현·도착·중단, malformed/만료/미지원/traffic 경고, 목적지 projection 및 geometry 단위가 달라도 끝점 생략.
- 기존 네트워크/키 없음/인증/quota/timeout 및 backend HTTP 계약 회귀를 함께 실행했다. 새 guides 계약의 비정상 입력은 합성 응답으로 검증했으며 실제 API 오류를 고의로 유발하지 않았다.
- 기존 앱은 `-Test -RerunTasks`로 89개를 새로 실행했다. 최종 PoC PID의 crash buffer FATAL/ANR 관찰은 0이며 과거 개발 중 크래시 1건과 구분한다. [런타임/정리 확인](evidence/runtime-health.txt).
- Pleos v2.1.0/API34, emulator-5554/사용자10, 최종 PoC 설치 Success 및 COLD 실행을 확인했다. 실제 데이터 읽기와 화면 갱신은 [수명주기/상태 로그](evidence/lifecycle.txt), [UI 검증](evidence/ui-validation.txt), 화면 증거로 확인한다. 템플릿 내부 텍스트는 UI 계층에서 완전히 노출되지 않아 시각 검토를 병행했다.
- 재생 위치는 앱 내부 값이다. 에뮬레이터 `geo fix`, 차량 제어/set, OEM 경로 변경, 보호된 앱/시스템 설정 변경은 없다.
- Car App API4 호환 메서드 deprecation 및 FastAPI TestClient 경고가 남았다. 장시간 안정성, 실제 주행 UX 전환·다중 display·여러 글꼴/화면 비율은 미검증이다.

## 10. 재실행·변경 파일·다음 단계

프로젝트 루트에서 기존 스크립트를 사용한다. API 요청은 명시적으로 1건 발생한다.

```powershell
.\scripts\build-codex.ps1 -Test
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
.\scripts\build-nav-host-poc.ps1 -Test
adb -s emulator-5554 install --user 10 -r nav-host-poc/build/outputs/apk/debug/nav-host-poc-debug.apk
# 별도 터미널: 기존 서버 .env/환경변수 사용, 키 출력 금지
backend/.venv/Scripts/python.exe -m backend.dev serve --mode live
# PoC 설치 후 일반 디버그 private cache에 공개 경로 공급
backend/.venv/Scripts/python.exe -m backend.nav_poc --request-public-route --adb adb
adb -s emulator-5554 shell am start --user 10 -W -n com.surpride.navhostpoc/androidx.car.app.activity.CarAppActivity
```

기존 앱에서 OEM 활성 경로 없음 확인 → PoC ‘실험 선택’ → 실경로 동적 DEMO → ‘조작’ → 시작. 메뉴 진입은 일시정지, 메뉴의 뒤로 화살표는 일시정지 상태로 복귀한다. 조작 버튼이 숨겨지면 화면을 탭해 다시 표시한다. ‘다음 안내’는 모의 점프다. 실제 차량/위치/안내가 시작되지 않는다. 종료 시 서버와 임시 private cache를 정리한다.

수정: `backend/app.py`, `nav-host-poc/build.gradle.kts`, PoC Manifest/MockNavigationService, `scripts/build-nav-host-poc.ps1`(-Test 옵션).

생성: `backend/guidance.py`, `backend/nav_poc.py`, `backend/tests/test_guidance.py`, PoC `guidance/GuidanceModels.kt`, `GuidanceParser.kt`, `GuidanceSimulator.kt`, `DynamicNavigationScreen.kt`, `PocParkedMonitor.kt`, `GuidanceTest.kt`, 본 보고서/계획/증거. 실제 경로·인증 파일은 추적하지 않는다.

종료 Git 상태는 기존5개 수정·신규26개이며 diff/신규 텍스트 공백 검사를 통과했다. 설치된 최종 PoC APK와 기록 hash가 일치한다. 실제 자격 바이트는 추적 대상 파일/APK에서 발견되지 않았다. 종료 시 private 경로 cache와 호스트 세션을 지우고 서버를 중지했다. 기존 MainActivity/SDK 연결과 OEM 활성 경로 false를 다시 확인했다. 사용자 `.env`와 기존 자료는 보존했다.

다음 단계는 (1) 공급자/Pleos의 지도·실안내·목적지 숨김 정책 확인, (2) 차량 접근 가능한 지점/안전 정보 확충, (3) 호스트 ETA/controls 및 실제 화면 비율 협의, (4) 활성 경로 소유권·중단/전환 계약, (5) 동의된 실제 GPS·map matching/이탈·재탐색이다. 이번 PoC의 성공을 실도로 안내 성공으로 보고하지 않는다.
