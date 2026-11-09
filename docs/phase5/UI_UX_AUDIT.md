# SURPRIDE Phase 5A — UI/UX 감사

작성: 2026-11-09. 범위: 디자인 감사 및 Phase 5B 제안. **UI 코드 변경 없음, 디자인 승인 전 구현 없음.**

## 1. 결론과 검증 범위

현재 디자인의 중심인 짙은 녹색, 밝은 라임, `S /`, 비밀 종점을 향하는 경로 곡선은 유지한다. 전면 교체보다 화면 간 조작 체계 통일, 개발 정보 분리, 한국어 정보 위계, Surface 안전 영역 개선이 효과적이다.

필수 화면 **12유형을 조사**했다. 이 중 **9유형은 이번 정차 에뮬레이터 캡처**, 활성 비밀 안내·재탐색·복귀 **3유형은 기존 실행 증거**로 시각 검토했다. 신규 PNG는 **17장**이며, 같은 화면의 단계·오류 상태가 포함된다. 17개의 독립 화면을 새로 검증했다는 뜻은 아니다. 신규 지도 캡처는 **이미 종료·공개된 세션**이다. 이번에는 활성 경로 시작, GPS 재현, 경로 API 호출, TTS 시험을 하지 않았다.

환경: Pleos Connect v2.1.0/API 34, `emulator-5554`, 사용자 10, 2560×1440, density 160. OEM 주행 뷰·상태 바·하단 GNB를 포함한 원본 캡처다. 앱에 배정된 창만 디자인 감사 대상으로 삼았다. 단일 해상도·기본 글자 크기 결과이며 작은 창, 글자 확대, 실물 디스플레이의 물리적 가독성은 미검증이다.

## 2. Git·데이터 보존

- 시작 브랜치: `main`, HEAD `5782ce8e4c48a086e20fca1ced7f398b9ea38c3b`.
- 기존 Phase 4B 수정 10개 파일과 미추적 구현·테스트·문서·증거를 그대로 보존했다. 시작 파일 468개의 SHA-256을 Git 제외 로컬 감사 자료에 기록했고, 종료 비교에서 **기존 파일 변경 0개**를 확인했다.
- 이번 산출물은 `docs/phase5/`의 문서와 스크린샷뿐이다. 커밋·Push·브랜치 변경 없음.
- 종료 브랜치/HEAD는 시작과 동일하다. 기존 dirty 상태에 `?? docs/phase5/`만 추가됐다. `git diff --check`는 종료 코드 0이며 기존 파일의 LF/CRLF 변환 예고만 있었다. 문서 로컬 링크도 존재를 확인했다.
- DEMO 조작 전 앱의 암호화된 설정/기록 파일을 앱 자체 비공개 캐시에 보관했다. 감사 후 앱을 종료하고 원본을 복원했으며 해시 일치를 확인했다. 새로운 평가 기록을 저장하지 않았다. 기존 LIVE 기록은 공개 개발 목적지의 에뮬레이터 시험 기록이다.
- 인증정보, 음성 원본, 대화 원문 파일, 사용자 좌표 덤프를 새로 저장하지 않았다. 지도 증거에 보이는 지역은 기존 공개 개발 시험 지역이다.

## 3. 화면과 실제 구현 파일

아래 경로는 프로젝트 루트 기준이다. `ExperienceActivity.kt` 등의 화면은 대부분 **Kotlin에서 생성하는 View**다. 모든 화면을 XML로 가정하면 수정 범위를 놓친다.

