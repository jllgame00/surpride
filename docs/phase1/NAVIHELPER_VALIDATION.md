# SURPRIDE Phase 1B — NaviHelper 실제 연동 검증

판정: **READY_WITH_LIMITATIONS**. NaviHelper 실제 연결, 공개 테스트 경로 요청, 활성 안내 상태, 목적지/남은 시간·거리/TBT 조회 및 취소를 Pleos 에뮬레이터에서 확인했다. 목적지 비공개 판정은 **PARTIAL**이다. SURPRIDE 화면에서는 원문을 생략할 수 있지만 기본 Navigation의 경로 선택·안내 화면과 최근 목적지에 실제 목적지가 노출된다. 완전 비공개 주행은 현재 검증한 공식 API 조합으로 제공할 수 없다.

작업 기준일: 2026-11-09(KST). 에뮬레이터 표시 시각은 호스트와 다르므로 캡처 시각을 호스트 시각으로 해석하지 않는다. 실제 차량 주행이나 전체 제품 구현 결과가 아니다.

## 1. SDK 버전 및 실제 사용 API

- 시작 Git: `main`, HEAD `1f485f4f154b33647216b5af23155d4eb3486ee4`, clean.
- 종료 Git: 동일 브랜치/HEAD, 아래 변경은 미커밋 상태. 커밋·Push·원격 설정 변경 없음.
- 기존 Vehicle SDK `2.3.2`와 Pleos Maven 저장소를 유지하고 `ai.pleos.playground:NaviHelper:3.0.5`만 추가했다. 실제 해석된 전이 모듈은 `navi-data:3.0.4`, `navi-aidl:3.0.1`이다. [공식 의존성 안내](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- 에뮬레이터 Navi Service는 `3.0.4`/code 55이다. Helper와 서비스의 버전 번호 차이만으로 호환성을 단정하지 않았으며 연결과 아래 API를 실제 검증했다. 모든 단말/배포 이미지의 호환성 보장은 아니다. [환경 증거](navi-evidence/environment.txt)
- Gradle/AGP/Kotlin/JDK/Android SDK 및 Phase 0B 빌드 스크립트 변경 없음.

실제 사용: `NaviHelper(context)`, `initialize(onConnected, onFailed)`, `addListener`, `removeListener`, `release`, `requestRoute`, `getRouteStateInfo`, `getDestinationInfo`, `getTBTInfo`, `getRecentDestinationInfo`, `cancelRoute`, `requestNavigationScreen(Foreground/RecentDestinations)`.

리스너는 연결 완료 후 한 번 등록한다. 해제 시 동일 인스턴스를 제거하고 `release`하며, 재연결은 새 Helper로 수행한다. 이전 세대의 지연 콜백은 무시한다. 앱이 OEM 화면으로 전환되어도 진단 이벤트를 수신하도록 Activity 생존 동안 Navi 연결을 유지하며, `onDestroy`에서는 경로를 취소하지 않고 연결만 해제한다. Vehicle의 기존 `onStart/onStop` 관리는 유지했다. [공식 리스너 계약](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)

목적지·도착·취소·탐색 시작/완료·오류 콜백을 구현했지만 구현 여부와 실제 수신 여부는 3절에서 구분한다. `getInternalApi`와 비공개 API는 사용하지 않았다. 실제 AAR의 공개 시그니처를 `javap`로 확인했다. [계약 증거](navi-evidence/public-contracts.txt)

## 2. 필요한 권한과 접근 결과

| 추가 권한 | 사용 목적 | 에뮬레이터 결과 |
| --- | --- | --- |
| `pleos.car.permission.NAVI_ROUTE` | 경로/목적지/TBT/이력 조회 | `granted=true`, 응답 수신 |
| `pleos.car.permission.NAVI_ROUTE_SEARCH` | 명시적 테스트 경로 요청·취소 | `granted=true`, 실제 동작 확인 |
| `pleos.car.permission.NAVI_CUSTOM_MAP` | 기본 내비 전면/최근 목적지 화면 요청 | `granted=true`, 화면 확인 |

이는 사용 API에 대한 [공식 권한 구분](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)에 따른 최소 추가 선언이다. [Package Manager 증거](navi-evidence/permissions.txt). 기존 차량 조회 권한은 보존했다. 위치·마이크 권한이나 시스템 설정 변경은 이번 진단에 추가하지 않았다. 실제 배포 앱의 프로젝트 등록/권한 승인까지 검증한 것은 아니다.

## 3. 경로 요청 및 이벤트 검증

대상은 `emulator-5554`, API 34, 현재 사용자 10, Pleos Connect v2.1.0이다. 실제 Gradle 패키지 `com.surpride.app`의 debug APK를 설치했다. 다른 디바이스로 명령을 보내지 않았다.

공개 목적지 한 곳만 사용했다. [공식 목적지 예제](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)의 `42dot`, 위도 `37.48544722`, 경도 `127.03636666`, 원래 POI ID와 주소를 변경 없이 전달했다. 문서 Java 위치 인수 예제의 좌표 순서와 Kotlin 예제가 다르므로 실제 3.0.5 모델 getter와 테스트로 확인한 **Kotlin 이름 인수 latitude/longitude**를 사용했다. 허위 이름으로 목적지를 위장하지 않았다.

실험은 초기 빌드와 메타데이터 조회 보완 후 최종 빌드에서 각각 수행했다. 앱 실행·재연결 자체는 읽기 전용 상태 조회만 한다. 진단 버튼을 명시적으로 누른 경우에만 요청하며, Build 모델/제품/디바이스가 검증한 Pleos 에뮬레이터와 일치해야 한다.

| 단계 | 관찰 결과 | 증거/제한 |
| --- | --- | --- |
| 초기 연결 | CONNECTED, 리스너 1, SAFE_GUIDING, 활성 false, 탐색 false, 약관 true | [초기 상태](navi-evidence/before-request.txt) |
| 요청 직전 | 기존 경로 없음/탐색 안 함을 새 조회로 재확인 | 상태 불명·기존 활성 경로면 요청 차단 |
| `requestRoute` | 호출 정상 반환 | 반환만으로 탐색 완료라고 처리하지 않음 |
| 요청 직후 외부 UI | 기본 Navigation이 자동 전면 표시, 이름/도착 핀 노출, 안내 시작 카운트다운 | 별도 화면 보기 버튼을 누르기 전 [캡처](navi-evidence/native-route-request.png) |
| 실제 경로 상태 | `onRouteStarted`, `onNavigationStatus(ROUTE_DRIVE)`, ROUTE_GUIDING/활성 true/탐색 false | [요청 후 이벤트](navi-evidence/after-request.txt), [안내 화면](navi-evidence/native-guidance.png) |
| 탐색 완료 콜백 | `onRoutingCompleted` 미관찰 | API 반환 및 활성 안내 성공과 별개. 공식 문서도 Navigation 구현별 이벤트 차이를 명시 |
| 목적지/안내 조회 | 테스트 목적지 일치 true, remainTime `60`, remainDistance `50`, TBT 1건 | [최종 메타데이터](navi-evidence/active-metadata.txt). 단위를 추정해 ETA 시각으로 변환하지 않고 원시값 표시 |
| 취소 | 목적지 일치와 현재 세션 요청 여부 확인 → 새 경로 상태 조회 → `cancelRoute` → `onRouteCancelled`/FREE_DRIVE/SAFE_GUIDING | [최종 취소 증거](navi-evidence/cancel-final.txt) |
| 해제/재연결 | DISCONNECTED/리스너 0/정리 오류 0 → CONNECTED/리스너 1/idle | [해제](navi-evidence/release.txt), [재연결](navi-evidence/reconnect.txt) |
| 프로세스 재시작 | COLD 실행, CONNECTED/idle, 자동 경로 요청 없음 | [재시작](navi-evidence/restart.txt) |
| 도착/재탐색/실제 권한 거부 | 미검증 | 차량 이동·위치 조작·권한 강제 변경은 수행하지 않음 |

완료 콜백이 전달되지 않는 구현에 대비해 활성 상태의 명시적 새로고침에서도 목적지/TBT를 조회한다. 요청 중복 방지, 15초 초기화/조회 타임아웃, 지연 응답 후 재연결 요구, 세션 소유권 검사 및 목적지 변경 중 취소 중단을 구현했다. SDK에 요청별 상관 ID나 원자적 compare-and-cancel 계약이 없어 검사와 취소 사이 외부 변경을 완전히 원자적으로 차단한다고 보장하지 않는다. 현재 버튼은 에뮬레이터 진단 전용이다.

최종 상태는 테스트 경로 취소 완료/idle, SURPRIDE 실행 중이다. 기본 내비에 생성된 공개 테스트 최근 목적지 항목은 남겨 두었다. 이력 삭제나 시스템 변조를 하지 않았다.

## 4. 목적지 정보 노출 지점별 결과

| 지점 | 실제 확인 | 비공개 판단 |
| --- | --- | --- |
| A. SURPRIDE 화면 | 목적지 이름·주소·좌표 없이 일치 bool, 남은 값/TBT 수만 표시 | 자체 UI 범위 가능. [활성 경로 진단 화면](navi-evidence/surpride-active-metadata.png) |
| B. 기본 Navigation | 요청 직후 `42dot`과 도착 핀, 주행 안내의 TBT 영역/요약 카드에도 이름 | 노출 확인. 기본 화면 전체 주소 노출은 별도 미검증 |
| C. 음성 | 오디오 청취/녹음 검증 없음 | UNVERIFIED. 소리가 없었다고 간주하지 않음 |
| D. 알림 | 활성 경로에서 펼친 알림 패널에 테스트 목적지 표시는 관찰되지 않음 | 한 시점 관찰일 뿐 지속 비공개 보장 아님. [알림 캡처](navi-evidence/notification-observation.png) |
| D. 홈/NaviContainer | HOME intent 후에도 UI dump는 SURPRIDE 영역을 반환 | 독립 홈 내비 카드의 목적지 노출은 UNVERIFIED |
| E. 클러스터 | 별도 지원 클러스터 출력 미확인 | UNVERIFIED. 화면 왼쪽 차량 뷰를 독립 클러스터로 간주하지 않음 |
| F. 최근 목적지/이력 | RecentDestinations 화면에 `42dot`과 주소 일부 노출, SDK 이력 조회도 테스트 포함 true | 노출 확인. [이력 캡처](navi-evidence/native-history.png), [응답 집계](navi-evidence/history-result.txt). 모든 경로 이력 기능까지 확인한 것은 아님 |
| G. ETA/TBT와 목적지 분리 | SDK로 남은 값/TBT를 받고 자체 UI에서 이름 생략 가능 | 기본 내비·음성·클러스터까지 선택적으로 숨기는 기능은 확인되지 않음 |

앱 모델에는 목적지/현재 위치/이력 전문을 보관하지 않는다. TBT 설명문도 기록하지 않고 항목 수만 유지한다. 오류는 코드만 기록한다. 저장 캡처에는 에뮬레이터 화면과 공개 테스트 목적지가 포함되며 실제 사용자 위치를 조회하지 않았다. 원시 UI dump는 Git에서 제외된 로컬 캐시에만 두었다.

## 5. 단일 활성 경로 및 주행 UX 정책 검토

[Pleos Navigation 개발 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide)은 하나의 활성 안내만 허용하고, 비활성/백그라운드 안내를 금지하며, 명시적 경로 전환 및 주행 정보/조작 제한 준수를 요구한다. 독립 Navigation 앱의 APP_MAPS/NaviContainer 분류와 실제 기능·마켓 카테고리 일치도 필요하다.

이번 앱은 OEM 내비에 경로를 요청하는 debug 진단 도구다. 독자 음성·회전 안내를 재생하지 않고 기존 활성 경로를 덮어쓰지 않는다. 독립 내비 자격을 주장하는 APP_MAPS나 주행 제한을 완화하는 선언은 추가하지 않았다. 이것이 SURPRIDE 최종 배포 카테고리/주행 UX 승인을 의미하지는 않는다. debug 긴 텍스트/여러 버튼 화면은 주행용 최종 UI가 아니며, 실제 차량 주행 조작은 검증하지 않았다.

독립 안내를 추진한다면 `CarAppFocusManager`를 통한 포커스/경로 소유, OEM 안내와의 단일 활성 경로, 위치 권한, 음성 포커스 및 UX 심사를 먼저 설계해야 한다. OEM 경로를 뒤에서 계속 안내시킨 채 자체 음성/TBT를 중복 제공하는 방식은 대안으로 채택하지 않는다.

## 6. 미스터리 내비게이션 실현 가능성 판정

**PARTIAL**: 자체 화면에서 목적지 텍스트를 생략하면서 SDK 메타데이터를 읽는 부분 경험은 검증했다. 그러나 요청만으로 기본 내비가 목적지를 드러내므로 제품 핵심인 **도착 전 모든 지원 채널 비공개는 충족하지 못한다**.

검사한 Helper 공개 메서드, RouteInfo, NavigationScreenRequest 및 공식 안내에는 최종 이름·주소·도착 핀·음성·이력을 일괄 또는 선택적으로 숨기는 공식 옵션이 확인되지 않았다. 따라서 현재 공개 계약 범위의 완전 비공개는 지원 근거가 없으며 구현 가능한 것으로 약속할 수 없다. 이는 모든 향후/승인 전용 API까지 존재하지 않는다는 단정은 아니다. 미검증 채널이 있으므로 CONFIRMED 판정은 불가능하다.

진단 패널의 `UNVERIFIED` 값은 자동 관찰 데이터가 외부 채널 전체를 검증하지 못한다는 뜻이다. 보고서의 수동 종합 판정 PARTIAL과 역할이 다르며 실행 중 임의로 비공개 성공 상태를 표시하지 않는다.

## 7. 대안별 실현 가능성

| 대안 | 구현 가능성/정책 | 사용자 경험 | 비용·권장 |
| --- | --- | --- | --- |
| A. NaviHelper 부분 비공개 | 실제 연결/경로 요청 가능. OEM 안내 유지, 단일 활성 경로 준수. 최종 카테고리는 확인 필요 | 자체 화면은 숨겨도 기본 내비·최근 목적지에서 스포일러 발생 | 낮음~중간. 완전 미스터리로 홍보할 수 없음 |
| B. 독립 Navigation 앱 | 조건부. 별도 허용된 지도/경로/안내 엔진, 라이선스, 포커스/경로 소유·APP_MAPS·UX 심사 필요. Helper를 연결하는 것만으로 독립 엔진이 생기지 않음 | 승인된 API가 모든 채널 비공개를 허용할 때만 핵심 경험 가능 | 높음. 공급자/사무국 확인 전 가능 판정 금지 |
| C. 출발 전 비공개, 출발 시 OEM 안내 공개 | 현재 검증된 Helper 경로를 활용 가능. 주행 UX/카테고리 승인 별도 | 출발 전 기대감은 유지, 주행·도착까지 비밀이라는 원래 요구는 축소 | 낮음~중간. 현 SDK에서 가장 현실적인 후속 시제품 |
| D. 사무국/API 담당 확인 | 공식 비공개 옵션·제공 범위·허용 UX에 대한 답변 필요 | 승인된 지원이 있으면 제품 방향을 재평가 | 문의 준비는 낮음, 일정/제공 여부 미확정. 문의를 실제 전송하지 않음 |

우선 확인 질문: 목적지명/주소/도착 핀/음성/홈/클러스터/최근 이력을 채널별로 숨기는 승인 API가 있는가? 숨김 상태에서도 안전 TBT·ETA를 제공할 수 있는가? OEM 경로를 사용하는 동반 앱의 허용 카테고리는 무엇인가? 독립 내비의 최소 표시 의무와 미스터리 UX가 양립하는가? Connect v2.1.0 OEM 내비의 완료/도착 이벤트 보장 범위는 무엇인가?

## 8. 테스트 및 빌드 결과

표준 명령은 그대로다.

```powershell
.\scripts\build-codex.ps1 -Test
git diff --check
```

최종 `assembleDebug` + `testDebugUnitTest`: **BUILD SUCCESSFUL (20초), 종료 코드 0**. 테스트 **28개 통과 / 실패 0 / 오류 0 / 스킵 0**: 기존 Vehicle 14 + 기본 1, 신규 Navigation 13. [빌드 로그](navi-evidence/build-tests.txt), [JUnit 집계](navi-evidence/test-summary.json). `git diff --check` 통과, 기존 파일의 LF→CRLF 경고만 존재한다.

Navigation 테스트는 자동 요청 금지, 신규 idle 확인, 실제 차량/기존 경로 보호, 목적지·세션 소유권과 취소, 초기화 실패/권한 예외/타임아웃, 중복 시작·해제·재시작/구 콜백 무시, Fake 구분, 실제 SDK 좌표 계약, 요청 예외, 목적지 변경과 취소 경합, 완료 콜백 없는 메타데이터 조회를 포함한다. Fake는 test 소스에만 있으며 실행 앱은 실제 SDK를 사용한다. 권한 거부와 초기화 실패의 단위 테스트 성공은 실제 단말 권한 거부 검증을 뜻하지 않는다.

최종 APK 설치 `Success`, MainActivity COLD 실행 `Status: ok`를 확인했다. [설치/실행](navi-evidence/install-start-final.txt), [최종 화면](navi-evidence/surpride-final.png). 최종 APK SHA256: `3C1E693BD69C20F2B394E0EB867CA5E26D31ED6E7D821B442ADD7BBF36E4E311`.

사용한 ADB 명령은 대상 serial과 user를 명시했다.

```powershell
adb -s emulator-5554 install --user 10 -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start --user 10 -W -n com.surpride.app/.MainActivity
```

검증 시점의 사용 가능한 crash 버퍼에서 SURPRIDE Process 기록 0, ActivityManager 오류 버퍼에서 해당 앱 ANR 기록 0, 프로세스 실행을 확인했다. [건강 상태](navi-evidence/runtime-health.txt). 로그 버퍼 보존 범위의 결과이며 장시간 주행 안정성 보장은 아니다. 시간 인수 전달 오류가 났던 첫 Logcat 조회는 성공으로 집계하지 않고 유효한 버퍼 조회로 재확인했다.

## 9. 변경 파일 목록

- 수정: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` — 의존성/세 권한 추가.
- 수정: `app/src/main/java/com/surpride/app/MainActivity.kt`, `app/src/main/res/layout/activity_main.xml`, `app/src/main/res/values/strings.xml` — 기존 시작/차량 패널을 유지한 debug 내비 패널.
- 생성: `app/src/main/java/com/surpride/app/navigation/NavigationDataSource.kt`, `NavigationState.kt`, `PleosNavigationDataSource.kt` — UI 분리, 안전 가드, 공개 SDK 어댑터.
- 생성: `app/src/test/java/com/surpride/app/navigation/FakeNavigationDataSource.kt`, `NavigationSessionTest.kt` — Fake/13개 테스트.
- 생성: `docs/phase1/NAVIHELPER_PLAN.md`, 이 문서, `docs/phase1/navi-evidence/` — 계약·빌드·실행·노출 증거. 개발 중 예상 실패 로그는 최종 성공 로그와 구분한다.

Vehicle 구현·테스트 파일, Phase 0/0B 문서와 빌드 스크립트, Phase 1A 검증 문서는 변경하지 않았다. 키/토큰/개인 홈 경로를 추가 기록하지 않았으며 캐시/SDK 바이너리/APK를 Git 대상으로 추가하지 않았다.

## 10. 남은 수동 검증 사항

1. 에뮬레이터 오디오를 실제 청취하고 출발/회전/도착의 목적지명 발화 여부 확인. 안전 음성은 끄지 않는다.
2. Pleos 홈의 내비 전용 영역 및 지원되는 독립 클러스터에서 이름/주소/지도 종료점/TBT 노출을 각각 확인.
3. 통지 상태 변화·재부팅 이후 이력 유지, 도착 이벤트와 탐색 완료 콜백의 OEM 지원 범위 확인. 전체 개인 이력을 수집하지 않는다.
4. 권한 미승인/서비스 불가 테스트용 승인 환경에서 실제 실패 콜백과 복구 확인. 현재 에뮬레이터의 권한을 임의로 변조하지 않았다.
5. 최종 앱 카테고리 및 주행 중 목적지 숨김 UX를 공식 확인한 뒤 A/C 또는 조건부 B의 제품 범위를 결정.

재현 시 다른 활성 경로가 없는 Pleos 에뮬레이터에서만 앱을 실행하고, 연결/idle 확인 후 공개 테스트 요청 버튼을 명시적으로 누른다. 기본 내비가 전면에 열리고 자동 안내 시작될 수 있다. SURPRIDE로 돌아와 조회하고, 동일 세션의 테스트 경로 취소 버튼으로 종료한다. 앱 재시작 후에는 소유권이 폐기되므로 기존 경로를 자동 취소하지 않는다.
