# SURPRIDE Phase 2A — Core Experience MVP

**판정: READY_WITH_LIMITATIONS.** 정차용 DEMO의 조건 선택 → 비밀 코스 준비 → 모의 드라이브 → 도착 공개 → 평가 → 기분 지도 흐름을 구현하고 Pleos에서 검증했다. 재사용 가능한 추천·상태 모델과 로컬 복원을 확보했다. 실제 목적지/경로/차량 주행 가능 거리/날씨 데이터와 실주행 UX 검증은 남아 있다.

기준일: 2026-11-09(KST). 에뮬레이터의 시각은 호스트와 다르다. 이 결과는 실제 내비게이션, 지도 서비스, 장소 운영 정보 또는 실차 주행 승인 결과가 아니다.

## 1. 실제 구현 기능과 Git 변경

- 기분 6종, 범위 4종, 장소 조건 6종 복수 선택/조건 없음, 동행 4종, 사용 가능 시간 또는 귀가 시각, 출발지 복귀, 랜덤 정도 선택.
- 규칙 기반 추천, 후보 없음/시간 부족/최근 DEMO 회피, 조건 수정·취소·재시작.
- 공개 전 목적지 없는 준비·진행 화면, 일반적인 준비물 힌트, 즉시 공개와 모의 도착 후 공개.
- 평가 1~5점, 완료 중복 방지, 기분별 DEMO 기록과 개념도.
- AES-GCM/Android Keystore 기반 로컬 저장, 앱 프로세스 재시작 복원, 손상된 저장의 명시적 복구 UI.
- 공식 CarUxRestrictions 조회/변경 수신, 불명·제한 상태의 입력 차단. 차량 제어/실제 경로 요청 없음.

Git 시작: `main`, HEAD `e6fdfa81252c2b2d7af4cbd9ee998a6e0d579235`, clean. 종료도 같은 브랜치/HEAD이며 아래 변경은 미커밋이다. 스테이징·커밋·Push 없음.

수정 파일:

- `app/build.gradle.kts`: 공식 android.car SDK의 선택적 compile/runtime library 선언. SDK/AGP/Kotlin/Gradle 버전 변경 없음.
- `app/src/main/AndroidManifest.xml`: 외부 비공개 ExperienceActivity와 선택적 android.car library.
- `app/src/main/java/com/surpride/app/MainActivity.kt`: DEMO 진입 클릭 처리 3줄.
- `app/src/main/res/layout/activity_main.xml`: 기존 브랜딩 아래 76dp DEMO 진입 버튼.

생성 파일:

- `experience/core/DriveModels.kt`, `DemoCatalog.kt`, `DestinationEngine.kt`, `TripController.kt`, `TripSnapshotCodec.kt`.
- `experience/ExperienceActivity.kt`, `ExperienceArt.kt`, `ParkedUxMonitor.kt`, `DemoStateStore.kt`.
- 대응 도메인 테스트 3개 파일, `docs/phase2/IMPLEMENTATION_PLAN.md`, 이 문서와 `evidence/`.

위 소스의 기준 경로는 `app/src/main/java/com/surpride/app/`, 테스트는 `app/src/test/java/com/surpride/app/experience/core/`다. 기존 Vehicle/NaviHelper 구현·테스트, `nav-host-poc`, Phase 0/1 문서/증거, 표준 스크립트/Gradle 버전 설정은 변경하지 않았다.

## 2. 화면 구조와 이용 흐름

기존 런처 MainActivity는 그대로다. **SURPRIDE DEMO 시작**을 누르면 내부 ExperienceActivity를 연다. SDK 진단을 새 제품 화면에 섞거나 PoC를 통합하지 않았다.

