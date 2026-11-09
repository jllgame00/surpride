# Phase 3B-2 경로 소유권·목적지 비공개 계약 검증

2026-11-09. **Phase: READY_WITH_LIMITATIONS / DESTINATION_PRIVACY: PARTIAL.**

PARTIAL은 기존 자체 NavigationTemplate/Surface와 로컬 payload의 제한된 검증을 뜻한다. **실제 Pleos 활성 Trip 및 시스템 전체 비공개는 UNVERIFIED**다. 소유권을 실제 획득하거나 길 안내·음성을 시작하지 않았다.

## 1. 시작 상태와 범위

시작은 `main / b76331468d725f43e42f84250754dbf002c83439 / clean`이다. main 앱·Vehicle2.3.2·NaviHelper3.0.5·FastAPI·GPS/DEMO·기존 증거와 빌드 스크립트는 보존했다. 커밋/Push/원격 변경은 없다.

종료에도 브랜치/HEAD를 유지하며 수정2개·신규14개(문서/증거 포함)다. 변경/신규 텍스트 공백 검사와 보고서 증거 링크 검사를 통과했다. 실제 인증 바이트는 Git-visible 파일과 설치 APK에서 발견되지 않았고 테스트 라이브러리 클래스도 APK에 포함되지 않았다.

기존 Car App Library `app/app-automotive:1.7.0`, min29/target34/compile37을 유지했다. 추가 의존성은 **테스트 전용** `app-testing:1.7.0`, `robolectric:4.16.1`이다. AGP/Kotlin/Gradle/시스템 SDK 버전 변경 및 Manifest 권한 추가는 없다.

## 2. 공식 정책과 소유권 계약

[Pleos Maps/Navigation 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide)은 APP_MAPS 분류, 단일 활성 경로, 명시적인 경로 전환, 주행 조작 제한을 요구한다. 비활성/배경 상태의 안내 허가로 해석하면 안 된다. 기존 Activity의 APP_MAPS와 CarAppService의 AndroidX NAVIGATION 선언은 서로 다른 목적이며 둘 다 유지했다. 마켓 심사는 별도다.