| 필수 화면 유형 | 시각 근거 | 실제 구현 / 관련 위치 |
|---|---|---|
| 시작 | 신규 01, 02 | `app/src/main/res/layout/activity_main.xml:23`, `app/src/main/java/com/surpride/app/MainActivity.kt:31`, `app/src/main/java/com/surpride/app/experience/ExperienceActivity.kt:168` |
| 기분·조건 선택 | 신규 03, 06, 07 | `ExperienceActivity.kt:199`–240: 기분/장소 → 시간/왕복 → 동행/우연 |
| 자연어 입력·조건 확인 | 신규 04, 05, 13, 14 | `app/src/main/java/com/surpride/app/moodinput/MoodInputActivity.kt:65`–137, 207–232 |
| 목적지 추천 준비 | 신규 08, 09 | `ExperienceActivity.kt:189`–192, 242–255 |
| 준비 힌트 | 신규 05, 09 | `ExperienceActivity.kt:249`–251, `MoodInputActivity.kt:222`의 인라인 내용. 별도 페이지 아님 |
| 지도 기반 비밀 안내 | 과거 H1; 신규 17은 종료·공개 상태만 | `nav-host-poc/src/main/java/com/surpride/navhostpoc/ActiveMysteryScreen.kt:363`–434, `map/RoadMapRenderer.kt`, `map/FollowCamera.kt` |
| 경로 재탐색 | 과거 H2, H3 | `ActiveMysteryScreen.kt`, `routing/RerouteCoordinator.kt`와 loading Trip/상태 문구 |
| 목적지 즉시 공개 | 신규 11은 DEMO; H1은 공개 전 | `ExperienceActivity.kt:254,274,285`, `ActiveMysteryScreen.kt`의 공개 액션 |
| 도착 후 공개 | 신규 15, 16은 DEMO | `ExperienceActivity.kt:277`–292; 실제 경로 공개는 `ActiveMysteryScreen.kt`와 도착/정차 확인 게이트 |
| 평가·기록 | 신규 11, 16은 평가 전; 12는 기존 기록 | `ExperienceActivity.kt:293`–325; 별도 실제 경로 결과는 `app/src/main/java/com/surpride/app/realdata/LiveNavigationResultActivity.kt:54`–88 |
| 기분 지도 | 신규 12 | `ExperienceActivity.kt:304`–325, `app/src/main/java/com/surpride/app/experience/ExperienceArt.kt:26`–35 |
| 복귀 안내 | 과거 H4 | `ActiveMysteryScreen.kt`, `returntrip/ReturnBudget.kt`, `routing/ProxyRouteRefreshClient.kt` |

`ExperienceActivity.kt`의 앞부분 공통 경로는 위 표의 전체 경로를 뜻한다. 내비게이션 관련 하위 경로는 `nav-host-poc/src/main/java/com/surpride/navhostpoc/` 기준이다.

추가 조사한 개발 화면: `app/src/main/java/com/surpride/app/realdata/RealRouteActivity.kt:171`–257. 실제 장소·경로 검증, provenance, PoC 전달을 위한 도구이며 완성된 일반 사용자 탐색 화면으로 취급하지 않는다. `MainActivity`의 Vehicle/NaviHelper 진단 열은 debug 조건에서만 보이며 XML 초기값은 `gone`이다. SDK 수신값을 제품 홈의 필수 정보로 옮기지 않는다.

## 4. 신규 캡처 목록

모두 [evidence/before](evidence/before/)에 있다. ADB 원본 캡처를 직접 열어 검토했다. 합성 목업이 아니다.

| ID / 파일 | 관찰 내용 |
|---|---|
| [01-launch.png](evidence/before/01-launch.png) | 브랜드 진입 + debug SDK 두 열. 보라색 기본 버튼, 작은 진단 본문 |
| [02-home.png](evidence/before/02-home.png) | 실제 제품 홈. 녹색/라임, 큰 정적 경로 아트, 우측 시작/입력/검증 진입 |
| [03-conditions.png](evidence/before/03-conditions.png) | 기분·장소 선택. 큰 타깃, 현재 선택 강조, 우측 요약 |
| [04-natural-input.png](evidence/before/04-natural-input.png) | 입력 전. 조밀한 2열, 같은 회색 버튼, 하단 추가 조건은 스크롤 필요 |
| [05-condition-confirm.png](evidence/before/05-condition-confirm.png) | 앱 제공 텍스트 예제를 해석. 음성/STT 성공 증거 아님. 미확정 enum과 현재 조건이 함께 보임 |
| [06-time-settings.png](evidence/before/06-time-settings.png) | 범위/시간 기준/왕복. 선택된 칩과 다음 버튼이 같은 강조색 |
| [07-companion-settings.png](evidence/before/07-companion-settings.png) | 동행/우연 정도, 차량·환경 미확인 안내 |
| [08-planning.png](evidence/before/08-planning.png) | DEMO 준비 상태와 실제 API 탐색을 하지 않는다는 설명 |
| [09-course-ready.png](evidence/before/09-course-ready.png) | 비밀 코스 준비·준비물. 목적지 식별정보 없음, 시작·공개·수정 버튼 모두 보임 |
| [10-demo-guidance.png](evidence/before/10-demo-guidance.png) | 지도 안내와 별개의 텍스트 DEMO. 큰 예시 회전, 5개 조작 |
| [11-immediate-reveal-rating.png](evidence/before/11-immediate-reveal-rating.png) | 즉시 공개한 가상 장소, 내부 추천 점수와 평가 영역 |
| [12-mood-map.png](evidence/before/12-mood-map.png) | 기분별 개념도, DEMO 기록 및 공개 시험 장소의 AVD 기록. 지리 방문 인증 아님 |
| [13-natural-reopened.png](evidence/before/13-natural-reopened.png) | 조건을 유지한 입력 재진입. 04와 같은 화면 유형 |
| [14-empty-input-error.png](evidence/before/14-empty-input-error.png) | 빈 입력 해석 오류. enum 중심 오류 문구, 기존 조건 유지 |
| [15-arrival-gate.png](evidence/before/15-arrival-gate.png) | 도착 시뮬레이션 후 공개 버튼. 실제 GPS 도착 아님 |
| [16-arrival-reveal-rating.png](evidence/before/16-arrival-reveal-rating.png) | 도착 후 가상 목적지 공개·미선택 평가 상태 |
| [17-map-stopped-revealed.png](evidence/before/17-map-stopped-revealed.png) | 기존 종료 세션의 실제 OSM 지도. 위치 신뢰도 낮음, 방향 없음, 이미 공개된 목적지. 활성 안내 검증 아님 |

