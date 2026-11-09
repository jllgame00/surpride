# Phase 4A — 자연어 기분·추천 조건 통합

검증일: 2026-11-09. 정차 Pleos Connect v2.1.0/API 34 개발 에뮬레이터 한 대에서 수행했다. 실차 운행 검증이 아니다.

## 판정

- Phase 4A: **READY_WITH_LIMITATIONS**
- AI_PROVIDER: **RULE_BASED**. 기기 내 유한 규칙 해석이며 LLM 연결 성공이 아니다.
- STT: **PARTIAL**. 공식 SDK 초기화·요청·READY는 확인했으나 실제 음성 인식 시작/최종 텍스트는 수신하지 못했다.
- DESTINATION_PRIVACY: **PARTIAL**. 기존 시스템 채널의 미검증 상태를 승계한다.

## Git 및 보존 범위

시작: `main`, HEAD `8501bd55d201ed625c2d1da17711b8d60cb32ffb`, clean. 종료 HEAD/브랜치 동일, 이번 변경은 미커밋 상태다. 커밋·Push·reset·clean·강제 checkout·원격 변경을 하지 않았다. 기존 Phase 0~3B-4 문서, 기록, 테스트, Vehicle/NaviHelper, 지도·GPS·소유권·TTS·재탐색·복귀 및 FastAPI 소스를 보존했다.

## 1. 실제 사용 방식과 공식 지원 조사

### Gleo / 외부 LLM

[Gleo 핵심 개념](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/core-concepts), [App Capability](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/app-capability), [Schema 전달](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema), [Action/Callback](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/action_callback)을 확인했다. Gleo Interface는 검토된 App Schema의 함수를 Intent/Callback으로 연결하는 계약이다. 이를 임의 프롬프트를 받는 자유 대화형 LLM API로 가정하지 않았다. Schema 등록·승인 증거가 없어 임의의 Action이나 외부 진입점을 추가하지 않았다.

외부 LLM 및 Pleos 등록 credential은 환경변수/기존 서버 환경파일에서 **존재 여부만** 확인했으며 구성 근거가 없었다. 키 값은 출력하지 않았다. [Connect SDK 공개 참조](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/)와 기존 API 소개의 LLM 언급만으로 사용 가능한 인증·의존성·호출 계약이 확보됐다고 판단하지 않았다. 이번 단계에서는 FastAPI에 대화 전송 endpoint나 클라우드 인프라를 만들지 않았다.

### Pleos SpeechToText

[SDK 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro), [의존성](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/importing-sdk), [권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions), [등록](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/app-registration)을 기준으로 공개 AAR의 실제 클래스도 확인했다.

- 의존성: `ai.pleos.playground:SpeechToText:3.3.1` 하나를 기존 공식 저장소에서 추가했다.
- 권한: `pleos.car.permission.STT_SERVICE`. Package Manager에서 일반 설치 후 `granted=true` 확인. 강제 권한 부여 없음.
- `SpeechToText(applicationContext, Mode.ON_DEVICE)`, 한국어 locale. 등록 자격증명이 필요한 HYBRID/SERVER는 사용하지 않았다.
- 실제 계약: `initialize`, 초기화 성공/실패, `addListener/removeListener`, `request/stop/release`, `ResultListener`의 ready/recognition/partial/final/error.
- 요구 CAAS 4.1.29 이상에 대해 설치 버전 4.2.10-coral-aosp를 확인했다.
- 공개 AAR SHA-256: `35109ad13400a7fa103cca9fabbf9b785e63a6a98b17a0698a1aef51458d37bf`.

