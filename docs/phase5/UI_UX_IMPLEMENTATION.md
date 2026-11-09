# SURPRIDE Phase 5B — UI/UX 구현 및 Pleos 검증

2026-11-09 · **READY_WITH_LIMITATIONS** · Phase 5C 조건부 진입 가능

승인된 짙은 녹색·라임, `S /`, 끝을 남겨 둔 경로 곡선을 유지하면서 Native View와 지도 Surface의 정보 위계를 정리했다. 공통 토큰, 시작·조건 입력, 자연어 확인, 코스 준비, 지도 안내, 공개·평가·기록을 구현했다. 최종 debug APK 두 개를 Pleos에 설치하고 실제 경로 재탐색, 정차 확인 후 공개, 평가 저장, 명시적 복귀와 복귀 완료를 검증했다.

**전체 481 PASS = app 196 + PoC 195 + FastAPI 90.** 시스템 전체 비공개·STT·실차 안전성의 판정은 이번 UI 변경으로 올리지 않는다.

## 1. 기준과 Git 보존

- 시작: `main`, HEAD `bdb7d23bf16972f259c951df1af0ff2f7ad4c24b`, clean. Phase 5A 문서에 남은 과거 HEAD와 이번 구현의 시작 HEAD는 다르다.
- 종료: 같은 브랜치/HEAD. 기존 파일 12개 수정, 새 제품·테스트 파일 14개, 이 보고서와 after 증거만 추가했다. 커밋·Push·reset·clean·강제 checkout 없음.
- 시작 파일 488개의 해시와 비교해 삭제 0개, 의도한 UI 파일 외 기존 파일 변경 0개를 확인했다. Phase 5A 문서·before PNG, backend, 기존 테스트, 빌드 스크립트·의존성 버전은 보존했다.
- 검증 전 앱 비공개 캐시에 보관한 DEMO 설정/기록과 LIVE 에뮬레이터 기록 파일을 종료 후 복원했다. 두 파일의 바이트 해시가 원본과 일치한다. 이번 평가 기록은 검증 증거에만 보이며 기존 사용자 기록에 추가로 남기지 않았다.
- 앱 화면 설정은 차량 설정 따르기로 복원했다. 일시적인 글자 크기 1.3 요청을 삭제했고 Android가 기본값 1.0을 반환한다. 시스템 야간 설정은 원래 `no`, 로컬 프록시는 종료, 두 앱은 종료했다.
- 기계 판독 요약: [validation-summary.json](evidence/after/validation-summary.json). 키·좌표·대화 원문·사용자 기록 파일은 포함하지 않았다.

## 2. 실제 적용한 디자인 시스템

[Phase 5A 방향](DESIGN_DIRECTION.md)을 출발점으로 삼았다. 웹의 작은 버튼·글자 수치를 자동차 UI에 적용하지 않았다. Kotlin/XML 및 기존 Canvas 구조를 유지했다.

| 역할 | Light | Dark |
|---|---|---|
| 배경 | `#F6F7F2` | `#0A1818` |
| Surface / raised | `#FFFFFF` / `#EDF1E9` | `#152827` / `#203532` |
| 본문 / 보조 | `#19231E` / `#4B5C52` | `#EBF1E1` / `#9AB1A5` |
| 강조 글자 | `#3F5F27` | `#D7F28B` |
| 주요 행동 채움 / 글자 | `#D7F28B` / `#19231E` | 동일 |
| 선택 / 포커스 | 체크·2dp 강조선 / 3dp 포커스선 | 동일한 의미 체계 |
| 경계선 | `#D5DED3` | `#2D4138` |
| 주의 / 오류 | `#78531A` / `#973D35` | `#F2CF85` / `#F3ABA3` |