### 과거 실행 증거 — 이번에 재실행하지 않음

| ID | 원본 | 이번 시각 검토 |
|---|---|---|
| H1 | [Phase 4B 활성 지도](../phase4/evidence/drive-experience/03-active-map.png) | 도로 지도·차량·전방 경로선·호스트 회전 카드 동시 표시. 하단 4줄 기술 정보가 큼 |
| H2 | [재탐색 후 실제 경로](../phase3/evidence/reroute-voice-return/02-reroute-real-kakao.png) | 재탐색 이후의 활성 안내 결과. 재탐색 중 장면으로 혼동하지 않음 |
| H3 | [재탐색 중 서버 실패/대기](../phase3/evidence/reroute-voice-return/03-server-unavailable-loading.png) | 호스트 loading, `REROUTING/WAITING_RETRY`, GPS 신뢰도 저하. 제품용 한국어 상태 필요 |
| H4 | [최종 복귀 활성 화면](../phase3/evidence/reroute-voice-return/18-final-return-active.png) | 우회전/거리, 복귀 경로선, RETURNING/귀가 예산 정보 |

## 5. 중요도별 발견 사항

P0는 실제 주행 제품 전환 전 필수, P1은 핵심 사용성, P2는 완성도, P3는 후속 선택 사항이다. 이번 디자인 감사에서 새로운 안전 사고나 시스템 비공개 성공을 판정한 것은 아니다.