| 화면 | 동작 | 화면 증거 |
| --- | --- | --- |
| A. 시작 | 브랜드/소개, 새 DEMO, 저장된 DEMO 이어서 | [시작](evidence/home.png) |
| B. 조건 선택 | 1/3 기분·장소, 2/3 범위·시간·복귀, 3/3 동행·랜덤. 선택 요약 유지 | [기분/장소](evidence/setup-mood.png), [시간](evidence/setup-time.png), [동행](evidence/setup-companion.png) |
| C. 비밀 준비 | 이름/주소/좌표 없이 합성 소요 시간·일반 준비물·이유 일부 | [준비 완료](evidence/ready.png) |
| D. DEMO | 예시 회전/모의 잔여 시간/수동 진행/힌트/즉시 공개/취소/모의 도착 | [진행](evidence/demo-driving.png) |
| 도착 모의 | ARRIVED에서 아직 이름 숨김, 공개 버튼으로 다음 단계 | [도착](evidence/arrived.png) |
| E. 공개/평가 | 가상 장소 설명과 점수, 1~5점 선택 후 기록 | [공개](evidence/revealed.png), [완료](evidence/completed.png) |
| F. 기분 지도 | 기분별 개수와 DEMO 평가 기록. 지도 타일/GPS 없음 | [기분 지도](evidence/mood-map.png) |