- 4dp grid, 화면 inset 32dp, 카드 padding 24dp, 버튼 radius 12dp, 카드 16dp. 기본 컨트롤 최소 높이 76dp이며 호스트 컨트롤의 강제 크기를 뜻하지 않는다.
- 본문 24sp, 보조 20sp, 제목 28–32sp, 브랜드·발견 장면은 별도 큰 크기. 시스템 sans-serif/한글 fallback, 본문 400·제목 600 중심. 새 폰트·이미지·지도 SDK 없음.
- 주요 행동은 라임 채움, 선택은 체크·테두리·`selected`/접근성 상태로 구별했다. Native Button 역할, ripple, focus, disabled를 유지했다.
- Light에서는 라임 글자를 흰 배경에 쓰지 않는다. 불투명 sRGB 계산상 본문/Surface Light 16.15:1·Dark 13.34:1, 보조 7.11:1·6.75:1, onAccent 13.04:1이다. 경고·오류·비활성/raised도 4.5:1 이상이다. 지도 타일·반투명 효과·호스트·실물 패널 전체의 대비 인증은 아니다.
- 앱의 정차용 화면 표시 선택에 차량 설정/밝은 화면/어두운 화면을 제공했다. 지도·OEM·호스트의 테마를 강제로 바꾸지 않는다.
- 공개는 경로 곡선의 `?`가 확인된 종점으로 바뀌는 정적 구성을 사용한다. 반복·화려한 모션은 추가하지 않았다. 짧은 공개 애니메이션은 필수 구현으로 간주하지 않았다.

## 3. 화면별 구현과 Before/After

Before는 Phase 5A 원본 그대로다. 동일한 Pleos 2560×1440/density160/OEM 창에서 비교했다. 추천 조건·조회 시점·공개 장소는 일부 다르므로 픽셀 차이를 디자인 개선률로 계산하지 않는다.

`F`는 최종 APK 캡처다. 숫자만 있는 파일은 단계별 APK의 중간 검증이다. **69장은 상태·반복·전환까지 포함한 캡처 수이며 69개 독립 화면의 완료 수가 아니다.**

| 화면 | Before | After | 구현 결과 |
|---|---|---|---|
| 시작 | [01](evidence/before/01-launch.png) | [Dark 최종](evidence/after/F30-launch-dark-final.png), [Light 최종](evidence/after/F34-launch-system-light-final.png) | 브랜드·슬로건·경로 곡선과 단일 준비 CTA. SDK 두 열은 처음부터 보이지 않음 |
| 제품 홈 | [02](evidence/before/02-home.png) | [최종](evidence/after/F31-home-dark-final.png) | 조건 선택 우선, 문장 입력과 실제 경로 준비는 보조. 정차 에뮬레이터 한계 유지 |
| 기분·조건 | [03](evidence/before/03-conditions.png) | [Dark 최종](evidence/after/F28-mood-selection-dark.png), [Light 최종](evidence/after/F35-conditions-light-final.png) | 6기분·장소·요약 보존. 선택 체크/외곽선과 다음 CTA 구분 |
| 자연어·확인 | [04](evidence/before/04-natural-input.png), [05](evidence/before/05-condition-confirm.png) | [최종 부분 수정](evidence/after/F04-partial-edit-confirmed.png) | 입력→해석 결과→고정 하단 적용/취소. 변경 항목만 표시, 상세 보정·예제 접기 |
| 시간·왕복 | [06](evidence/before/06-time-settings.png) | [단계 캡처](evidence/after/07-time-light.png) | 기존 범위·귀가/시간·왕복 선택 보존, 동일 버튼 체계 |
| 비밀 코스·힌트 | [09](evidence/before/09-course-ready.png) | [DEMO 단계](evidence/after/08-course-ready-light.png), [실제 API 최종](evidence/after/F29-real-course-ready-final.png) | 총시간·이동/체류/복귀/여유·준비물을 우선. UNKNOWN 및 개발용 출발지 고지 |
| 지도 비밀 안내 | [Phase 4B](../phase4/evidence/drive-experience/03-active-map.png) | [최종](evidence/after/F08-active-secret-map.png) | 실제 지도·marker·전방 경로·회전 유지. 하단 raw 진단을 상태·추정 ETA/거리·도로명으로 정리 |
| 이탈·재탐색 | [이전 loading](../phase3/evidence/reroute-voice-return/03-server-unavailable-loading.png) | [위치 불확실](evidence/after/F09-reroute-state.png), [재탐색 후 최종](evidence/after/F10-reroute-result.png) | 불확실 상태 우선, loading Trip/새 경로는 로그로 별도 확인. F09를 loading 문구 캡처로 간주하지 않음 |
| 즉시 공개 조작 | [11](evidence/before/11-immediate-reveal-rating.png) | [DEMO 공개 단계](evidence/after/22-destination-revealed.png), [최종 메뉴](evidence/after/F16-before-arrival-confirm.png) | 기존 공개 로직 보존. 최종 활성 세션의 즉시 공개 클릭 후 유지까지는 이번 회차 미재검증 |
| 도착 공개·평가 | [16](evidence/before/16-arrival-reveal-rating.png) | [지도 최종](evidence/after/F17-arrived-revealed.png), [앱 최종](evidence/after/F19-live-result-revealed.png), [저장](evidence/after/F20-live-result-saved.png) | 정차 확인→종료→장소명·이유→평가. 점수 선택 전 저장 비활성, 명시적 복귀 |
| 기분 지도·상세 | [12](evidence/before/12-mood-map.png) | [최종](evidence/after/F32-mood-records-final.png), [상세](evidence/after/F33-record-detail-final.png) | 6기분 개념도·텍스트 요약, DEMO/실제 경로 에뮬레이터 기록 구분. 없는 이동시간·기분 필드를 지어내지 않음 |
| 복귀 안내 | [이전 활성](../phase3/evidence/reroute-voice-return/18-final-return-active.png) | [최종 활성](evidence/after/F24-return-active.png), [완료](evidence/after/F27-return-completed.png) | 같은 지도·안내 표현 재사용. 요청/시작/정차 도착 확인을 분리 |
| 개발 진단 | [01](evidence/before/01-launch.png) | [단계 캡처](evidence/after/18-debug-sdk-separated.png) | debug 전용 진입으로 분리. 기존 Vehicle/NaviHelper 기능 유지 |