| ID / 중요도 | 문제와 근거 | Phase 5B 권고 |
|---|---|---|
| U01 / P0 출시 조건 | H1/H3/H4의 4줄 footer에 GPS/Trip 카운터, 백엔드/enum, 귀가 예산이 섞임. `ActiveMysteryScreen.kt:416`–434. 정차 개발 PoC라는 현재 표시 자체는 적절함 | 회전·도로·시간/거리·우선 상태만 주행 표면에 남기고 카운터/백엔드는 정차 진단으로 이동. 안전 상태와 개발용 표시를 삭제해 LIVE 완성품으로 보이게 하지 않음 |
| U02 / P1 | 01 보라색 Material, 02 라임 커스텀, 04/05 회색 native 버튼이 세 가지 체계. `res/values/themes.xml:3`, `values-night/themes.xml:3`, `ExperienceActivity.kt:28,340`, `MoodInputActivity.kt:227` | 공통 토큰과 버튼 역할 통일. 보라색은 단색 기본 테마 흔적이며 그라데이션 문제가 아님 |
| U03 / P1 | 04/05 입력·해석·예제·수정·취소가 동일 무게. 확인/적용은 긴 우측 스크롤 아래에 있어 첫 화면에서 드러나지 않음. `MoodInputActivity.kt:65`–137 | 입력 → 해석 결과 → 명시적 적용을 3개 영역으로 구성. 필수 변경점과 미확정 질문을 먼저, 상세 보정은 접기/정차 스크롤. 적용 전 기존 조건 보존 |
| U04 / P1 | 03/06/07에서 선택 강조, 현재 메뉴, 다음 행동 모두 라임 채움. `ExperienceActivity.kt:340`–356. 커스텀 TextView 버튼에 selected/checked 상태와 별도 pressed/focus drawable 없음 | 선택에는 테두리·체크·접근성 상태, 진행 CTA에는 채움. 터치/키보드 포커스/pressed와 비활성 이유 표시 |
| U05 / P1 | 17의 긴 도착 이유는 말줄임되고, H1 footer가 지도를 압박. `ActiveMysteryScreen.kt:405`–434는 최소 footer 130px, 글자 최대32px로 고정 계산 | safe bounds와 fontMetrics로 필요한 높이를 측정. 4줄을 억지로 유지하지 않음. 좌우 padding을 뺀 폭으로 말줄임·clipRect. 회전 카드/OSM 출처를 가리지 않음 |
| U06 / P1 검증 과제 | 홈/READY는 비스크롤 고정 2열, 170/220dp rail. 현재 02/09에서는 주요 행동 잘림 없음. 작은 창/글자 확대 때는 소스상 위험. `ExperienceActivity.kt:109,168,242` | 앱 창의 실제 폭·높이로 적응형 구성, 설명 스크롤과 행동 영역 분리. 현재 화면에 없는 잘림을 관찰했다고 주장하지 않음 |
| U07 / P2 | 04/05/14 `RULE_BASED/ON_DEVICE/UNKNOWN/AUTO`, 02/09/12 영문 소제목이 제품 한국어와 혼재. 같은 기능 진실을 여러 줄 설명 | 사용자용 “기기 안에서 조건을 정리해요”, “아직 정하지 않았어요”. 상세 enum/SDK는 진단에 유지. 음성 인식 완료를 보장하는 문구 금지 |
| U08 / P2 | 11/16의 내부 추천 점수 50/기분20/장소10/동행15는 발견 순간과 경쟁. `ExperienceActivity.kt:291` | 목적지·선정 이유·평가 순. 내부 점수는 진단. “안전한 주차/입장 가능”은 UNKNOWN이면 확인 필요 |
| U09 / P2 | 12 지리 지도처럼 보이는 격자에 6개 고정 감정 점이 있고, AVD/Demo 기록 목록이 반복. raw px 도형/20px 문구. `ExperienceArt.kt:14`–35 | “기분별 기록” 개념도를 명료화하고 출처 그룹 구분 유지. dp/sp/접근성 요약 적용. 실제 지리 방문 지도처럼 오인시키지 않음 |
| U10 / P2 | 13/22dp radius, 3/5/9/14/18dp 여백, 68/70/76dp 컨트롤이 혼재. `ExperienceActivity.kt:334`–357, `MoodInputActivity.kt:226`–228, `LiveNavigationResultActivity.kt:95` | 4dp 기반 역할 토큰. 큰 터치 영역을 유지하며 의미 없는 차이만 정리 |
| U11 / P2 | Map strip의 눈/스피커/복귀는 지도 조작이 아님. 상태가 바뀌어도 스피커 형상이 동일. `ActiveMysteryScreen.kt:385`–390 | 공식 template action/정차 조작으로 이동 가능성 확인. 종료 행동을 항상 접근 가능하게 유지. API 4 Surface에 임의 터치 버튼을 그려 해결하지 않음 |
| U12 / P3 | 기본 Android launcher 그림, Unicode 예시 회전, 서로 다른 아이콘 어휘. `res/drawable/ic_launcher_foreground.xml`, `ExperienceActivity.kt:262` | 기존 vector 기반 한 스타일, 런처는 S/경로 모티프 검토. 유료 폰트·사진·새 아이콘 SDK 도입 불필요 |

### Unslop 체크에서 유지 판정한 항목

- 직접 작성한 의미 없는 색상 그라데이션, glow, 과도한 그림자, 반복 장식 애니메이션은 조사 화면/소스에서 확인하지 못했다. `GradientDrawable`은 단색/테두리 용도다.
- 녹색/라임과 넓은 여백은 제품의 차분한 기대감에 맞는다. 전면 흰색 대시보드나 사진 히어로로 교체하지 않는다.
- 경로 곡선은 제품과 직접 연결된 정적 아트다. 불필요한 장식으로 일괄 삭제하지 않는다. 운행 지도에는 이 모티프를 추가하지 않는다.
- 현재 본문 `#EBF1E1`/배경 `#0A1818`는 15.74:1, 보조 `#9AB1A5`/패널 `#152827`는 6.75:1이다. 불투명 소스 색상의 sRGB 계산이며 사진/지도/disabled alpha/호스트 UI 전체의 대비 인증은 아니다. 막연한 “전체 저대비” 판정은 부적절하다.
- 76dp 주요 행동, 명시적 공개, 평가 전 저장 비활성화, 정차 UX 제한, DEMO/AVD provenance를 보존한다.