[프로젝트 등록 안내](https://document.pleos.ai/en/docs/pleos-only/create-my-project)의 STT/TTS/FusedLocation 공개 예정 표시와 개별 SDK 문서·공개 artifact 사이에는 접근 범위 차이가 남는다. ON_DEVICE 런타임 결과와 등록/마켓 승인을 구분하며, 사용 승인을 받았다고 주장하지 않는다.

## 2. 자연어 모델 및 상태

`MoodInputActivity → RuleBasedMoodInterpreter → MoodInterpretation/PreferencePatch → 사용자 확인 → 기존 DrivePreferences → DestinationEngine / RouteRecommendationEngine` 순서다. 입력 화면은 장소 검색·경로 시작·소유권 획득을 수행하지 않는다.

기존 6가지 기분(HEALING, EXCITEMENT, ENERGY, REFLECTION, COMFORT, ADVENTURE)을 유지한다. 예를 들어 답답해서 바람 쐬고 싶은 표현은 HEALING으로 제안한다. 이는 취향 분류이며 심리 상태·질환 진단이 아니다.

추출되지 않은 필드는 nullable UNKNOWN이다. 현재 선택값과 이번 문장에서 추출한 값을 화면에서 구분한다. UNKNOWN으로 기존 값을 덮어쓰지 않는다. scope 미지정은 UI에서 AUTO/미지정으로 표시하고 시간별 범위를 제안하지만, 사용자가 눌러야 기존 scope를 변경한다. 제안 범위는 실제 도로 반경이 아니다.

시간·왕복·동행·풍경·범위·랜덤 정도를 부분 patch로 적용한다. 장소 선호는 기존 ALL 조건을 유지하고, 제외는 하드 필터다. `바다보다는 숲`은 WATER 선호를 제거하고 NATURE를 추가하며 PET/CHILD 등 언급하지 않은 조건을 유지한다. WATER를 무조건 금지한다는 의미로 확대하지 않는다. 새 선호/제외가 기존 반대 집합에 있으면 서로 정리한다.

교차 유형 OR(바다나 숲), 지원되지 않는 혼잡도/특정 장소 지정, 진단 요구, 충돌 기분, 모호한 시각, 소수/음수 시간 등은 자동 적용을 막고 확인·수정을 요구한다. 모든 한국어를 이해하는 범용 모델이 아니다. 미지원 조건을 유지한 채 실행할 수 있다고 표시하지 않는다.

## 3. 구조화 출력 경계

`PreferencePatchJson`은 향후 provider 교체를 위한 검증 경계다. 이번 규칙 해석이 실제 외부 JSON 응답을 수신한 것은 아니다.

```json
{"mood":"HEALING","categories":["WATER"],"availableMinutes":90,"returnToStart":true}
```

허용 key: `mood`, `scope`, `categories`, `excludedCategories`, `removedCategories`, `companion`, `availableMinutes`, `returnByEpochMillis`, `returnToStart`, `randomness`. enum은 기존 모델만 허용한다. 알 수 없는 key, 중복 key, 숫자 문자열/boolean 강제 변환, 중복 enum, nested object, 잘못된 JSON, 잘못된 타입을 거부한다. 원문 최대 1,000자, JSON 최대 4,096자. 시간 1~10,080분, 랜덤 0~100, 귀가 시각은 유효한 양수 epoch 및 2100년 한도다.

귀가 시각은 Asia/Seoul 기준이다. 명시된 오늘/내일을 처리하되 지난 시각을 임의로 다음 날로 넘기지 않는다. 지원 문형 밖의 `오후 6시 30분에 집에 돌아와야 해` 같은 시각은 가용 30분으로 오인하지 않고 차단한다. 시각과 사용 가능 시간은 별도 제약이다.

`InterpretationRequests`는 요청 ID, 5초 timeout, 취소, 이전 callback 폐기, 잘못된 결과 거부를 처리한다. 테스트 provider로 오류/timeout/cancel을 검증했다. 이는 외부 LLM의 네트워크 호출 성공 증거가 아니다. AI/규칙 출력은 장소명·좌표·시스템 명령·경로 소유권을 지정할 수 없다.

## 4. 예제와 조건 수정

| 입력 | 결과 / 처리 |
|---|---|
| 오늘 좀 답답해서 바람 쐬고 싶어. 바다나 강이 보이는 곳으로 한 시간 반만 쓰고 싶어. 집으로 돌아와야 해. | HEALING, WATER, 90분, 왕복 true; 동행 UNKNOWN, 범위 미지정 |
| 시간은 두 시간으로 바꿔줘. 혼자 갈 거야. | 120분, SOLO만 수정; 기분·풍경·왕복 유지 |
| 아니 바다보다는 숲 | WATER 요구 제거, NATURE 추가, 나머지 유지 |
| 바다와 야경은 빼줘 | WATER/NIGHT 제외 |
| 혼자는 싫어 / 숙박은 안 해 | 대안 동행/범위를 추측하지 않고 확인 요구 |
| 사람 많은 곳은 싫어 | 혼잡도 데이터 미지원 표시, 자동 적용 차단 |
| 바다나 숲 / 1.5시간 / 랜덤 40.5 | OR 의미 또는 숫자 지원 범위에 대한 확인 요구 |

문장 수정 후에는 재해석 또는 명시적 현재 선택 확인이 필요하다. 해석 결과는 화면 내 draft이며 `확인한 조건 적용` 이전에는 기존 추천 조건을 변경하지 않는다. 현재 선택 유지, 기존 선택으로 복원, 적용 없이 닫기를 구분했다. 시간 preset이 기존 귀가 deadline을 지우지 않으며 별도의 명시적 해제 동작을 제공한다.

## 5. 음성 상태와 대체 입력

정차/전경 상태에서 사용자 버튼으로만 STT를 시작한다. `onReady`를 인식 시작으로 간주하지 않는다. FINAL만 텍스트 입력창에 반영하고, 이후에도 사용자가 해석·확인을 눌러야 한다. partial/final과 FAKE/PLEOS_ON_DEVICE 출처를 분리한다.

15초 인식 기한, 무음, 권한 거부, 미지원, 마이크·엔진·네트워크·언어 오류, 취소, 앱 중단을 유한 상태로 처리한다. 인식 종료 후 늦게 오는 최종 callback을 기다리며, 지난 시도의 callback은 폐기한다. 구독 해제·stop·release는 중복 정리에 안전하다. 권한 실패·무음 등은 가짜 source 테스트이며 실제 권한을 강제로 거부하지 않았다.

실제 에뮬레이터 로그는 `SDK_INITIALIZED → REQUEST_CALL_RETURNED → SDK_READY`, 앱 상태는 `INITIALIZING → NO_SPEECH`였다. `onStartedRecognition` 및 최종 문장은 없었다. 녹음 파일을 만들지 않았고, 텍스트 예시 버튼은 **음성 인식 아님**으로 표시한다. 원인 후보는 차량 마이크 입력/인식 서비스의 에뮬레이터 지원 범위이며 어느 하나로 단정하지 않는다. 실물 차량에서 시험하지 않았다.

## 6. 기존 추천 및 힌트 통합

DrivePreferences 끝에 기본 emptySet인 `excludedCategories`만 추가했다. DEMO와 LIVE에서 제외 조건을 먼저 필터링하고, include/exclude 충돌은 실패 처리한다. 실제 경로 API는 가용 시간·귀가 deadline·편도/복귀·체류·안전 여유를 기존 방식으로 평가한다. 경관 점수·날씨·주차·영업·SOC 기반 주행거리 등을 새로 추정하지 않았다. Vehicle UNKNOWN/UNSUPPORTED 처리는 기존 모델과 화면을 유지한다.

기존 snapshot의 byte 형식/히스토리는 보존했다. 제외 조건이 있을 때만 선택적 EXC1 후행 확장을 기록하며, 기존 형식은 그대로 읽고 empty 제외는 기존과 동일한 byte다. 손상 marker/enum/중복/길이를 거부하는 테스트가 있다. Activity Result에는 검증된 설정만 전달하며 대화·좌표·목적지·기록을 전달하지 않는다.

준비 힌트는 고정된 안전 정책을 사용한다. 물·편한 신발, 야외 겉옷, 긴 이동 휴식, 1박 숙소 확인을 제안하며 실제 우천/시설 운영/안전성을 확정하지 않는다. `출발 전 우천 예보를 직접 확인해 주세요`처럼 미확인 사실을 구분한다. 추천 점수와 목적지는 기존 엔진만 결정한다.

## 7. 개인정보 및 UX

원문/음성은 현재 입력 세션 RAM에만 둔다. EditText 자동 상태 저장을 끄고 파일·서버·분석 로그에 대화를 기록하지 않는다. 화면 재구성은 ViewModel draft로 유지하되 프로세스 종료 후 원문 복원을 보장하지 않는다. 확인한 구조화 취향과 기존 평가 기록은 기존 앱 저장 정책을 따른다. 로그는 source/phase/유한 오류 코드만 사용한다.

외부 AI로 목적지 식별정보를 보낼 경로가 없다. 기존 FastAPI는 경로 요청 전용이며 키는 서버에만 있다. 이번 입력 기능은 기존 내비게이션 동의·소유권을 변경하지 않는다. UX 제한이 불명확하거나 운행 제한이 걸리면 입력/해석/STT를 취소·차단한다. 선택 UI가 계속 대체 수단이다. 실제 운행 중 장문 대화는 구현하지 않았다.

## 8. 빌드 및 자동 테스트

| 대상 | 기존 | 최종 | 결과 |
|---|---:|---:|---|
| Android app | 98 | 155 | PASS |
| nav-host-poc | 166 | 166 | PASS |
| FastAPI | 42 | 42 | PASS |
| 합계 | 306 | **363** | **실패 0** |

```powershell
.\scripts\build-codex.ps1 -Test
.\scripts\build-nav-host-poc.ps1 -Test
& backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
git diff --check
```

기기 내 규칙/JSON 26개, STT controller 13개, 요청/Activity contract 8개, 제외 엔진·snapshot 10개로 57개를 추가했다. 기존 테스트를 삭제/비활성화하지 않았다. 실제 결함을 실패 테스트로 재현한 후 수정했다. 마지막 main assembleDebug/testDebugUnitTest 성공, PoC assembleDebug/testDebugUnitTest 성공(변경 없는 작업은 UP-TO-DATE). FastAPI 42 PASS이며 기존 Starlette/httpx 사용 중단 예정 경고 1건이 있다. `git diff --check` 오류 없음; 기존 Windows 줄바꿈 경고만 있다.

## 9. Pleos 실제 검증 및 증거

ADB 설치 `Success`, 앱 실제 표시. 기존 APK에 -r 설치하여 앱 데이터 보존. 정차 P/0km/h UI에서 명시적 조작으로 진행했다. 기존 public 개발 출발지와 공개 장소만 사용하며 개인 위치를 저장하지 않았다.

1. 자연어 예시 → 90분/WATER/HEALING/왕복 해석. 동행 UNKNOWN과 현재 선택을 분리 표시.
2. 두 시간·혼자 수정 → 120분/SOLO, 기존 풍경/왕복 유지. 확인 후 기존 화면과 DATA LAB에 전달.
3. 동일 조건 DEMO 샘플 없음 → 추천 실패를 정직하게 표시. 자동 조건 완화 없음.
4. 기존 서버 LIVE 경로 평가 → 120분 예산 이내 왕복 코스 선정, 별도 복귀 조회 포함. 공급자 호출 시점에 따라 총 91~94분/17.9km였고 실제 주행 완료값이 아니다.
5. 최신 main APK에서 텍스트 예시를 재해석하고 120분/SOLO로 수정·확인한 뒤 실제 코스를 PoC로 전달했다. 예시 버튼으로 입력한 텍스트이며 사람의 실제 음성 입력 성공은 아니다.
6. OEM route inactive 및 다른 focus 없음 확인 후 PoC의 일회성 동의 행으로 시작했다. 공식 포커스 `owned={1}`, `TRIP returned=true`, `destinations=0`, `ACTIVE`를 확인했다. 공개 경로의 처음 100~280m 위치 재현에서 DEVICE_API 진행거리·다음 안내 거리·지도/회전 표시가 갱신됐다. `06-map-guidance.png`에는 목적지 비공개·실제 OSM·전방 경로·다음 안내가 함께 보인다. 내부 ETA는 기존 API 기록 비율 추정이며 최신 교통 ETA라고 표시하지 않는다.
7. 안내를 종료하여 focus 해제 후 마지막 구간으로 위치를 옮겼다. 명시적으로 다시 시작해 접근 구간을 재현했다. 첫 접근에서는 도착 후보 전 중지 동작이 실행돼 공개되지 않았고, 다시 시작한 최종 근접/유지 재현에서 후보가 형성됐다. 후보 형성 후 **정차·도착 확인**을 눌러 `revealed=true`, `STOPPED`, focus `owned={}`와 실제 공개 화면을 확인했다. 전체 경로를 연속 주행한 증거가 아니다. 경로 대표점의 주차/진입 안전성은 여전히 UNKNOWN이다.
8. 기존 `안내 종료 후 SURPRIDE 평가·기록`에서 공개된 결과에 3/5를 선택하고 저장했다. `LIVE 에뮬레이터 평가를 저장했습니다` 표시를 확인했다. 기존 DEMO 기분 지도/기록과 LIVE AVD 기록은 분리된 기존 구조를 유지한다. 자동 복귀를 시작하지 않았다.

증거는 [evidence/mood-input](evidence/mood-input/)에 보관한다. `01-editor.png`, `02-rule-based-extraction.png`, `03-correction-preview.png`는 입력과 조건 확인 화면이다. `04-demo-preparation.png`는 **추천 실패 화면**이며 성공 증거가 아니다. `05-live-private-ready.png`는 목적지 비공개 LIVE 평가 결과다. 개발용 공개 예시 문장만 화면에 남겼으며 음성 원본/개인 대화/비밀키/raw GPS 로그는 포함하지 않는다.

`06-map-guidance.png`, `07-arrival-reveal.png`, `08-result-recorded.png`는 지도 안내·정차 확인 후 공개·저장 결과다. `stt-attempt.txt`는 실제 SDK 유한 상태 로그만, `test-summary.json`은 테스트 집계만 보관한다. `03-confirmed-preferences.png`는 확인 전 편집 화면을 촬영한 초기 증거이며 확정 완료의 근거로 사용하지 않는다.

안정성: main과 PoC의 현재 PID에서 FATAL EXCEPTION이 없었고, 17:50 이후 보존된 event buffer에서 두 패키지의 Crash/ANR 이벤트는 0건이었다. 전역 보존 buffer에는 **15:17 및 15:26의 과거 PoC IllegalArgumentException 2건**과 다른 입력기 프로세스의 crash가 있어 전체 에뮬레이터가 무오류라고 주장하지 않는다. 이번 실행의 실패로 혼합하지 않았으며 원본 exception/location 로그는 저장하지 않았다. `stability.json`에 범위를 명시했다. 종료 시 안내 focus는 해제 상태이고, 이번에 띄운 loopback 개발 서버를 중지했다.

## 10. 목적지 비공개

| 채널 | 판정 | 근거 / 한계 |
|---|---|---|
| 자연어 해석 UI/데이터 계약 | PASS | 취향 enum·시간만 취급, 목적지 데이터 미전달 |
| 외부 AI 대화 | PASS(전송 없음) | provider는 RULE_BASED, 외부 AI 호출 자체 없음 |
| 추천 준비 UI | PASS | 이름·주소·좌표 대신 조건/힌트/시간 |
| NavigationTemplate / Surface / Trip | PASS(이번 비공개 구간 범위) | 실제 지도 화면과 destinations=0 확인. 도로 라벨·현재 위치로 추측 가능; 정차 확인 후 정상 공개 |
| Pleos 홈·알림·계기판/HUD·최근 기록 | UNVERIFIED | Phase 4A에서 전체 채널 활성 감사를 반복하지 않음 |
| 기존 TTS | 기존 검증 승계 | 이번 STT 실험은 목적지 발화/음성 안내 검증이 아님 |

## 11. 변경 파일

- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`: 공식 STT 의존성·권한, 내부 입력 Activity.
- `app/.../moodinput/MoodInputActivity.kt`, `PreferenceInputContract.kt`, `InterpretationRequests.kt`: 정차 조건 확인/취소/시간 제한·private Activity Result.
- `app/.../moodinput/core/PreferencePatch.kt`, `PreferencePatchJson.kt`, `RuleBasedMoodInterpreter.kt`: 부분 수정, 엄격 schema, 유한 한국어 규칙·준비 힌트.
- `app/.../moodinput/voice/SpeechInputController.kt`, `PleosSpeechInputSource.kt`: 상태 모델 및 실제 ON_DEVICE adapter.
- 기존 `ExperienceActivity.kt`, `DriveModels.kt`, `DestinationEngine.kt`, `TripController.kt`, `TripSnapshotCodec.kt`, `RealRouteActivity.kt`, `RouteRecommendationEngine.kt`: 입력 연결·제외 조건·안전 힌트·형식 호환.
- 신규 테스트: `PreferenceExclusionsTest.kt`, moodinput의 parser/JSON/controller/contract/request 테스트.
- `docs/phase4/AI_MOOD_IMPLEMENTATION_PLAN.md`, 이 문서, 공개 개발 예시 증거.

## 12. 미구현 및 Phase 4B

Gleo Schema 등록·심사/공식 자유대화 계약, 외부 LLM 키·서버 연동, 실제 STT 최종 텍스트 수신은 미완료다. 혼잡도·임의 장소 지정·복잡한 부정/OR·모든 한국어 문형을 지원하지 않는다. 기분으로 새 장소/안전/날씨를 생성하지 않는다. 기존 추천 가능한 실제 장소는 공개 5곳, 동행 검증은 SOLO로 제한된다. 조건이 엄격하면 추천 결과가 없을 수 있다.

다음 단계는 (1) Pleos 사무국에 ON_DEVICE 마이크/인식 서비스 및 등록 접근 범위를 확인하고 실제 final callback 검증, (2) 대표 한국어 corpus로 실패 문형 확대와 명확한 확인 질문 설계, (3) 필요 시 동의·최소 전송·보존 정책을 갖춘 서버 LLM을 이 schema 뒤에 연결, (4) 실제 장소/동행/날씨 데이터 근거 확장, (5) 기존 시스템 채널 비공개·실차 안전성의 별도 승인/검증이다. 외부 AI 도입 시에도 목적지 식별정보를 프롬프트에 보내지 않는 구조를 유지한다.