### 유지한 좋은 요소

S /, 한국어 슬로건, 녹색·라임, 경로 곡선, 넓은 터치 요소, 6기분과 현재 선택 요약, 명시적 시작·공개·도착 확인, 평가 전 저장 제한을 유지했다. 실제 지도/위치/경로/OSM 표기와 추천·경로·재탐색·음성·복귀의 기존 엔진은 재작성하지 않았다.

## 4. 앱 소유 UI와 호스트 경계

| 소유자 | 이번 변경 | 보존 / 제한 |
|---|---|---|
| 앱 View/XML | 시작·조건·문장 입력·준비·공개·평가·기록·정차 대화상자 | Kotlin/XML 유지, 정차 UX gate 및 기존 상태/저장 계약 보존 |
| 앱 지도 Surface | 측정된 3행 footer, dpi/sp 글자, 상태 우선순위, 지도 출처 영역 | 실제 도로·위치·방향·경로·Follow Camera 유지, 목적지 핀/Overview 추가 없음 |
| AndroidX/Pleos 호스트 | 공식 template/action으로 조작 연결 | 회전 카드 위치·폰트·높이·OEM GNB를 강제 변경하지 않음 |

실제 Pleos는 일반 action strip의 첫 행동만 표시하는 상황이 있었다. 첫 행동을 **종료·조작** 진입으로 두고, 활성 안내의 **같은 Screen**에서 공식 ListTemplate 정차 메뉴를 반환한다. 최상단은 안내 종료, 그 뒤 즉시 공개·음성·조건부 도착 확인·지도 복귀다. Surface에 임의 터치 버튼을 그리지 않았다.

별도 Screen push는 기존 `onStop`의 소유권/GPS 정리를 유발할 수 있어 활성 메뉴에 사용하지 않았다. 같은 Screen 메뉴와 Back 복귀를 실제로 시험했고 ACTIVE 세션의 Trip/GPS 갱신이 유지됐다. 실제 Screen 종료·호스트 중단의 기존 정리는 유지한다. 이 정차 개발 메뉴를 주행 중 복잡한 메뉴 사용 승인으로 해석하지 않는다.

**ETA 한계:** [F07](evidence/after/F07-navigation-entry.png)의 기존 정적 MOCK PoC에서는 native ETA 카드 잘림이 여전히 재현된다. 활성 SURPRIDE의 앱 footer는 측정/clip으로 개선했지만 호스트 카드 자체를 해결했다고 주장하지 않는다. 목적지 없는 Trip의 호스트 최종 ETA와 앱 자체의 경로 기반 추정 시간도 구별한다.

## 5. Debug UI 분리