## 6. 핵심 6화면 개선 카드

| 화면 | 현재 문제 → 개선 목표 | 구체 변경 제안 | 변경 금지 / 유지 | 우선순위·난이도 / 파일 |
|---|---|---|---|---|
| A 시작 | 01 개발 런처와 02 제품 홈의 진입·스타일 차이 → 한 번에 시작점 이해 | 제품 시작 CTA 1개, 말로 조건 정리는 보조, 경로 검증/SDK는 정차 개발 진입. 현재 2열 아트의 비율을 창에 맞춤 | SURPRIDE/슬로건/S /, 선택형 대체 입력, debug 진단 기능 | P1·중 / MainActivity, activity_main.xml, ExperienceActivity:168 |
| B 조건 | 칩·CTA 동색, 04 긴 폼 → 선택/수정/적용 구별 | 단계와 요약 유지, 체크·선택 semantic, 수정 차이 먼저, 시간/왕복 먼저 읽히게 정렬 | 6기분/하드 제약/미확정 값/명시적 적용/정차 제한 | P1·중 / ExperienceActivity:199, MoodInputActivity |
| C 비밀 코스 준비 | 09 준비물·총시간·긴 공백, 상징 아트와 CTA의 연결 약함 → 출발 판단에 필요한 정보 집중 | 총 이용시간과 포함 항목, 준비물 2–3개, 출발 CTA·공개 보조·조건 수정. DEMO는 “체험 시작” 역할 구별 | 이름/주소/좌표 숨김, 실제 ETA 미계산 경고, 즉시 공개 | P1·중 / ExperienceActivity:242, RealRouteActivity:171 |
| D 지도 안내 | H1 기술 footer/17 긴 이유 → 지도와 회전 우선 | 도로/경로/차량 유지, 시간·거리 1줄+우선 상태, 측정 기반 safe footer. 앱 상태 문구/토큰 통일 | 호스트 회전 카드/안전 도로명/OSM attribution/포커스/공개 게이트 | P0/P1·상 / ActiveMysteryScreen, RoadMapRenderer, FollowCamera |
| E 목적지 공개 | 16 이름 다음 바로 내부 점수·평가 → 발견과 이유를 먼저 | 목적지 제목, 이유 1문장, 확인된 장소 정보, 평가·기록. 즉시 공개/도착 공개 문구 구별, 복귀는 명시적 선택 | 정차/도착 확인, 미검증 주차·영업정보 표시, 개인정보·기록 모델 | P2·중 / ExperienceActivity:277–302, LiveNavigationResultActivity |
| F 기분 지도 | 12 개념도와 실제 지리 의미 혼동 가능/기록 반복 → 감정 기록 도구 | 6기분의 수/평가, 최신 기록과 출처 구분, 정차 상세보기, 빈 상태 CTA. 개념도임을 유지 | DEMO와 AVD 기록 분리, 실제 방문 인증 주장 금지, 기존 저장 파일 | P2·중 / ExperienceActivity:304, ExperienceArt |

## 7. NavigationTemplate·Surface·OEM 경계

| 소유자 | 변경 가능한 것 | Phase 5B에서 약속하면 안 되는 것 |
|---|---|---|
| 앱 View | 제품 홈/입력/준비/공개/평가의 레이아웃·타입·색·상태 | 주행 중 정차용 긴 폼 허용 |
| 앱 Surface | 지도 픽셀, 전방 경로, 차량 마커, 카메라, 앱 footer/라벨, 출처 표시 | 지도 타일의 도로·안전 정보를 목적지 은폐 목적으로 삭제 |
| AndroidX/Pleos 호스트 | RoutingInfo/Step/Maneuver/지원 action과 색 요청을 전달 | 호스트 카드의 좌표·글꼴·반경·버튼 치수·strip 노출을 앱 토큰대로 강제 |
| OEM 시스템 | 공개된 앱 창/UX 제한 준수 | 좌측 차량 뷰·상태 바·GNB를 재배치하거나 덮어쓰기 |