다크 그린/라임 강조, 제목·요약·행동 버튼의 위계, 정적 코스 개념도를 사용했다. 풍경 사진·게임 조작·지속 애니메이션은 없다. 주요 새 터치 영역은 76dp 이상이며 실제 READY 화면 UI 계층에서 76px 미만 대상 0개를 확인했다(현재 AVD density 기준). 이는 [Android for Cars 크기 지침](https://developers.google.com/cars/design/automotive-os/design-system/sizing)을 반영한 것으로, 모든 화면/폰트 크기의 심사 적합성 보장은 아니다.

## 3. 추천 데이터 모델과 상태 관리

Android에 의존하지 않는 코어:

| 모델/구성 | 역할 |
| --- | --- |
| DrivePreferences | 기분/범위/장소 Set/동행/시간·deadline/복귀/랜덤 |
| Mood, TravelScope, PlaceCategory | 선택 값과 표시명 |
| DestinationCandidate | provider가 교체할 후보 ID/정보/적합도/지원 조건/범위별 시간 출처 |
| JourneyEstimate | 편도·체류·복귀·여유 시간, DEMO/VERIFIED_ROUTE 출처, 검증 거리 |
| RecommendationContext | 평가 시각/최근 ID/확인된 계절·날씨/차량 범위 |
| RecommendationResult | 후보·시간·점수·경고 또는 명시적 실패 |
| MysteryTripState | 단계·선택·준비된 코스·진행·공개·평가·기록 |
| TripPresentation | 공개 전 정보만 내보내는 화면용 모델. 공개 단계에서만 destination 제공 |
| DestinationEngine / TripController | 순수 추천 함수 / 명시적 상태 전이 |

정상 전이:

```text
SETUP → PLANNING → READY → DEMO_DRIVING → ARRIVED → REVEALED → COMPLETED
```

READY/DEMO에서 즉시 공개하면 REVEALED로 이동하지만 `arrived=false`를 유지한다. 기록도 ‘미리 공개’로 구분한다. 추천 실패·조건 수정·취소는 SETUP으로 돌아가며 기존 완료 기록은 유지한다. 완료는 평가가 있어야 하며 같은 trip ID를 중복 추가하지 않는다. 진행 예시는 4단계로 제한한다. 실제 주행 상태는 이 DEMO 상태로 표현하지 않는다.

스냅샷은 카탈로그 버전/열거형/개수/시간/점수·선택 후보를 보관한다. 후보 이름·설명·GPS 전문은 저장하지 않고 ID로 현재 카탈로그와 연결한다. 손상/알 수 없는 카탈로그/불일치 단계는 복원 오류로 처리하며 조용히 덮어쓰지 않는다. 복원 시 같은 코스를 다시 추천하지 않는다.

## 4. 추천 점수와 제약조건

먼저 다음 조건을 모두 통과해야 한다. 랜덤 값으로 조건을 완화하지 않는다.

1. 요청한 장소 조건 모두 포함, 동행 지원, 최근 ID 제외.
2. 해당 범위의 유효한 시간 자료 존재. 합성 시간도 출처를 유지하며 미확인 값을 만들지 않음.
3. 편도 + 체류 + 여유 + (복귀 선택 시 복귀) 시간이 사용 가능 시간과 귀가 deadline 잔여 시간 중 작은 값 이내.
4. 확인된 날씨가 후보 지원 범위 밖이면 제외.
5. 검증된 경로 거리와 검증된 차량 범위가 모두 있을 때만 거리 초과 제외.

통과한 후보의 점수:

| 요소 | 점수 |
| --- | --- |
| 기분 적합성 | 후보 0~5 × 10, 최대 50 |
| 장소 | 요청 조건을 통과하면 25, 조건 없음은 10 |
| 동행 | 지원 조건 통과 15 |
| 계절/날씨 | 확인된 값이 후보에 맞으면 각 5. 불명은 0과 경고 |
| 우연성 | seed + 후보 ID 기반 0~19점 범위, 랜덤 정도에 비례 |

총점 내림차순, 같으면 ID 순이다. 동일 seed/입력은 재현 가능하다. UI의 랜덤 프리셋은 0/40/100이며 코어는 0~100을 받는다. 기록은 최근 30개를 유지하고 그중 최근 5개 DEMO 후보를 다음 추천에서 제외한다. 모두 제외되거나 조건에 맞지 않으면 실패를 보여 주며 조건을 몰래 낮추지 않는다.

계절/날씨는 외부 자료가 없어 현재 UI에서 미확인으로 넘긴다. 엔진은 확인된 context가 주어질 때만 이를 반영할 수 있다. 현재 샘플 동행 지원은 가상 메타데이터이며 실제 가족/반려동물 입장 가능 여부 검증이 아니다.

## 5. DEMO·실제 데이터 구분과 개인정보

카탈로그는 **가상 장소 7개**다. 모든 공개 이름 앞에 ‘가상 샘플’을 표시한다. 실제 이름/좌표/영업/주차 데이터를 수집하거나 검증하지 않았다. 30분/1시간/2~3시간/1박 이상의 범위별 이동·체류·복귀 값은 합성 시나리오다. 모의 잔여 시간은 수동 진행 단계에서 계산한 예시이며 ETA/실제 귀가 보장이 아니다.

코어의 `VERIFIED_ROUTE` 경로는 향후 검증된 공급자 입력을 위한 계약일 뿐 이번 UI에서 사용하지 않는다. 확인되지 않은 주행 가능 거리는 추정하지 않는다. RANGE를 SDK에서 받아도 단위가 km임을 별도로 확인한 경우, 실제 출처/AVAILABLE/양수·유한/최근 60초 조건을 모두 만족해야 차량 범위로 전달한다. SOC·연료·누적 주행거리로 환산하지 않는다.

선택/진행/평가/DEMO 기록은 앱의 `noBackupFilesDir`에 AES-GCM으로 암호화하고 Android Keystore 키를 사용한다. 백업/기기 전송/Git 대상으로 저장하지 않는다. 사용자 데이터·키·원시 저장 파일을 증거에 복사하지 않았다. 캡처의 선택·평가·기록은 이번 ADB 검증에서 생성한 가상 테스트 데이터다. 원시 UI XML은 Git에서 제외된 `.gradle/`에만 두었다.

기분 지도는 기분별 개념도이며 실제 지리 지도/방문 인증이 아니다. 미리 공개 기록도 모의 도착과 구분한다.

## 6. 기존 SDK 연계와 정차용 정책

기존 MainActivity의 Vehicle 진단과 NaviHelper 연결/조회/테스트 버튼을 보존했다. ExperienceActivity는 새 NaviHelper 경로를 요청하거나 Vehicle 제어 API를 호출하지 않는다. 현재 DEMO 추천은 차량 범위를 미확인으로 받는다. 실제 SDK 주행 가능 거리 계약이 확보되면 기존 reading을 검증 어댑터를 통해 context에 주입할 수 있다. 현재 SOC 수신을 실제 경로 안전성으로 바꾸지 않았다.

UI 제한은 [Pleos 주행 제한 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)의 공식 `CarUxRestrictionsManager` 조회·변경 수신을 사용한다. `requiresDistractionOptimization=false`일 때만 정차용 조작을 허용하고, 제한 true/불명/연결 해제/화면 중단 시 입력을 잠근다. 이것은 UX 정책 허용 상태이며 기어 P를 직접 판정한 값은 아니다. DO를 선언하거나 시스템 주행 제한을 우회하지 않았다.

Pleos v2.1.0에서는 `android.car`가 일반 shared library 목록에 없고 boot class path에 있다. 최초 required=true 설치는 MISSING_SHARED_LIBRARY로 거절됐다. 선택적 library 선언으로 수정한 뒤 정상 설치와 실제 공식 UX API 응답을 확인했다. 라이브러리/API가 없는 환경에서는 LinkageError/연결 실패를 미확인 잠금으로 처리한다. 시스템 패키지/권한/설정은 변경하지 않았다.

독립 검토에서 복원 오류 확인창이 오래 남으면 제한 변경 뒤 초기화할 수 있는 문제를 찾았다. 상태 변경/화면 중단 시 확인창을 닫고, 표시와 실제 확인 콜백에서 최신 정책을 재확인하도록 수정했다. 제한 true/null/false로 지연 콜백을 검증했다. 실제 주행/지역별 잠금 전환은 이번에 차량 상태를 조작하지 않아 미검증이다. 마켓/실차 UX 승인도 별개다.

## 7. 자동 테스트와 빌드

```powershell
.\scripts\build-codex.ps1 -Test
git diff --check
```

최종 표준 명령 **BUILD SUCCESSFUL, 종료 코드 0**. 최신 검증은 24초이며 APK assemble과 단위 테스트를 수행했다. 설치/화면 검증 APK SHA256: `394621643D630ABD1A918ACE05EAD4CCCE3B8DC163874DCF0E729DB47FC51C3D`.

**58개 통과 / 실패 0 / 오류 0 / 스킵 0**:

- 기존 28개: Vehicle 14, Navigation 13, 기본 1.
- 신규 30개: DestinationEngine 16, TripController 10, Snapshot/정책 4.

[빌드 로그](evidence/build-tests.txt), [JUnit 집계](evidence/test-summary.json).

필터링/6개 기분/4개 범위/시간·귀가/복귀/조건 유지/seed 재현/후보·시간 자료 없음/날씨/동행/최근 회피/차량 불명·fake·stale·단위 확인, 공개 전 비밀/즉시 공개/모의 도착/잘못된 전이/평가/중복 완료/취소/재시작/codec 손상/제한 시 지연 동작을 다룬다. 신규 테스트 실패를 확인한 후 구현했으며 RANGE 단위 미확인 사례도 실제 실패를 재현하고 수정했다.

외부 공급자 입력에 재사용할 때 큰 시간 값의 정수 합이 음수로 넘치는 사례도 테스트로 재현했다. Long으로 합계 범위를 검증해 유효하지 않은 후보를 제외하도록 수정했다.

UI 컴파일 중 View.foreground와 색 변수의 이름 충돌은 별도 색 이름으로 해결했다. 설치 실패와 도메인/도구 오류를 실제 앱 실행 성공과 섞어 집계하지 않았다. UI는 단위 테스트 58개에 포함된 것으로 부풀리지 않고 아래 ADB 검증으로 구분한다.

## 8. Pleos 실제 실행 결과

대상은 `emulator-5554`, 사용자 10, Pleos Connect v2.1.0/API 34, 화면상 P/0 km/h 상태다. 최종 APK 설치 `Success`, MainActivity COLD 실행과 DEMO 버튼 진입을 확인했다. [설치/시작](evidence/install-start.txt)

확인한 범위:

- 조건 선택·현재 요약·사용 시간/귀가 시각 모드 선택.
- READY/DEMO/ARRIVED UI 계층에서 목적지 이름 미표시.
- 모의 다음 회전과 힌트 상태, 도착 이벤트와 공개/평가/지도 기록.
- 앱 프로세스 종료 후 재실행해 READY/DEMO 진행·힌트 복원. 실제 위치/경로 복원이라고 주장하지 않음.
- 조건 불일치 시 편집 가능한 SETUP과 실패 설명.
- 최종 크기 UI의 취소/즉시 공개 및 ‘미리 공개’ 구분.

증거: [프로세스 재시작](evidence/restart-start.txt), [진행 복원 화면](evidence/demo-restored.png), [후보 없음](evidence/no-candidate.png), [취소](evidence/cancelled.png), [즉시 공개](evidence/early-reveal.png). 진행 복원/귀가 모드 캡처 일부는 76dp 크기 보완 전 동일 로직 검증 때 확보했으며 주요 A~F 캡처는 최종 UI로 갱신했다.

사용한 명령은 대상 serial/user를 명시했다.

```powershell
adb -s emulator-5554 install --user 10 -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start --user 10 -W -n com.surpride.app/.MainActivity
```

설치만으로 성공을 선언하지 않고 실제 화면과 UI 계층을 확인했다. Car UX 상태 불명/제한 정책의 단위 테스트와 실제 허용 상태 수신을 분리했다. 화면 재구성의 모든 형태/작은 창/큰 폰트 및 실제 주행 제한 전환은 별도 수동 검증이 필요하다.

마지막 APK에서도 기존 Vehicle RESPONDING/NaviHelper CONNECTED·활성 경로 false를 확인했다. [SDK 회귀](evidence/sdk-regression.txt). 완료 기록 3건은 재시작 전후 같았고 미리 공개 구분도 복원됐다. [UI 검증 집계](evidence/ui-validation.txt), [기록 복원 화면](evidence/mood-map-restored.png). 사용 가능한 버퍼의 해당 앱 Crash/ANR 기록은 각각 0이었다. [로그 상태](evidence/runtime-health.txt). 장시간 안정성 검증은 아니다. 최종 화면은 DEMO 시작 화면으로 남겼다.

## 9. 미구현 기능과 한계

- 실제 GPS/지도 타일/도로 geometry/경로 탐색/TBT 엔진/실시간 ETA·재탐색·음성 없음.
- 실제 장소/영업/주차/동반자 정책·날씨·차량 range 데이터 검증 없음.
- 기본 내비/알림/클러스터/음성까지 목적지를 숨기는 기능 없음. Phase 1B/1C의 판정을 변경하지 않음.
- 실제 방문/위치 기록, 사용자 계정·클라우드 동기화·추천 학습·LLM/STT 없음.
- 저장 키 분실/손상 시 자동 복구할 수 없으며 사용자 명시 초기화가 필요. 앱 삭제 시 로컬 기록 소멸.
- 실제 차량 주행과 다양한 화면/글꼴/회전·지역별 UX 제한, 앱마켓 승인 미검증. 현재는 정차용 DEMO다.

## 10. 실제 내비게이션 연동을 위한 다음 단계

1. 승인된 장소·지도·경로 공급자와 데이터 출처/라이선스·시간/거리 단위 계약을 확정한다. 가상 카탈로그를 provider 입력으로 교체하되 engine의 제약·점수 구조는 재사용한다.
2. 실제 위치와 경로 estimate를 얻어 범위·귀가·차량 거리 검증을 연결한다. 자료가 없으면 경고/실패를 유지하고 합성 시간을 실경로로 승격하지 않는다.
3. 현재 TripPresentation/명시적 상태 전이를 NavigationTemplate 어댑터에서 활용하되, **DEMO_DRIVING과 실제 안내를 별도 모드/상태로 구현**한다. nav-host-poc를 현재 앱에 자동 통합하지 않는다.
4. 단일 활성 경로·포커스·NavigationManager 시작/종료·중단 콜백, 실제 도착·이탈·재탐색을 검증한다.
5. 목적지 숨김 UX의 필수 정보·음성·클러스터·지도/이력 정책을 공식 확인하고 주행 UX 심사를 별도로 진행한다.