- Main SDK 패널은 초기 숨김, debuggable 및 정차 조건에서만 진입하고 필요한 시점에 SDK를 초기화한다.
- Experience 내부 추천 숫자는 debug 코스 진단으로 이동했다. 사용자 공개 화면은 선정 이유와 평가를 먼저 보여 준다.
- 자연어 raw 상태/오류·예제, 실제 경로의 비교/Mock/원시 provenance는 debug 확장으로 이동했다.
- 활성 지도 footer에서 callback/Trip 카운터·backend·raw enum을 제거하고 정차 debug 진단으로 분리했다. 위치 저하·경로 실패·중단·미확인 안내는 그대로 표시한다.
- debug 조건을 소스에서 확인했다. release APK의 전체 화면 실행은 이번 범위에서 하지 않았으므로 release 실화면 검증은 미완료다. nav-host-poc 자체는 개발 PoC다.

## 6. 접근성·레이아웃·피드백

- 최종 Light 조건 화면의 UIAutomator에서 앱 Button 20개의 실제 bounds를 확인했다. 모두 최소 높이 76px이며 density160에서 76dp에 대응한다. 선택 상태와 체크를 확인했다. 모든 호스트·모든 대화상자의 터치 검증으로 확대하지 않는다.
- 앱 rail을 스크롤 컨테이너로 바꾸고 홈/준비의 긴 설명 영역도 스크롤 가능하게 했다. 기록 그림은 최소 높이와 dp/sp·접근성 텍스트 요약을 제공한다.
- Surface는 글꼴 metrics와 허용 영역으로 행 수를 계산한다. 한 행이 들어갈 때 오류/중단 등 최우선 상태를 남기고, 너무 작은 지도는 출처가 잘못 가려지지 않게 생략한다. 한 행조차 읽을 수 없는 창 크기를 지원한다고 주장하지 않는다.
- Light/Dark 앱 화면을 실제 캡처했다. 앱 자체 표시 선택을 이용했으며 OEM 주야간 강제 변경으로 검증하지 않았다.
- 기본 해상도에서 주요 CTA·회전·OSM 출처·앱 footer가 동시에 보인다. 긴 자연어 개인정보 설명이 한글 음절 단위로 줄바꿈되는 부분은 추가 문구/레이아웃 다듬기가 필요하다.
- [F06](evidence/after/F06-conditions-large-font.png)은 공식 설정에 1.3을 요청한 시험이다. 실제 확대 적용 범위가 충분히 확인되지 않아 큰 글자 전체 합격으로 처리하지 않는다. [29](evidence/after/29-natural-dark-focus-only.png)는 입력 포커스만 보이며 소프트 IME가 열린 증거가 아니다.
- TalkBack 실제 읽기, 물리 키/로터리 전 경로, IME viewport, 여러 밀도·짧은 창의 실화면, 야간 지도/빛 반사/실물 디스플레이는 미검증이다. 작은 footer·상태 우선순위는 합성 단위 테스트와 실제 한 해상도 결과를 분리한다.

## 7. 실제 사용자 흐름·데이터 검증

환경은 Pleos Connect v2.1.0/API34, Car App API4, `emulator-5554`, 사용자10이다. 모든 시험에서 정차 상태(P/0km/h)를 유지했다. OEM 활성 경로 없음과 포커스 조건을 확인하고 화면의 명시적 시작/종료를 사용했다.

| 항목 | 결과 / 근거 | 한계 |
|---|---|---|
| 자연어 조건 수정 | 텍스트 예제로 120분·혼자 추출, 기존 기분·범위·왕복·장소 조건 유지 → 적용 확인 | RULE_BASED. 실제 LLM/STT 성공 아님 |
| 추천·실제 경로 준비 | 기존 FastAPI live 프록시와 Kakao 결과로 후보 비교, 최종 재조회에서 총71분·16.8km 확인 | 공개 개발용 출발지/관광 중심 좌표. 현재 위치 출발·차량 진입/주차 검증 아님. 시점에 따라 ETA 변함 |
| 활성 지도 | 공식 포커스, `TRIP returned=true … destinations=0 state=ACTIVE`, 도로·marker·전방 경로·회전 표시 | 호스트 호출 반환과 시각 확인을 구별. 시스템 전체 수용/표시 계약 보장 아님 |
| GPS·재탐색 | 공식 emulator 위치 재현→DEVICE_API 수신, loading Trip/ACTIVE 유지, Kakao 새 경로로 교체·지도/도로명 갱신 | 정차 AVD 위치 입력. 실제 차량 이동 아님 |
| TTS | 메뉴 ON, PLEOS_ON_DEVICE `requested=1 started=1 completed=1 errors=0`, audio focus 기반 기존 코드 사용 | 완료 콜백 검증. 실제 청취/발음 품질은 미검증 |
| 도착·공개·저장 | 도착 접근 재현→명시적 정차 확인→STOPPED→목적지 공개→4점 저장→기록 상세 확인 | 공개 시험 장소의 에뮬레이터 체험이며 실제 방문 인증 아님 |
| 복귀 | 사용자 요청으로 실제 복귀 경로 준비, `autoStart=false`, 별도 시작→활성 안내→정차 확인→복귀 완료 | 교통·시간 내 귀가 보장 아님 |
| 종료·복원 | 두 도착 확인에서 `ownerReleased=STOPPED`, 검증 종료 후 앱 종료·원본 기록 복원 | crash 복구/장시간 운행 전체를 새로 실험한 것은 아님 |