실제 dependency는 Car App Library 1.7.0, 기존 협상 호스트는 Car App API 4다. `SurfaceCallback.onClick`은 API 5이므로 Surface 위에 직접 탭 가능한 새 pill을 약속하지 않는다. map action strip은 지도 상호작용용으로 권장되며 일반 template action과 역할을 나눠 검증해야 한다. [Surface 계약](https://developer.android.com/reference/androidx/car/app/SurfaceCallback), [템플릿 UX](https://developers.google.com/cars/design/create-apps/apps-for-drivers/templates/navigation-template), [커스터마이징 경계](https://developers.google.com/cars/design/create-apps/apps-for-drivers/customize-app).

과거 [Phase 1 호스트 ETA 이미지](../phase1/independent-nav-evidence/navigation-template.png)에는 하단 카드 잘림 증상이 있다. 현재 `ActiveMysteryScreen.kt:391`은 host destination estimate를 생략하고 424에서 앱의 비례 추정 ETA를 그린다. **현재 앱 footer와 과거 호스트 ETA 카드는 다른 요소다.** 과거 이미지로 OEM GNB가 원인이라고 확정하거나 호스트 버그가 해결됐다고 주장하지 않는다. 현재 앱 footer의 bounds/측정은 개선할 수 있으나 native 카드는 호스트 재현·보고 대상으로 남긴다.

## 8. 문구와 실제 상태

| 실제 상태 | 제품 문구 제안 | 금지할 과장 |
|---|---|---|
| AI_PROVIDER RULE_BASED | “문장으로 조건 정리 · 기기 내 처리” | “AI가 마음을 정확히 이해했어요” |
| STT PARTIAL | “음성 입력 시도 / 인식이 안 되면 직접 입력” | READY 수신만으로 “음성 인식 완료” |
| 경관 경유 CLOSED, 실제 후보 0 | “경관 정보가 부족해 일반 도로 경로로 준비했어요” | “검증된 아름다운 강변 코스” |
| 시스템 비공개 PARTIAL | 제품은 “목적지 비공개”; 정차 안내/도움말에 시스템 채널 한계 고지 | “어떤 화면·음성에서도 절대 알 수 없어요” |
| 정차 AVD, 실차 미검증 | 개발 빌드 “정차 에뮬레이터 체험”; DEMO는 “가상 장소·모의 주행” | 실차 운행 안전성/시간 내 귀가 보장 |
| 기존 API 비례 ETA | “약 N분 · N km / 경로 기록 기준 추정” | 지속 갱신되는 실시간 교통 ETA |

## 9. 공식 정책과 남은 검증

[Pleos 기본 원칙](https://document.pleos.ai/en/docs/connect/guide/docs-design/Introduction/intro)은 짧은 시선 확인, 간단한 조작, 높은 대비와 읽기 쉬운 정보를 요구한다. 본 감사는 이를 제품 제안으로 적용했으며 심사 승인이나 실차 적합성을 인증하지 않는다. [앱/시스템 레이아웃](https://document.pleos.ai/en/docs/connect/guide/docs-design/getting-started/layout), [운전자 주의 분산](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline), [내비게이션 개발 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide)도 별도 경계로 적용한다.

이번 미검증: 활성 경로 신규 실행, 시스템 전체 비공개, 주행 UX 동작 재시험, STT 최종 결과, 클러스터/HUD, 실차, 작은 창/다른 density, 큰 글자, 주야간 지도 전체, TalkBack 포커스 탐색. 증거가 없는 채널을 PASS로 바꾸지 않는다. `STT=PARTIAL`, `DESTINATION_PRIVACY=PARTIAL`, `ROUTE_QUALITY_EVIDENCE=PARTIAL`을 유지한다.

기존 Phase 4B 결과 파일은 app 187 + PoC 175 + FastAPI 90 = **452개 통과**다. 이번은 UI 코드가 없는 문서 감사로 빌드/452개 테스트를 재실행하지 않았다. 452는 이전 검증 기준이며 새 실행 결과가 아니다.

## 출처·적용 범위

감사 방법은 **Unslop, Damir Sokolovsky (@heydamir)**의 [SKILL.md 및 references](https://github.com/heydamir/unslop/tree/main/skills/unslop)를 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)에 따라 출처 표시하고 Android Automotive 문맥으로 수정 적용했다. [원 저작자 프로젝트](https://heydamir.design/unslop). 웹용 px/행 밀도/히어로/모션 규칙을 Kotlin View·Surface·호스트에 그대로 적용하지 않았다. 저작자의 SURPRIDE 보증·승인을 의미하지 않는다.