[CarAppFocusManager](https://developer.android.com/reference/android/car/CarAppFocusManager)는 NAVIGATION 포커스 요청/소유권 상실/반납 계약을 제공한다. 소유권 callback에 따라 실제 안내를 중지해야 하며 SecurityException 가능성도 있다. 이 이미지에서 읽기 전용 `AppFocusService`가 존재하고 `mActiveAppTypes:{}`를 전후 확인했지만, **실제 requestAppFocus 성공/권한 범위를 검증한 것은 아니다**. 추가 권한명을 추정하여 선언하지 않았다.

[NavigationManager](https://developer.android.com/reference/androidx/car/app/navigation/NavigationManager)의 실제 안내 시작은 사용자 요청 및 callback 등록 후 `navigationStarted`, 종료/도착은 `navigationEnded`다. `onStopNavigation` 후 업데이트를 멈춰야 한다. `updateTrip` 데이터는 차량 능력에 따라 cluster/HUD 등에 전달될 수 있다. 실제 platform focus와 AndroidX host navigation lifecycle은 서로 대체 관계가 아니다.

이번에는 실제 manager adapter를 연결하지 않았다. **OEM route 없음·정차만으로 목적지 없는 활성 안내의 정책 적합성이 증명되지 않기 때문에 실제 활성화 실험을 보류**했다. 사용자 지시의 보류 조건에 따른 것이며 시스템 접근 거부를 우회한 결과가 아니다.

Car App API4는 실제 host 연결 로그로 재확인했다. 사용하는 Trip/Step/기본 Maneuver/DateTimeWithZone/manager lifecycle은 1.7.0에 존재하는 기본 계약이다. 최신 문서의 API9 voice-assistant 기능 등은 도입하지 않았다. `NavigationManager.onStopNavigation()` 구현 메서드는 실제 bytecode상 RestrictTo이므로 호출하지 않는다. 테스트에서는 공개 TestNavigationManager getter로 얻은 공식 callback에 stop을 주입한다.

## 3. Trip/Destination/Step/Maneuver/TravelEstimate 감사

현재 Gradle이 해석한1.7.0 AAR의 classes.jar와 감사한 jar가 동일함을 SHA256으로 확인했다. builder bytecode, 실제 Robolectric 실행 및 Parcel 직렬화 왕복을 함께 검증했다. [필드 감사](ownership-evidence/contract-audit.txt).

| 모델 | 실제1.7.0 계약 | 이번 factory의 사용 |
|---|---|---|
| Trip | Destination/Step 목록은 빈 목록 가능. 추가한 항목마다 대응 TravelEstimate 필요. loading=true이면 Step 추가 불가 | hidden은 Destination와 destination estimate 모두0, next Step/estimate는1 |
| Destination | name/address/image. **이름·주소가 모두 null/empty이면 build 실패**. 한쪽만 있으면 가능 | hidden에서는 객체 자체를 생략. reveal은 실제 identity 사용, image 없음 |
| Step | cue, road, maneuver, lane/image. Maneuver 생략 가능; lane image 설정 시 lane 데이터 필요 | 필요한 회전 cue/도로명/유효 Maneuver 유지; identity가 섞이면 전체 요청 거부 |
| Maneuver | type 필요. 일부 roundabout type은 exit number/angle도 필수. icon 선택 | 기본 회전 검증. 필수 추가 정보 없는 type은 build 실패하며 임의 값으로 보완하지 않음 |
| TravelEstimate | Distance와 도착시각 필요. remaining time은 library상 >=0 또는 UNKNOWN(-1), text/icon/color 선택 | 알려진 비음수 시간만 받음. text/icon으로 목적지 삽입하지 않음 |

근거: [Trip.Builder](https://developer.android.com/reference/androidx/car/app/navigation/model/Trip.Builder), [Destination.Builder](https://developer.android.com/reference/androidx/car/app/navigation/model/Destination.Builder), [Step.Builder](https://developer.android.com/reference/androidx/car/app/navigation/model/Step.Builder), [Maneuver.Builder](https://developer.android.com/reference/androidx/car/app/navigation/model/Maneuver.Builder), [TravelEstimate.Builder](https://developer.android.com/reference/androidx/car/app/navigation/model/TravelEstimate.Builder). 문서의 최신 계약만으로 현재 버전을 추정하지 않았다.

### 질문 A~F의 답

- **A — 이름·주소 없는 Trip:** Destination 목록을 통째로 생략한 Trip은 유효하다. 빈 이름/주소의 Destination을 넣는 방식은 유효하지 않다. 허위 이름이나 좌표를 넣지 않았다.
- **B — 이름 없는 ETA/TBT:** 공식 FakeHost에 next Step과 그 Step까지의 ETA를 주는 것은 테스트에서 예외 없이 처리됐다. **실제 Pleos host 수용/표시는 미검증**이다. Trip의 목적지 ETA는 Destination과 짝이므로 hidden payload에는 없으며, next Step ETA를 최종 도착 ETA로 표시하지 않는다. NavigationTemplate의 별도 TravelEstimate 표시 가능성과 Trip의 목적지 ETA 계약도 구별한다.
- **C — 최종 좌표:** 이1.7.0의 다섯 모델에는 직접적인 latitude/longitude/CarLocation 필드가 없다. factory는 좌표를 입력받지 않고 이미지/URI도 설정하지 않는다. 이는 다른 host intent/Surface pixels/향후 API/문자열에서의 위치 추론까지 막는다는 뜻이 아니다.
- **D — cluster/HUD/알림:** Trip의 도로명·cue·maneuver·거리·시간 등을 host가 사용할 수 있다. 실제 Pleos에서 어느 채널에 어떤 정보가 남는지는 미검증이며 Destination 생략만으로 비공개라고 단정하지 않는다.
- **E — 목적지 없는 안전 안내:** 회전/도로 정보를 표현하는 계약은 가능하지만 안전한 독립 안내는 미검증이다. lane·규제·안전 정보 완전성, 실제 지도, GPS 정밀도, 활성 소유권, 오류 중단을 추가 검증해야 한다. identity가 안전 문구에 들어가면 문구를 지우거나 바꾸지 않고 payload 전체를 거부한다.
- **F — 공개 요청 후 전환:** 명시적 `userRequested=true`일 때만 실제 name/address와 목적지 estimate를 추가하는 로컬 전환을 테스트했다. 같은 모델 세션에서 hidden→reveal update를 official FakeHost에 보냈지만 **실제 Pleos 전환은 미검증**이다. 공개 후 다시 Destination을 생략해도 이전 기록 삭제를 보장하지 않는다.

테스트 identity는 명확히 표시한 합성 fixture이며 실제 host에 송신하지 않았다. 개발 감사 화면의 MOCK 데이터도 실제 내비게이션을 속이기 위한 목적지로 사용하지 않는다.

## 4. 호스트에 전송되는 데이터

`PrivacyTripFactory`는 network/location/focus/NavigationManager 접근 없이 로컬 객체만 생성한다. hidden payload는 current road, next Step(cue/road/maneuver), next-step distance/time/arrival을 갖고, Destination/destination estimate/image/icon은 갖지 않는다. reveal은 사용자 요청 후 identity와 destination estimate를 추가한다. identity 문자열은 toString에서 가리고, 실패 메시지에 원문을 넣지 않는다. 단순 토큰 검사로 모든 장소 추론을 방지하는 것은 아니다.

직렬화 검증은 실제 Bundleable/Parcel로 Trip을 왕복한 후 필드와 비공개 상태를 검사했다. **이것은 로컬 라이브러리의 데이터 계약 검증이며 실제 시스템 전송 증거가 아니다.** 기기에서는 ListTemplate 감사 문구/개수만 전달했다. 실제 NavigationManager에 보낸 Trip은0건이다.

[공식 TestNavigationManager](https://developer.android.com/reference/androidx/car/app/testing/navigation/TestNavigationManager)의 `getTripsSent`와 시작/종료 카운터는 **super 검증보다 먼저 시도를 기록**한다. 따라서 getter에 기록이 있다는 사실만으로 성공을 세지 않았다. 시작 전/종료 후 update 및 callback 없는 시작의 예외와, 정상 세션에서 예외 없는 update를 나누어 검사했다. 이 테스트 도구는 실제 CarAppFocusManager/OEM host/마켓 심사를 대체하지 않는다.

## 5. 독립 소유권 상태 모델

`OwnershipController`는 INACTIVE→REQUESTED→ACTIVE→STOP_REQUESTED→STOPPED 및 ERROR를 다룬다. 실제 포커스 adapter는 없다. 초기화/재연결만으로 활성화하지 않는다.

- 사용자 요청·정차·OEM route 상태 확인·policy/privacy 확인 flag가 모두 있어야 request 가능하다. flag는 실제 정책 승인이나 platform permission 그 자체가 아니며, 미래 adapter가 신뢰할 수 있는 상태를 공급해야 한다.
- OEM active/unknown이면 차단한다. 전환 동의가 있어도 기존 경로가 종료되기 전에는 요청하지 않고 OEM route를 자동 취소하지 않는다.
- 요청 수락만으로 ACTIVE가 되지 않는다. grant callback 뒤에 navigation lifecycle을 시작한다. 중복 요청/승인, 환경 변경·포커스 상실·host stop을 처리한다.
- 종료는 navigationEnded→해당 request ID의 focus 반납→callback 해제 순서다. 실제 adapter는 ID마다 별도 platform ownership callback/lease를 연결해야 한다.
- 늦은 grant 및 정리 도중 다시 들어온 grant의 미반납 lease를 추적한다. 부채/부분 실패가 남으면 ERROR와 재연결 요구를 유지한다. 정리 도중 재연결을 막고, 이전 세션의 오류가 새 세션을 종료하지 않도록 세대 번호를 검사한다.
- release/오류는 자동 재활성화하지 않는다. 정리가 끝난 뒤 명시적인 reconnect, 다시 사용자 요청이 필요하다. publish는 ACTIVE에서만 허용한다.

독립 검토에서 발견한 정리 부채·오류 latch·재진입 문제는 **47개 중5실패, 추가48개 중1실패**로 재현한 뒤 수정했다. 최종 재검토에서 남은 Important 지적이 없음을 확인했다. 실제 focus 상실/서비스 재연결/host Binder stop을 에뮬레이터에서 발생시킨 결과로 표현하지 않는다.

## 6. 채널별 목적지 비공개 판정

| 채널 | 판정 | 근거·범위 |
|---|---|---|
| SURPRIDE NavigationTemplate | PASS | 기존3B-1 비공개 projection/화면 증거 및 이번 회귀. 활성 Trip 모드는 별도 미검증 |
| Surface 지도 | PASS | 기존 국소 도식/끝점 제외 및 회귀. 실제 지도/모든 시점의 위치 추론 방지는 아님 |
| 실제 시스템 host의 Trip 정보 | UNVERIFIED | 로컬 직렬화/FakeHost만 검증, 실제 updateTrip0건 |
| 계기판·HUD | UNVERIFIED | 실제 활성 Trip/지원 표면 검증 없음. 미표시는 PASS가 아님 |
| 시스템 알림 | UNVERIFIED | 실제 안내 알림을 발생시키지 않음 |
| 음성 안내 | UNVERIFIED | 실행하지 않음. host의 파생 음성 계약도 미확인 |
| 최근 목적지·기록 | UNVERIFIED | 실제 등록/조회/삭제 실험 없음. 보호된 OEM 기록에 접근하지 않음 |

새로 확인한 LEAK는 없지만, 미검증 채널에 LEAK가 없다고 보장하지 않는다. 기존 NaviHelper/OEM 목적지 노출 결론을 뒤집지 않는다. 최종 **PARTIAL**은 자체 개발 화면에 한정하며 CONFIRMED가 아니다.

## 7. 실제 Pleos 에뮬레이터 검증

Pleos Connect v2.1.0/API34, emulator-5554/사용자10에서 debug APK 설치 Success, CarAppActivity COLD392ms/Wait404ms를 확인했다. ‘실험 선택→소유권·비공개 계약 감사’ 화면에서 다음을 관찰했다.

- `destinations=0, steps=1, stepEstimates=1, model=INACTIVE, actualActivation=false, carApi=4`.
- 정차 전용/실제 활성화 보류, next Step ETA와 최종 ETA의 구별, 시스템 채널 UNVERIFIED 문구가 모두 표시됐다.
- AppFocusService는 전후 `mActiveAppTypes:{}`였다. 기존 main NaviHelper 연결/OEM 활성 경로 false를 확인했다. 포커스 요청/실제 Trip update/음성/경로 전환은 없다.

[실제 감사 화면](ownership-evidence/local-audit.png). 이 ListTemplate 화면은 **목적지 없는 활성 Trip을 host가 렌더링한 증거가 아니다**. 기존 GPS의271개 콜백/실경로 UI 증거는 보존하며 이번에는 새 경로 API 요청·geo fix·GPS 추적·실차 주행을 실행하지 않았다.

실제 native NavigationTemplate의 기존 ETA 하단 잘림도 전체 해결로 승격하지 않는다. 이번 감사 화면은 정적 ListTemplate이며 목적지 ETA 카드를 표시하지 않는다.

## 8. 테스트·빌드·환경

- **앱89 + PoC48 + FastAPI33 =170개 통과**, 실패/오류/skip0. 기존149개와 새21개(소유권14/Trip4/공식 테스트 manager3)를 포함한다.
- main `build-codex.ps1 -Test -RerunTasks`: 성공/41개 task 실행. PoC `build-nav-host-poc.ps1 -Test`: 최종 일반 sandbox 실행 성공/48개 테스트 실행. FastAPI33개도 이번 Phase에서 실행했다.
- 새 테스트는 사용자 gate/OEM 충돌/요청-승인 구별/host stop/중복/오류/부분 정리/늦은 callback/reconnect/재진입, hidden/reveal/identity 오염/잘못된 필드, 실제 Parcel 왕복과 official FakeHost를 포함한다.
- Robolectric cold runtime 다운로드가 staging parent 디렉터리 생성 오류로 실패했다. user.home/Maven cache/tmp를 테스트 JVM에서만 프로젝트 `.gradle/rt`로 격리했다. 짧은 경로 자체가 해결책이라는 증거는 없으며 MAX_PATH 문제로 단정하지 않는다. 승인된 네트워크/프로젝트 캐시 준비 실행 후 **일반 표준 명령으로 신규 테스트가 통과**했다. 빈 캐시의 restricted 환경에서는 최초 준비 승인이 다시 필요할 수 있다. 전역 ACL/캐시/환경변수는 변경하지 않았다.
- 기존 Car App deprecated API, FastAPI TestClient 및 Robolectric Conscrypt의 JDK native-access 경고가 남았다. 경고를 없애려고 SDK/JDK/AGP를 바꾸지 않았다.

명령·집계·APK hash·런타임/정리 결과: [테스트 증거](ownership-evidence/test-summary.txt), [런타임 증거](ownership-evidence/runtime-health.txt). 현재 PID 범위의 crash 관찰과 과거 개발 crash 기록을 구분한다. 인증/개인 위치/원시 Trip·경로를 증거에 저장하지 않는다.

## 9. 변경 파일 및 재실행

- 수정: PoC `build.gradle.kts`(테스트 의존성/로컬 test JVM 환경), `MockNavigationService.kt`(별도 감사 메뉴).
- 추가: `ownership/OwnershipController.kt`, `PrivacyTripFactory.kt`, `OwnershipAuditScreen.kt`.
- 추가 테스트: `OwnershipControllerTest.kt`, `TripPrivacyContractTest.kt`, `TestNavigationManagerContractTest.kt`.
- 추가 문서/증거: `NAVIGATION_OWNERSHIP_PLAN.md`, 본 보고서, `ownership-evidence/`.
- 기존 main/GPS/Vehicle/NaviHelper/FastAPI 구현·빌드 스크립트·검증 자료는 수정하지 않았다. 실제 focus adapter를 연결하는 패치는 포함하지 않는다.

```powershell
.\scripts\build-codex.ps1 -Test
.\scripts\build-nav-host-poc.ps1 -Test
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
adb -s emulator-5554 install --user 10 -r nav-host-poc/build/outputs/apk/debug/nav-host-poc-debug.apk
adb -s emulator-5554 shell am start --user 10 -W -n com.surpride.navhostpoc/androidx.car.app.activity.CarAppActivity
# 실험 선택 → 소유권·비공개 계약 감사. 실제 활성화 버튼은 없다.
```

## 10. 실제 안내 가능성과 Phase 3B-3 조건

필수 API는 존재하고 로컬 계약·상태 모델은 검증됐으므로 기술적 후속 경로는 있다. 그러나 **실제 Pleos 소유권 획득/독립 안전 안내/전체 채널 비공개 승인 가능성은 미확인**이다. 다음 조건을 먼저 충족해야 한다.

1. Pleos에 목적지 없는 Trip/최종 ETA 처리와 독립 내비의 활성 안내·비공개 UX 심사 허용 범위를 확인한다. library build 성공을 승인으로 대체하지 않는다.
2. 카카오 경로/geometry의 별도 지도·독립 안내 표시·저장 조건을 확인한다. 이번 Phase에서 사용 권한을 새로 확보한 것은 아니다.
3. 검증된 UX/OEM 상태 공급과 ID별 ownership callback을 가진 실제 CarAppFocusManager adapter를 구현한다. 모든 명령/콜백은 main thread로 직렬화한다. 기존 경로가 있으면 사용자의 명시적 전환 및 기존 안내 종료를 확인한 뒤 요청한다.
4. 실제 manager callback 등록→focus grant→navigationStarted→updateTrip→host stop/오류/포커스 상실 정리를 별도 정차 PoC에서 검증한다. 음성/실도로 주행은 여전히 별도 승인·검증 단계다.
5. 허용된 실제 host 실험에서 Template/Surface/Trip/cluster/HUD/알림/음성/이력을 채널별로 다시 판정한다. 지원되지 않거나 관찰 못한 표면은 UNVERIFIED를 유지한다.
6. GPS 정밀 매칭·이탈/재탐색·구간 ETA·차선/안전 정보·사용자 공개 전환과 기록 보관을 검증한 뒤 실제 안내 구현 범위를 결정한다.