위치 재현은 경로 앞부분과 도착 접근 부분을 사용했다. 종점 근처 재배치는 안내를 먼저 중지한 뒤 수행하고 다시 명시적으로 시작했다. 전체 경로를 끊김 없이 주행한 시험으로 표현하지 않는다.

[선별 이벤트](evidence/after/navigation-events.txt)는 집계 상태·호스트 요청·재탐색/음성/도착 이벤트만 포함한다. 좌표·토큰·원문 API 응답을 저장하지 않았다. 화면의 지역·공개 장소는 기존 공개 개발 시험 데이터다.

## 8. 목적지 비공개와 기능 사실

**DESTINATION_PRIVACY = PARTIAL** 유지.

| 채널 | 판정 | 이번 근거 |
|---|---|---|
| SURPRIDE Template | PASS — 확인 범위 | 공개 전 목적지명·주소 없음, 회전/거리 유지 |
| 지도 Surface | PASS — 명시적 목적지 표현 | 최종 핀·Overview 없음. 일반 지도 라벨·현재 위치로 추측 가능성은 남음 |
| AndroidX Trip | PASS — 공개 전 보낸 계약 | 목적지 목록0·ACTIVE 호출 반환 확인. 공개 후 허용된 전환과 구별 |
| Pleos 홈 | UNVERIFIED | 활성 세션의 홈 메타데이터 전체 미검증 |
| 시스템 알림 | UNVERIFIED | 목적지 채널 전체 미검증 |
| 클러스터/HUD | UNVERIFIED | 지원/표시 미검증 |
| TTS | PASS — 제한된 생성/콜백 범위 | 생성 문구 비공개 테스트 및 완료 콜백. 음향 청취 검증은 아님 |
| 최근 목적지·이력 | UNVERIFIED | OEM/호스트 기록 전체 미검증 |

`AI_PROVIDER=RULE_BASED`, `STT=PARTIAL`, `ROUTE_QUALITY_EVIDENCE=PARTIAL`, 실제 경관 경유 CLOSED/검증 후보0, 실차 미검증을 유지했다. UI는 “기기 안에서 규칙으로 조건 정리”, “실제 자동차 경로 조회 결과·조회 시점 예상”, “가상 체험”, “정차 에뮬레이터”를 구별한다. UNKNOWN 차량 주행 가능 거리·날씨·주차·영업시간을 임의 확정하지 않았다.

## 9. 빌드·테스트·설치

기존 명령을 재사용했다. 버전·Gradle 전역 설정·외부 캐시 권한 변경 없음.

```powershell
.\scripts\build-codex.ps1 -Test
.\scripts\build-nav-host-poc.ps1 -Test
.\backend\.venv\Scripts\python.exe -m pytest backend/tests -q
git diff --check
```

| 단계 | app | PoC | FastAPI | 결과 |
|---|---:|---:|---:|---|
| 시작 회귀 | 187 | 175 | 90 | 452 PASS |
| 5B-1 공통/입력 | 196 | — | — | 빌드 성공, Light 실제 실행 |
| 5B-2 지도/분리 | — | 185 | — | 빌드 성공, 실제 지도·조작 확인 |
| 5B-3 공개/기록/메뉴 개선 | 196 | 190 | — | 빌드 성공, 평가/기록·Dark 실행 |
| 5B-4 최종 | **196** | **195** | **90** | **481 PASS, 실패/오류/skip 0** |

