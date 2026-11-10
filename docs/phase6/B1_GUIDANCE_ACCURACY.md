# SURPRIDE Phase 6B-1 — 안내 거리와 Geometry 무결성

2026-11-10 · **READY_WITH_LIMITATIONS** · **GUIDANCE_ACCURACY = VERIFIED_CONTRACT**

이 판정은 입력·거리 환산·Screen 출력 계약에 한정한다. 실제 Host 활성 회전 카드와 실음향은 이번에 끝까지 확인하지 못했다. 실차 안전성 인증, 목적지 완전 비공개 또는 배포 승인이 아니다.

## 1. 기준과 변경 범위

- 시작: `main / b2e0e47ddc78e2a78a45161bbd11964518ac36de`, 미커밋 변경 없음. 과거 감사 HEAD `c0d81ee`를 현재 HEAD로 간주하지 않았다.
- [감사 A01/A02/A05](CODEBASE_AUDIT.md), [B1 계획](REFACTOR_PLAN.md), [R01](RELEASE_RISK_REGISTER.md)을 현 코드와 대조했다.
- 변경은 PoC guidance 모델·파서·matcher·camera·simulator, backend guidance 경계, Screen GPS 순서 처리와 관련 테스트에 한정했다. 새 SDK·DI 계층·UI 재설계 없음. 메인 앱 제품 소스 변경 없음.
- 커밋·Push·이력 변경 없음. 정차/에뮬레이터/명시적 시작/소유권/출처/waypoint LIVE CLOSED 제한을 유지했다.
- 종료도 같은 `main / b2e0e47ddc78e2a78a45161bbd11964518ac36de`다. 추적 파일 15개 수정·신규 파일 10개(테스트 5, 결과 문서 1, 공개 시험 화면 3, 검증 요약 1)를 미커밋으로 남겼다. 시작 시 추적 670개 중 범위 밖 655개는 바이트가 같다. `git diff --check` 오류 없음.

## 2. A01 원인과 수정

기존 `RouteMatcher.accept`는 `geometry 진행 / 전체 geometry 길이 × 전체 API 거리`를 사용했다. 각 도로의 geometry/API 비율이 다를 때 도로 경계와 guide offset이 어긋났다. camera와 simulator에도 같은 전역 환산이 있었다.

[`GuidanceRoute`](../../nav-host-poc/src/main/java/com/surpride/navhostpoc/guidance/GuidanceModels.kt)는 원 road별 geometry와 API 거리를 유지한다. `geometryRoadIndices`는 모든 실제 선분의 원 `roadIndex`를 보존하고, 도로 시작 누적 geometry/API 거리를 별도로 계산한다. `RouteMatcher`는 `distanceAtGeometry`, `roadIndexAtGeometry`, `nextManeuver`를 사용한다.

앵커가 없는 도로 i의 기본 환산은 다음과 같다.

```text
전체 API 진행 = 이전 도로 API 거리 합
              + (현재 도로 내부 geometry 진행 / 현재 도로 geometry 길이)
                × 현재 도로 API 거리
```

검증된 guide vertex가 있으면 도로 시작·guide·도로 끝 사이를 각각 선형 환산한다. 역환산 `geometryAtDistance`와 guide의 `geometryOffset`도 같은 앵커를 사용한다. 전체 비율 보정 상수는 없다.

## 3. Maneuver 및 앵커 계약