신규 29개: MoodInputPresentation 9, NavigationFooter 10, SurfaceFooterLayout 7, NavigationControlMenu 3. 기존 테스트를 삭제/수정해 통과시키지 않았다. 상태·개인정보·작은 영역·공식 조작 계약을 검증했으며 단순 디자인 숫자 복제 테스트는 추가하지 않았다. 초기 RED는 새 helper가 없는 컴파일 실패 확인이며 모든 새 assertion의 독립 RED 증거로 과장하지 않는다.

최종 app/PoC `assembleDebug` 및 `testDebugUnitTest` 성공. 로컬 로그는 Git 제외 `.gradle/phase5b/`에 있다. FastAPI 90 PASS와 함께 기존 Starlette deprecation·pytest cache 권한 경고가 있었다. 테스트 실패가 아니며 cache 삭제/권한 우회로 해결하지 않았다. PoC Conscrypt/JDK native-access 경고도 비치명적이다. `git diff --check` 종료0; Git의 LF/CRLF 변환 예고만 남았다.

최종 두 APK는 사용자10에 `install -r` 성공했고, 설치된 own-package APK SHA256과 빌드 산출물 해시가 일치했다. 값은 validation-summary에 있다. 실제 화면/이벤트 검증은 이 APK에서 수행했다.

Crash buffer에는 기존 이력이 남아 있었다. 사용 가능한 exit history에서 PoC의 15:26:40 크래시 1건을 발견했으나 **최종 설치 21:28:54 이후 두 패키지의 Crash/ANR 이력은 0건**이었다. 이전 buffer를 지우거나 전체 기록이 깨끗하다고 주장하지 않는다. 이 결과는 이번 제한된 실행 시간의 관측이다.

## 10. 변경 파일

경로는 루트 기준. 이 외 추천/경로/GPS/소유권 도메인·backend·빌드 파일은 수정하지 않았다.

| 수정 파일 | 내용 |
|---|---|
| `app/src/main/java/com/surpride/app/MainActivity.kt` | 제품 시작/정차 표시 선택/debug 진입 |
| `app/src/main/java/com/surpride/app/experience/ExperienceActivity.kt` | 공통 선택·준비·공개·기록·스크롤 |
| `app/src/main/java/com/surpride/app/experience/ExperienceArt.kt` | 모티프·기분 개념도 dp/sp·접근성 |
| `app/src/main/java/com/surpride/app/moodinput/MoodInputActivity.kt` | 입력/확인/고정 적용·보정 분리 |
| `app/src/main/java/com/surpride/app/realdata/RealRouteActivity.kt` | 사용자 준비/실제 데이터 고지/debug 확장 |
| `app/src/main/java/com/surpride/app/realdata/LiveNavigationResultActivity.kt` | 발견/평가/복귀 UI |
| `app/src/main/res/layout/activity_main.xml` | 브랜드 시작 배치 |
| `app/src/main/res/values/start_colors.xml` | Light 토큰 |
| `app/src/main/res/values/themes.xml`, `app/src/main/res/values-night/themes.xml` | 기본 테마 통일 |
| `nav-host-poc/src/main/java/com/surpride/navhostpoc/ActiveMysteryScreen.kt` | footer·공식 정차 메뉴·진단 분리 |
| `nav-host-poc/src/main/java/com/surpride/navhostpoc/map/RoadMapRenderer.kt` | 라벨·출처 크기/색/상태 표현 |

| 새 파일 | 내용 |
|---|---|
| `app/src/main/java/com/surpride/app/ui/SurprideUi.kt` | 최소 Native UI 공통 helper |
| `app/src/main/java/com/surpride/app/moodinput/MoodInputPresentation.kt` | 사용자용 조건 변경/상태 문구 |
| `app/src/main/res/values/design_tokens.xml` | dp/sp·버튼 토큰 |
| `app/src/main/res/values-night/start_colors.xml` | Dark 색 |
| `app/src/main/res/drawable/ic_selection_check.xml` | 선택 체크 |
| `app/src/test/java/com/surpride/app/moodinput/MoodInputPresentationTest.kt` | 입력 presentation 9 tests |
| `nav-host-poc/src/main/java/com/surpride/navhostpoc/NavigationFooter.kt` | 비공개·상태 우선 presentation |
| `nav-host-poc/src/main/java/com/surpride/navhostpoc/SurfaceFooterLayout.kt` | 안전 영역/행 측정 |
| `nav-host-poc/src/main/java/com/surpride/navhostpoc/NavigationControlMenu.kt` | API4 같은 Screen 정차 메뉴 |
| `nav-host-poc/src/main/res/drawable/ic_arrival_confirm.xml`, `ic_voice_off.xml` | 조작 아이콘 |
| `nav-host-poc/src/test/java/com/surpride/navhostpoc/NavigationFooterTest.kt` | 상태/비공개 10 tests |
| `nav-host-poc/src/test/java/com/surpride/navhostpoc/SurfaceFooterLayoutTest.kt` | 영역 7 tests |
| `nav-host-poc/src/test/java/com/surpride/navhostpoc/NavigationControlMenuTest.kt` | 메뉴/중지 3 tests |
| `docs/phase5/UI_UX_IMPLEMENTATION.md` | 본 보고서 |
| `docs/phase5/evidence/after/` | PNG69, 선별 로그, 검증 JSON |

## 11. 증거 해석 주의

- `01`–`34`는 단계별 캡처다. 최종 수정된 선택 체크·대화상자·rail은 `F28`–`F35` 등 최종 증거를 우선한다.
- `F01-oem-window-transition`은 앱 시작 직후 OEM 지도가 남은 전환 화면이다. SURPRIDE 시작 화면 검증으로 사용하지 않는다. 실제 최종 시작은 F30/F34다.
- `F12-arrival-controls`는 호스트 전환 중 회색 잔상이 있는 장면이다. 안정 메뉴는 F13/F16, 이 잔상은 별도 호스트 전환 제약이다.
- `F22-return-prepared`는 복귀 요청 처리 중 화면이다. 준비 완료·실제 활성 증거는 F23/F24와 로그를 사용한다.
- `09-system-night-command-unchanged`는 야간 명령만으로 앱 색이 변하지 않은 장면이다. Dark 증거는 앱 표시 설정 후의 F30/F31이다.
- 모든 PNG는 2560×1440 원본이다. 합성 목업·이미지 보정·사용자 위치 데이터·오디오 원본 없음. 실패/중간 장면을 없애 성공만 남기지 않았다.

## 12. 미해결 사항과 Phase 5C 조건

**Phase 5C는 조건부 진행 가능**하다. 공통 표현과 주요 사용자 흐름은 실제 실행됐지만 출시/실차 gate는 남는다.

1. **호스트:** legacy native ETA 카드 잘림, 첫 action만 보이는 제한, template 전환 잔상. 공식 호스트 버전/정책 안에서 재검증하고 임의 overlay로 덮지 않는다.
2. **접근성/해상도:** 큰 글자 실제 적용, IME, TalkBack/로터리, 좁은 창·다중 밀도, 긴 한국어 고지, 야간 지도·실물 가독성. 현재 raster 지도는 카메라 회전 시 타일 라벨도 함께 회전하는 한계가 남는다. 현재 테스트를 전 해상도 합격으로 확대하지 않는다.
3. **실행 범위:** 최종 활성 세션의 즉시 공개 후 안내 유지, 호스트 stop 콜백/포커스 상실/서버 실패/GPS 장기 상실/프로세스 재생성의 UI를 전체 실화면 행렬로 추가 검증한다. 이번 회귀 단위 테스트와 별개다.
4. **제품 사실:** STT 최종 문장, 경관 후보 확보, 차량 진입점·주차/영업 검증, 시스템 비공개 채널을 별도 해결한다. 신규 기능/승인 완료처럼 문구를 바꾸지 않는다.
5. **release:** 실제 release APK·자체 패키지 통합·장시간 안정성·정책 심사·실차 안전성은 별도 승인/검증 단계다. 지금도 정차 에뮬레이터 개발용이다.

## 13. Unslop 출처 및 적용

**Unslop — Damir Sokolovsky (@heydamir)**, [원본 SKILL 및 참고 지침](https://github.com/heydamir/unslop/tree/main/skills/unslop), [프로젝트](https://heydamir.design/unslop), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

제품에서 방향을 정하고 여백→색→아이콘→문구→타입→배치→세부 상태→비주얼→모션 순서로 검토하는 접근을 Kotlin/XML·차량용 Surface에 수정 적용했다. 승인된 기존 브랜드와 좋은 요소는 보존했다. 웹 전용 수치·이미지 히어로·장식 모션을 이식하지 않았다. 원 저작자의 보증이나 Pleos 심사 승인을 뜻하지 않는다.