[`backend/guidance.py`](../../backend/guidance.py)는 guide의 이전 안내부터의 `distance`를 section 누적 offset으로 변환하고 section의 `road_index`를 전체 road index로 변환한다. [카카오 공식 Directions 문서](https://developers.kakaomobility.com/guide/navi-api/directions)의 `x/y`, `distance`, `road_index`, `vertexes` 정의를 대조했다.

Android와 backend는 offset이 해당 road의 API 구간 안에 있는지 검사한다. 제공된 x/y는 그 road에서 정확히 한 번 나타나는 vertex일 때만 private `anchor`로 보존한다. endpoint와 모순되거나 앵커 순서가 geometry/API 양쪽에서 증가하지 않으면 거부한다. 없는 좌표를 가까운 점으로 임의 투영하지 않는다. 앵커가 없는 기존 fixture는 road별 비율 계약을 사용하며 실제 turn 좌표까지 검증된 자료로 간주하지 않는다.

같은 offset의 출발·회전 metadata는 허용한다. 서로 다른 회전 type이 충돌하면 추측하지 않고 거부한다. 경계에서는 outgoing road를 선택하며 해당 회전을 남은 거리 0으로 유지한다. 좌표 비교·도로 연결의 허용 오차는 0이며, geometry 탐색의 `0.000001m`는 부동소수 경계 비교용으로만 사용한다.

## 4. A02 원인과 수정

기존 parser는 road geometry를 검증 없이 flatMap하여 앞 road 끝과 다음 road 시작 사이에 입력에 없는 선분을 만들었다.

지금은 road 내부 연속 중복점을 정규화한 뒤, 인접 road endpoint의 정확한 일치를 확인하고서만 결합한다. 같은 endpoint는 한 점으로 합쳐지며 edge 소유권은 원 road에 남는다. 단절·역순 경계·한 점/중복점만 있는 road·0 API 거리·합계 불일치·전체 원 좌표 100,000개 초과는 거부한다. 전체 원 점 수는 정규화 전 기준이다. Android 생성자에서도 검증하여 parser 우회 시 가상 connector가 생기지 않는다.

작은 비동일 경계도 현재는 거부한다. 공개 개발 LIVE 응답 한 건의 7개 경계가 모두 정확히 같았으며 작은 차이를 허용할 근거는 얻지 못했다. 따라서 임의의 수 m snapping을 추가하지 않았다. 다른 정상 응답을 과도하게 거부할 수 있는 가용성 제한은 남는다. 허용 범위 확대는 공급자 근거와 별도 fixture가 확보된 뒤 수행해야 한다.

## 5. 불확실한 경로 차단

- 상류 불량 geometry/guide는 backend의 기존 오류 응답 경로로 처리하며 성공 guidance를 만들지 않는다.
- Android `GuidanceParser`는 일반적인 비식별 오류로 거부한다. 로드되지 않은 경로는 정상 TBT/TTS·소유권 획득 준비 조건을 충족하지 않는다.
- 재탐색도 같은 parser를 통과한다. 실제 Screen 통합 테스트에서 불량 응답과 NO_ROUTE는 loading 뒤 adapter 종료·GPS 구독 해제·음성 소유권 해제로 이어진다.
- 기존 accuracy/heading/ambiguity/jump/reverse·도착 확인 조건은 유지한다. 중첩 복귀 구간의 불확실성을 정상 안내로 바꾸지 않는다.

## 6. 수정 전 실패와 수정 후 동일 입력

정식 테스트를 먼저 추가했고 구현 변경 전 assertion 실패를 확인했다. 원본 실행 로그는 비공개 작업 폴더 `.gradle/phase6-b1/`에 보존했다.

| 입력/관찰 | 수정 전 | 수정 후 |
|---|---|---|
| geometry 약 100m/900m, API 500m/500m, 첫 road 50m 위치 | 전역 비율로 약 50m 진행 | API 진행 250m, 다음 회전 250m, 현재 road A |
| 같은 fixture 첫 road 끝 | 감사 재현에서 회전 거리 약 399m 오차 | API 진행 500m, road B, 회전 0m |
| 같은 fixture matcher/voice/camera | 거리·회전 위치 불일치로 테스트 실패 | 음성 “약 250미터 앞에서 우회전하세요.”, camera 전방 창 240m |
| 0–100m road와 500–600m road 사이 | 감사 재현의 300m가 ON_ROUTE, 신규 거부 assertion 실패 | 경로 생성 자체 거부, matcher 시작 불가 |
| road interval 밖 guide / road 밖 anchor / 0 API road | 수용 또는 앵커 유실 | 모두 거부 |
| road 내부 검증 anchor | 앵커를 무시해 환산 오차 | anchor를 분기점으로 같은 API 거리 환산 |

초기 Android accuracy 테스트 **8/8 실패**, backend **4/4 실패**가 각각 `red-poc.log`, `red-backend.log`에 있다. 테스트 컴파일 실패가 아니라 기대 동작 assertion 실패다. 이후 같은 테스트가 통과했다. 추가 동일-offset 충돌 및 Screen 순서 회귀도 별도 RED→GREEN으로 확인했다.

## 7. TBT/TTS/Camera/도착 영향

[`FollowCamera`](../../nav-host-poc/src/main/java/com/surpride/navhostpoc/map/FollowCamera.kt)는 API offset을 전체 길이 비율로 바꾸지 않고 검증된 turn geometry를 사용한다. `GuidanceSimulator`도 동일한 역환산을 사용한다. matcher 결과의 다음 회전과 남은 거리가 기존 Trip 및 voice controller에 전달되므로 도로명·거리·발화 기준이 일치한다. ETA는 여전히 API 잔여 거리 비율 추정이며 실제 교통 재측정으로 승격하지 않았다.

도착 후보는 수정된 진행과 실제 종점 거리·연속 fix·정차 확인을 함께 사용한다. 새 회귀는 종점 근처 3개 fix 뒤에도 명시적 확인이 필요함을 검증한다. 원래 geometry 기준 jump/reverse 검증을 API 거리 기준으로 변경하지 않았다. 중첩 구간 초기 획득은 AMBIGUOUS를 유지한다.

## 8. A05 실제 Screen 최소 통합

[`ActiveGuidanceIntegrationTest`](../../nav-host-poc/src/test/java/com/surpride/navhostpoc/ActiveGuidanceIntegrationTest.kt)는 **실제 ActiveMysteryScreen 객체**와 PositionSession callback, matcher, AndroidX ownership adapter, TestNavigationManager, voice controller, proxy refresh/reroute coordinator를 연결한다. test-only reflection으로 위치·Focus·speech·HTTP 경계만 제어한다. 제품에 테스트용 진입점이나 새 프레임워크를 추가하지 않았다.

검증 조합은 GPS→도로/Trip/250m 발화, 경로 교체 후 오래된 이벤트 무시, adapter stop 후 queued callback 차단, 소유권 loss 후 GPS·발화 차단, 불량 재탐색 차단, NO_ROUTE 안전 종료, 미래 시각 GPS 뒤 정상 fix 회복이다.

최초 Screen 테스트에서 route 교체 뒤 오래된 이벤트가 새 matcher를 UNKNOWN으로 덮는 결함을 확인해 순서 guard를 추가했다. 독립 검토에서는 거부된 미래 timestamp가 그 guard의 기준을 오염시키는 회복 문제를 발견했다. 실제 Screen RED→GREEN 회귀를 추가하고 matcher가 시간/정확도/순서에서 거부한 이벤트는 `fix` 기준에 저장하지 않도록 보완했다.

이 테스트는 개별 controller만의 테스트보다 넓지만 실제 Binder/CarService/Audio Focus 구현은 대체한다. lifecycle onStart/onStop 전체를 Host에서 순회한 E2E도 아니다. adapter.stop과 queued callback 검증을 전체 실제 Host 생명주기 합격으로 표현하지 않는다.

## 9. 신규 테스트와 기존 회귀

| 종류 | 기존 | 신규 | 이번 합계 |
|---|---:|---:|---:|
| 앱 JVM | 196 | 0 | 196 |
| PoC JVM | 196 | 23 | 219 |
| FastAPI | 90 | 12 | 102 |
| JVM/서버 합계 | 482 | **35** | **517** |
| 실제 AVD Android 계측 | 10 | 0 | **10 별도** |
| 고유 검사 전체 | 492 | 35 | **527** |

PoC 신규는 accuracy 8, integrity 8, 실제 Screen 7이다. backend 12는 parameterized 사례를 각각 센 수다. app·PoC debug APK와 app androidTest APK 빌드를 확인했다. 실패·오류·skip 0을 기준으로 집계한다. 기존 482개를 삭제·skip·완화하지 않았다.

직접 `GuidanceRoute`를 만들던 기존 matcher/reroute/camera/waypoint fixture는 제거된 중복 geometry 생성자 인자 대신 원 road 정보를 제공하도록 수정했다. 기존 assertions와 테스트 이름을 보존했다. backend 다중 section 테스트의 두 번째 section은 같은 위치의 첫 section 복제였으므로 실제 연결되는 새 section으로 고쳤다. 원래 global road index/offset assertions는 유지했다.

실행:

```powershell
$env:GRADLE_USER_HOME=Join-Path (Get-Location) '.gradle/codex-user-home'
$env:ANDROID_USER_HOME=Join-Path $env:GRADLE_USER_HOME 'android-user-home'
.\gradlew.bat '-PnavHostPoc=true' ':app:testDebugUnitTest' ':nav-host-poc:testDebugUnitTest' ':app:assembleDebug' ':app:assembleDebugAndroidTest' ':nav-host-poc:assembleDebug' '--offline' '--no-daemon' '--console=plain' '-Pkotlin.compiler.execution.strategy=in-process'
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
```

계측은 연결된 기존 Pleos `emulator-5554`, user 10에서 `am instrument --user 10 -w com.surpride.app.test/androidx.test.runner.AndroidJUnitRunner`로 실행했다. 기존 instrumentation 10개를 재실행했으며 신규 안내 회귀 35개를 실기 계측으로 실행했다는 의미는 아니다. 기존 Starlette/httpx 및 Gradle native-access 경고는 남는다.

최종 전체 결과는 `final-build-tests.log`의 BUILD SUCCESSFUL, XML의 앱 196/PoC 219 및 실패·오류·skip 0, `final-backend.log`의 102 passed, `instrumentation-final.log`의 OK (10 tests)로 대조했다. 로그는 `.gradle/phase6-b1/`에 보관하며 공개 문서에는 비식별 집계만 남긴다.

## 10. 실제 API 근거

기존 개발 인증정보로 공개 개발 출발·도착 지점의 Kakao Directions LIVE 응답을 조회했다. 키·토큰·전체 응답·좌표 목록은 보고서에 넣지 않았다.

| 관찰 | 값 |
|---|---:|
| roads / 인접 경계 | 8 / 7 |
| 정확히 동일한 endpoint | 7/7 |
| 최대 경계 gap | 0m |
| single-point / 0 API road | 0 / 0 |
| guide 앵커가 해당 road vertex와 정확히 일치 | 9/9 |
| road 합계 / summary 거리 | 7437m / 7437m |

이 응답은 강화한 backend 정규화를 통과했다. 앱의 LIVE 준비에서도 약 53분·7.4km 추천과 공식 FileProvider handoff를 확인했다. 한 경로 표본이 모든 Kakao 경로의 연결성과 거리 계약을 증명하지는 않는다. 공급자 실경로의 모든 회전 유형·복귀/연속/지하 구간은 미검증이다.

## 11. Pleos 정차 실기동 결과

기존 사용 중인 Connect x86_64 AVD(2560×1440, user 10)를 사용했다. 데이터 초기화·새 AVD 생성·OEM 권한 우회·시스템 변경 없이 두 APK를 업데이트 설치했다. P/0km/h 정차 상태다.

| 대상/시나리오 | 실제 관찰 | 판단 |
|---|---|---|
| 메인 앱 | 기존 UX로 LIVE 추천 성공, 조건/비밀 목적지 표시, 공식 PoC handoff | 성공 |
| 독립 PoC | OSM 타일·기기 GPS 위치·전방 경로·한강대로·잔여 ETA/거리 | 준비 화면 성공 |
| 공개 경로 GPS 재현 | 실제 callback ON_ROUTE, 누적 API 진행 약 5695m, 다음 회전 249m(정수 표시, 목표 250m 오차 1m 이내) | matcher 실제 입력 확인 |
| 수정 회전 거리의 실제 Host 활성 카드 | 안내 준비→컨트롤 전환 때 메뉴 미표시/이전 Surface 잔상 재관측 | **미검증**, A04 잔존 |
| 불량 경로 handoff | 테스트가 만든 packet에서 road 경계를 벌린 뒤 지도·ETA 미준비, 활성 TBT 없음 | 실제 로드 차단 확인 |
| 안전 종료/Focus | 자동 안내를 시작하지 않음. 종료 뒤 `AppFocusService.mActiveAppTypes={}` | 최종 Focus 없음, acquire→release 주기 미검증 |
| Crash/ANR | 자체 두 package crash buffer 언급 및 exit-info crash/ANR 기록 0 | 관찰 구간 내 미관측, 장시간 보장 아님 |

정상 경로에서 `tripUpdates=0`이었다. 따라서 실제 native 활성 TBT·TTS·Focus 획득·해제 성공을 주장하지 않는다. 보이지 않는 컨트롤을 눌러 명시적 시작을 우회하지 않았다. 이번 변경 범위 밖인 A04를 해결하려고 Host/SDK 전체 구조를 바꾸지 않았다.

LIVE 지도/GPS·불량 경로 화면은 마지막 미래 GPS guard 보완 전에 캡처했다. 거리·geometry 구현은 동일하다. 보완 후 전체 회귀·APK 재빌드·PoC 업데이트 설치·기본 MOCK 화면 실행·계측 10개를 다시 확인했다. 최종 APK에서 LIVE 활성 안내를 추가 성공한 것으로 집계하지 않는다.

[메인 LIVE 준비 화면](evidence/b1/main-live-ready.png), [PoC OSM/GPS 준비 화면](evidence/b1/poc-prepared.png), [불량 경로 차단 화면](evidence/b1/poc-invalid-geometry.png)을 첨부했다. 표시 위치는 공개 개발 시험 지점이며 외부 사용자 위치를 수집하지 않았다. 지도 캡처의 OSM attribution은 보존했다.

검증용 로컬 프록시와 GPS 재생을 종료했다. 원래 private record/cache·설정 파일을 바이트 해시로 확인하여 복원했고 원 GPS로 되돌렸다. 이번에 생성한 handoff 파일 1개만 제거했다. 사용자 데이터 전체 삭제 없음. APK는 수정 빌드를 유지한다. 빌드/설치 SHA-256 일치는 [검증 요약](evidence/b1/verification.json)에 기록한다.

## 12. 독립 검토

읽기 전용 별도 검토자가 전체 working diff와 신규 테스트를 검토했다. 거리/geometry 계약과 안전 임계값 보존을 확인했고, 중요한 지적 1건(미래 timestamp의 순서 기준 오염)을 실제 Screen 실패 테스트로 재현하여 수정했다. 실제 lifecycle·Host 미검증을 제한으로 남긴다. 다른 A03/A04/A06, 운영·개인정보 과제는 이 변경의 해결 완료로 집계하지 않는다.

## 13. R01 및 미해결 위험

| 항목 | 6A 당시 | 6B-1 증거에 따른 상태 |
|---|---|---|
| A01 전역 비율 환산 | 재현된 결함 | **계약 수정·정식 회귀 완료**, 실제 Host 회전 카드 미검증 |
| A02 가상 connector | 재현된 결함 | **생성 차단·양단 검증·재탐색/실기 로드 거부 확인** |
| R01 | 안내 정확성 수정 전 제출 차단 | 코드 계약 결함 해소. 활성 Host/다양한 실경로 검증 조건은 유지 |
| A05/R02 | 실제 Screen 결합 증거 부족 | 실제 Screen 최소 통합 보완. 전체 lifecycle/실제 Binder·late focus 경합은 PARTIAL |
| A04/R04 | Host 컨트롤 지연·미표시 | 재관측, 미해소. 시연의 명시적 시작/종료 접근을 먼저 검증해야 함 |

정확 endpoint·정수 거리 합·고유 vertex 정책은 불명확한 입력을 과감히 거부하므로 가용성을 낮출 수 있다. 공급자 전체 응답 분포나 경계 반올림은 측정하지 않았다. 도로 내부 anchor 없는 구간의 비율 환산은 정확한 차량 회전 시각 보증이 아니다.

DESTINATION_PRIVACY PARTIAL, ROUTE_QUALITY_EVIDENCE PARTIAL, STT PARTIAL, 실제 경관 검증 후보 **0곳**, 실차·장시간·실음향 미검증은 유지한다. 기존 로그/캐시 보존·원자적 저장·terminal 복원 위험도 남는다.

## 14. Phase 6B-2 진입

**제한을 유지한 6B-2 진입 가능.** 다음 범위는 A06 원자적 packet 저장 → A03 terminal/공개/복귀 상태 복원 → 관련 Screen 회귀다. 현재 B1 거리/geometry fixture와 전체 회귀를 기준으로 보존한다. A04의 실제 Host 시작/종료 도달성은 별도 필수 검증으로 추적하며 완료 전 활성 안내 시연 합격을 선언하지 않는다.

최종 **READY_WITH_LIMITATIONS / VERIFIED_CONTRACT**. 실제 도로 주행·상용 배포는 계속 미검증·미승인이다.
