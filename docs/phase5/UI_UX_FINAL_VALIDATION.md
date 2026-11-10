# SURPRIDE Phase 5C — 차량용 UI/UX 최종 검증

2026-11-10 · **READY_WITH_LIMITATIONS** · Phase 6 제한 조건부 진입 권고

- **VISUAL_ACCEPTANCE = PENDING_USER_REVIEW**
- **DESTINATION_PRIVACY = PARTIAL**
- AI_PROVIDER = RULE_BASED / STT = PARTIAL 유지

사용자의 정정 지시에 따라 중단된 작업을 재개했다. 사용자 실행 중인 **emulator-5556 한 대**에 두 APK를 설치하고 실제 화면을 표시했다. 이번 재개에서 새 AVD 생성·초기화·다른 기기 설치·실차 주행은 하지 않았다. 이전 [중단 기록](evidence/final/INTERRUPTED_VALIDATION.md)과 PNG9장을 보존했다.

근거: [기계 판독 요약](evidence/final/validation-summary.json), [선별 내비게이션 이벤트](evidence/final/navigation-events.txt). 원시 API 응답·키·토큰·개인 위치·기록 파일을 증거에 넣지 않았다. 화면의 장소는 기존 공개 개발 시험 데이터다.

## 1. Git 시작·종료 및 보존

| 항목 | 결과 |
|---|---|
| 시작·종료 branch / HEAD | `main` / `43c659442ea7894f2ff3835eb4c1f3843d5b204f` 유지 |
| 재개 시작 | 공통 버튼 수정1줄, 계측8개, 계획·중단 보고서·PNG9장이 미커밋 상태. clean으로 간주하지 않음 |
| 종료 | 제품4파일·기존 PoC 테스트1파일 수정, 계측 파일·계획·이 보고서·final 증거 디렉터리 미추적 |
| Git 조작 | 커밋·Push·stage·reset·clean·브랜치 변경 없음 |
| 데이터 | 원래 DEMO/실제 경로 평가 기록·테마를 private cache에 보존. 검증 후 원본 바이트 해시 일치, 재실행 후 재확인 |
| 환경 | size2560×1440 / density160 / font_scale1.0 / 위치 활성화 / 원래 GPS 좌표·테마·경로 세션 파일 복원 |
| 종료 | outbound·return 정차 확인에서 STOPPED. GPS 재생·프록시 종료. 에뮬레이터는 유지하고 메인 시작 화면 표시 |

평가 저장 검사에서 추가한 4점 시험 기록은 증거 확보 후 원래 파일을 복원했다. 기존 기록을 지우거나 시험 기록을 남긴 상태로 종료하지 않았다.

## 2. 검증 화면·환경

Pleos Connect v2.1.0 / Android API34 / `Connect on x86_64 emulator` / 사용자10. Host `carApi=4` 연결 확인. 전체 화면2560×1440/density160, 기본 앱 창 관측 x853–2535 / y90–1300. OEM 계기·상단 바·하단 시스템 영역을 제외한다. 모든 조작은 **P / 0km/h 정차 AVD**에서 했다.

실제 확인: 시작, 홈, 기분·시간·왕복 조건, 자연어 입력/해석/오류/수정, 실제 API 추천·준비, 지도 안내·공개·재탐색·중단, 도착 확인, 평가·기록, 기분 지도·상세, 복귀 준비·활성·완료. **메인 앱의 준비·평가와 독립 nav-host-poc의 NavigationTemplate 실행을 구분**한다.

프록시는 `backend/README.md`의 `backend.dev serve --mode live` / `stop` 절차로 재시작·종료했다. provision의 private run-as 전달 절차는 사용자 기기5556에만 적용했다. 실제 Kakao 경로와 기존 공개 개발 카탈로그를 사용했다. 실패를 MOCK 성공으로 바꾸지 않았다.

## 3. 해상도·밀도·큰 글자

| 실행/검사 | 결과 | 검증 범위 |
|---|---|---|
| 2560×1440 / density160 / font1.0 | 계측10/10, 주요 사용자 흐름 실제 실행 | 기본 기준 |
| 같은 물리 해상도 / density200 / font1.3 | 계측10/10, Activity width1346dp / height896 또는950dp | 좁아진 조건·IME·CTA 실제 확인 |
| 같은 물리 해상도 / density160 / font2.0 | 계측10/10, Activity width1682dp / height1120 또는1210dp | 2배 조건·입력·CTA 실제 확인 |
| 같은 물리 해상도 / density120 / font1.0 | Light/Dark 시작 화면 실제 표시 | 넓은 영역 표본, 전체 흐름 합격은 아님 |
| 공식 wm size1920×1080 시도 | Host가 이전2560 창 좌표를 유지해 우측 잘림 | 두 번째 지원 물리 해상도로 인정하지 않음. 원상 복원 |
| 260dp 버튼 폭 / font1.0·1.3·2.0 | 여러 줄·말줄임 없음·터치 높이 유지 | 실제 View 계측, 260dp 차량 전체 화면 실행은 아님 |

설정 요청값만으로 판단하지 않고 실제 Activity 설정 로그를 보존했다. [1.3배 조건](evidence/final/F18-density200-font130-conditions.png)·[IME](evidence/final/F19-density200-font130-ime.png), [2배 조건](evidence/final/F20-font200-conditions.png)·[IME](evidence/final/F21-font200-ime.png)를 비교한다. 본문 일부는 스크롤 영역 밖으로 이어지지만 입력창과 하단 계속/닫기 버튼은 키보드와 함께 보인다. 모든 화면·폰트 조합의 완전 검증은 아니다.

Light/Dark 시작·자연어 오류/수정·평가/기록을 실제 확인했다. 앱 토큰의 sRGB 대비 계산: 본문/카드 Light16.15:1·Dark13.34:1, 보조문구7.11:1·6.75:1, 오류6.92:1·8.18:1, 주 CTA13.04:1. 이는 색쌍 계산이며 지도·Host·전체 UI 접근성 인증이 아니다. 지도 야간 테마는 미검증이다.

## 4. IME·한국어 입력

**긴 한국어 버튼 해결:** 공통 스타일에 `setSingleLine(false)`를 추가했다. 이전 실패를 보존하고 수정 APK에서 260dp 및 1/1.3/2배 줄바꿈 검사를 통과했다.

**CarIME 가림 해결:** 입력 `imeOptions`에 공식 `EditorInfo.IME_FLAG_NO_FULLSCREEN`만 추가했다. 실제 CarIME가 전체 화면 추출 대신 앱 입력·조건·하단 CTA를 함께 표시했다. [수정 전](evidence/final/07-ime-open-baseline.png) / [수정 후](evidence/final/F11-ime-no-fullscreen.png). Android 공식 [NO_FULLSCREEN 계약](https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_FULLSCREEN)은 IME가 무시할 수 있으므로 다른 OEM 키보드까지 해결했다고 주장하지 않는다. 시스템 IME 변경·권한 우회 없음.

실제 한국어 자판으로 자모 조합·줄바꿈을 입력하고, Back으로 키보드를 숨긴 뒤 내용 유지·화면 복원을 확인했다([조합](evidence/final/F12-ime-korean-composition.png), [Back 후](evidence/final/F13-ime-back-preserved.png)). 빈 문장 오류→수정→규칙 해석 결과도 실제 확인했다. 긴 여러 줄 한국어의 Activity 재생성, 빈 입력 복원, 1000자 제한, 미검토 적용 차단, 입력 이름과 EditorInfo 전달은 계측으로 확인했다. 1000자를 직접 자판 입력하거나 입력 중 프로세스를 종료한 검증은 아니다.

개인정보 설명의 끝 음절만 남는 줄바꿈은 짧은 두 문장으로 조정했다. 규칙 기반 해석·입력 취소/적용 계약을 보존했고 STT/AI 기능을 확장하지 않았다.

## 5. 접근성·D-pad·TalkBack

공통 버튼은 한국어 텍스트 이름, 체크·외곽선·isSelected/stateDescription으로 선택 상태, enabled로 비활성을 제공한다. 제목 heading, 입력 이름, 자연어 상태 polite live region, 기분 개념도의 텍스트 설명, 평가 장식의 접근성 제외를 소스/노드에서 확인했다. 공통 목표는 최소76dp 높이·폭이다.

기분·시간 선택, 시작/취소, 즉시 공개, 안내 종료, 정차·도착 확인, 평가 저장, 복귀 준비/시작은 이름 있는 Button 또는 공식 Host Row/Action이다. 선택·오류를 색상만으로 표현하지 않는다. Host 원격 UI는 UIAutomator 트리가 빈 경우가 있어 실제 화면과 템플릿 계약을 함께 확인했다.

**선택 후 포커스 소실 해결:** 같은 패널·단계 내 View 재생성에서 버튼 생성 순서 태그로 입력 포커스를 복원한다. 화면 전환에 이전 버튼을 강제 포커스하지 않는다. 실제 D-pad 이벤트 계측으로 기분 선택, 이름이 바뀌는 왕복 토글, 동일 이름이 반복되는 기록 버튼을 확인했다. 이름 기반 복원 대안은 리뷰 지적 후 제거하고 변경/중복 이름 회귀 검사를 추가했다.

활성 서비스는 CarSafetyAccessibilityService/RotaryService이며 TalkBack은 설치/활성 목록에서 확인되지 않았다. **실제 TalkBack 음성·읽기 순서·접근성 포커스·동적 상태 읽기는 UNVERIFIED**다. D-pad 포커스 통과가 TalkBack 합격은 아니다. Canvas 지도/footer의 의미 전달, 전체 로터리 순회·화면 전환 포커스는 후속 Gate다.

## 6. 지도·NavigationTemplate·Host

[비밀 안내](evidence/final/F42-active-secret-map.png)에서 실제 도로지도·OSM 출처·위치 marker·Follow Camera·전방 경로·회전/거리·도로명·남은 시간/거리 추정을 함께 확인했다. GPS 상실 시 이전 회전을 보류/loading으로 바꾸고 약10초 지속 시 종료했다. 이탈 후 loading Trip·새 Kakao 경로 교체, 서버 미연결 재시도 후 안전 종료를 화면·로그로 확인했다.

Native ETA 하단 잘림은 기존 정적 MOCK PoC에서 [재현](evidence/final/F33-host-static-restarted.png)됐다. Host 내부 레이아웃은 변경하지 않았다. 활성 SURPRIDE는 목적지 없는 Trip와 자체 **추정** 시간/거리 footer를 사용한다. Native 목적지 ETA와 구분한다. API4 Host가 첫 action만 보여주는 경우 기존 공식 메뉴로 종료·공개·복귀·도착에 접근했다.

전환 직후 Surface만 보이고 Host 컨트롤이 지연/미표시되는 현상·전환 잔상이 남았다. 안내 중단 상태에서 자체 PoC만 정상 재시작하고 공식 시험 목록으로 복구했다. Host/OEM 프로세스 조작 없음. 지도 타일은 실제 로드했지만 타일 서버 장애·만료 타일의 실화면은 미검증, 기존 TileStore 실패/캐시 회귀 검사만 통과했다. 주행 제한은 기존 fail-closed 계약/검사를 보존했다.

공개 전 ACTIVE Trip 목적지 목록0·앱 목적지명/최종 핀 미노출, 명시적 공개 후 목록1·ACTIVE 유지, 도착 후보 비공개→정차 확인 후 공개를 확인했다. 일반 지도 지명으로 추측 가능성, 홈·알림·클러스터/HUD·최근 목적지 전체 채널, TTS 실청취는 미검증이다. **DESTINATION_PRIVACY=PARTIAL 유지.**

## 7. 상태 전이·예외 A–O

[정적/단위 계약 감사](evidence/final/STATIC_SCENARIO_COVERAGE.md)는 소스 대응 자료다. 그 문서의 ‘남은 실화면’은 감사 당시 범위이며 아래가 최종 실행 결과다.

| 시나리오 | 이번 결과·한계 |
|---|---|
| A 추천→안내→공개→평가 | 실제 API/ACTIVE 비밀 지도/정차 확인 공개/4점 저장. 정확히1개 증가, 중복 저장 컨트롤 제거·기존 기록 유지 |
| B 조건 변경→재추천 | 편도46분8.1km→왕복69분18.6km 재조회. 조회 시점 결과이며 품질 인증 아님 |
| C 입력 오류→수정 | 빈 입력 오류·수정 후 규칙 결과 실제 확인, 긴 입력/재생성/미검토 차단은 계측 |
| D 경로 실패→복구 | 메인 미연결 오류→프록시 재시작·세션 전달→실제 준비 복구. 모든 오류 코드 실주입은 아님 |
| E GPS 상실 | 공식 위치 OFF→회전 보류→STOPPED. ON 복원 후 명시적 시작 |
| F 재탐색 성공 | 공개 개발 위치 이탈→loading Trip→API 경로 교체·ACTIVE 유지 |
| G 재탐색 실패 | NETWORK/WAITING_RETRY→두 번째 실패 후 안전 종료. 성공으로 표시하지 않음 |
| H 즉시 공개 | ACTIVE 공개 후 목적지 목록1·안내 유지 |
| I 후보→정차 확인→공개 | 후보 revealed=false, 확인 후 ARRIVED/STOPPED·공개 |
| J 공개 후 복귀 | 준비 autoStart=false→명시적 ACTIVE. 겹친 구간 재생에서 안전 종료 후 현재 위치 경로 재요청→재시작→도착 확인·복귀 완료 |
| K 사용자 취소 | 공식 활성 메뉴 종료→STOPPED 실제 확인 |
| L Host 종료 요청 | 공식 onStopNavigation·소유권 정리 단위 계약 통과. 실제 Host 콜백 조작 미실행 |
| M 재시작·재구성 | 입력 Activity 재생성 계측, PoC 준비 상태 자체 프로세스 재시작·재진입에서 자동 안내 없음. 활성 OS 종료 전체 복구 미검증 |
| N 서버 미연결 | 메인 준비·재탐색 실제 오류/복구. 복귀 준비의 서버 단절 조합 미실행 |
| O 위치 권한 없음 | 표준 fine 철회→정확 위치 부족 시작 차단→공식 권한 대화상자로 복원. 우회 없음 |

기존 공개 개발 세션 검증을 거쳐 공식 `adb emu geo fix`로 위치를 재생했다. 안내 중단 상태에서 위치를 옮기고 접근 구간을 재생했으며 전체 도로 주행은 아니다. 첫 복귀는 겹친 도로의 heading 없는 재생에서 REVERSED/AMBIGUOUS로 안전 종료됐다. matcher·임계값을 완화하지 않았다. **원래 복귀 경로의 연속 완주를 주장하지 않는다.** 기존 현재 위치 경로 재요청으로 복구한 뒤 마지막 약200m·정차 확인까지 통과했다.

## 8. 수정 파일·해결 결함

| 파일 | 변경·검증 |
|---|---|
| `app/.../ui/SurprideUi.kt` | 공통 버튼 여러 줄. 260dp/3개 fontScale GREEN |
| `app/.../moodinput/MoodInputActivity.kt` | 공식 IME flag·설명 두 줄. 실제 CarIME/계측 GREEN |
| `app/.../experience/ExperienceActivity.kt` | 같은 화면 선택 후 D-pad 포커스 복원. 토글·중복 이름 GREEN |
| `nav-host-poc/.../NavigationFooter.kt` | 복귀 완료에서 없는 평가/재복귀 유도 대신 종료 문구. 단위/실화면 GREEN |
| `nav-host-poc/src/test/.../NavigationFooterTest.kt` | 완료 상태 회귀1개 추가 |
| `app/src/androidTest/.../AutomotiveUiValidationTest.kt` | WIP8개 보존·완성, 중복 이름 검사 추가로9개 |

새 브랜드·프레임워크·의존성·애니메이션·엔진 재작성 없음. 녹색/라임·S /·경로 곡선, 지도·GPS·회전·재탐색·음성·저장과 기존 검사를 보존했다.

## 9. 최종 자동 검사·APK

| 검사 | 결과 |
|---|---|
| 메인 JVM | **196 PASS**, 실패/오류/skip0 |
| PoC JVM | **196 PASS** = 기존195+완료1, 실패/오류/skip0 |
| FastAPI | **90 PASS**, 기존 Starlette deprecation 경고1 |
| JVM/backend 합계 | **482 PASS**, 기존481개 모두 포함 |
| Android 계측 | 새9+기존 ExampleInstrumentedTest1 = **10 PASS** |
| 환경 반복 | density200/font1.3, density160/font2.0, density160/font1.0 각각10/10 |
| 고유 합계 | **492 PASS**, 환경별 반복30회를 고유30개로 더하지 않음 |
| 빌드 | 메인 assembleDebug/assembleDebugAndroidTest/testDebugUnitTest 및 PoC assembleDebug/testDebugUnitTest 성공 |
| 설치·실행 | 메인 `com.surpride.app` / 독립 `com.surpride.navhostpoc` 모두5556/사용자10 성공 |
| 설치본 확인 | 두 설치 APK SHA256이 최종 검증 APK와 각각 일치 |

초기 RED를 삭제하지 않았다. 줄바꿈, IME flag 부재, 포커스 소실·변경/중복 이름, 완료 문구 실패를 재현 후 GREEN 확인했다. 계측 초기화는 정차 상태까지 기다리고 실제 D-pad 이벤트로 touch mode를 벗어나도록 보완했다.

검증 APK는 Git 제외 `.gradle/phase5c/resume/final-app.apk`, `final-poc.apk`, `final-test.apk`에 보존했다. 메인 SHA256 `54041f8986e5166ffc00cfa957f48b85dfd5ba2f09e99c2105938badd4415d64`, PoC `60d0a5dc9d3094da4c3ba590565c9518236dfb12a0acb27963b82ad16fb7d47c`. 빌드/pytest/계측 원본 로그도 같은 제외 디렉터리에 있다.

## 10. 실제 실행 안정성·정리

재개 구간 `dumpsys activity exit-info`에서 메인·PoC·`com.android.car.templates.host`의 crash/native crash/ANR은 각각0건이었다. 설치·계측·권한 철회·정상 재시작에 따른 기타 종료는 존재한다. Crash buffer에도 대상 패키지 Java fatal process는 확인되지 않았다. 시스템 native fatal 기록136줄은 별도로 존재해 OS 전체 무오류로 판단하지 않는다. 이력/버퍼 보존 범위의 관측이며 장시간 인증은 아니다.

Host 지연·ETA 잘림은 crash가 없어도 남은 UI 제한이다. UIAutomator 세션은 중복 실행하지 않도록 했다. TTS는 기존 계약/회귀를 보존했고 음향 청취 검증은 하지 않았다. 안내 STOPPED 뒤 재생·README 프록시를 종료하고 원본 기록·테마·세션·화면/글자/위치를 복원했다. 에뮬레이터는 종료하지 않았다.

## 11. 최종 화면·Before/5B 비교

final의 **실제 PNG86장**은 이전9장을 포함한 ADB 원본이며 보정/합성하지 않았다. 번호가 합격 순서는 아니다. 조건·조회 시점·장소 차이가 있어 픽셀 개선률을 계산하지 않는다.

| 화면 | Before / Phase 5B | Final·판단 근거 |
|---|---|---|
| 시작 | [Before](evidence/before/01-launch.png) / [5B](evidence/after/F30-launch-dark-final.png) | [Dark](evidence/final/F52-dark-start-default.png)·[복원 후](evidence/final/F81-restored-start.png): 브랜드·준비 CTA 유지 |
| 기분 선택 | [Before](evidence/before/03-conditions.png) / [5B](evidence/after/F28-mood-selection-dark.png) | [Final](evidence/final/F16-conditions-final.png)·[확대](evidence/final/F20-font200-conditions.png): 선택·줄바꿈·76dp |
| 자연어 | [Before](evidence/before/04-natural-input.png) / [5B](evidence/after/F04-partial-edit-confirmed.png) | [IME](evidence/final/F11-ime-no-fullscreen.png)·[오류](evidence/final/F24-natural-empty-error-dark.png)·[수정](evidence/final/F25-natural-corrected-dark.png): 입력/CTA 동시 접근 |
| 준비 | [Before](evidence/before/09-course-ready.png) / [5B](evidence/after/F29-real-course-ready-final.png) | [왕복](evidence/final/F28-real-roundtrip-ready.png)·[복구](evidence/final/F55-network-recovered-ready.png): 분·거리·실패 상태 명시 |
| 실제 지도 | [이전](../phase4/evidence/drive-experience/03-active-map.png) / [5B](evidence/after/F08-active-secret-map.png) | [Final](evidence/final/F42-active-secret-map.png): 지도·marker·회전·거리·도로명 |
| 재탐색 | [이전](../phase3/evidence/reroute-voice-return/03-server-unavailable-loading.png) / [5B](evidence/after/F10-reroute-result.png) | [성공](evidence/final/F48-reroute-success.png)·[실패 종료](evidence/final/F51-reroute-network-failure-stopped.png) |
| 공개·평가 | [Before](evidence/before/16-arrival-reveal-rating.png) / [5B](evidence/after/F17-arrived-revealed.png) | [공개](evidence/final/F62-arrived-destination-revealed.png)·[저장 전](evidence/final/F64-live-rating-unselected.png)·[저장 후](evidence/final/F65-rating-saved.png) |
| 기분 지도 | [Before](evidence/before/12-mood-map.png) / [5B](evidence/after/F32-mood-records-final.png) | [Final](evidence/final/F79-mood-records.png)·[상세](evidence/final/F80-record-detail.png): 개념도/실제 평가 구분 |
| 복귀 완료 | [이전 활성](../phase3/evidence/reroute-voice-return/18-final-return-active.png) / [5B](evidence/after/F27-return-completed.png) | [활성](evidence/final/F73-return-active-verified.png)·[복구 후](evidence/final/F76-return-recovery-active.png)·[완료](evidence/final/F78-return-completed.png): 명시적 정차 확인·종료 문구 |

오해 방지: F17은 전환 중, 좁은 영역 증거는F18/19이고 F17b는 Host 잘림이다. F29/31/32/36/38/53/56은 전환/중간 관측이며 이름만으로 합격에 쓰지 않는다. F47의 loading은 로그로 입증한다. F49/50은 재시도 중, 최종 실패는F51이다. F69/F70-status/F70b/F71b/F72는 시작 확인·만료·위치 모호성 과정, ACTIVE는F73/76이다. F71은 메뉴 전환 중이며 실제 복귀 확인 메뉴는F77이다.

## 12. 미해결·사용자 승인 대기

| 항목 | 상태·다음 Gate |
|---|---|
| Native ETA/첫 action/컨트롤 지연 | 공식 Host 지원·표시 안정성 후속 확인, 내부 강제 조작 금지 |
| 다중 물리 해상도 | 공식 지원 폼팩터 추가 필요, 1920 override 실패를 성공으로 대체하지 않음 |
| TalkBack·로터리·전환 포커스 | 실제 음성/전체 순회 미검증, Canvas·동적 상태 의미 전달 포함 |
| 겹친 복귀 경로 | 위치 재생의 연속 통과 실패·안전 종료, 방향 신뢰도/연속성 후속 확인. 현재 위치 재준비 복구만 통과 |
| 지도 야간·타일 장애·실제 Host 종료 | 실화면 잔여, 기존 단위 검사를 실제 PASS로 대체하지 않음 |
| 목적지 비공개 | PARTIAL, 시스템 표시·이력·음향 추가 확인 |
| 시각 만족도 | **PENDING_USER_REVIEW**, 위9종·큰 글자/IME·Host 제한 사용자 직접 검토 |

모든 화면을 자동 승인하지 않는다. 한국어 줄바꿈·정보 우선순위, 확대 입력/CTA, 지도+회전 가독성, Host ETA 제한, 공개·평가·복귀 표현은 별도 사용자 승인 Gate다.

## 13. Phase 6 진입 권고

**READY_WITH_LIMITATIONS:** 기본 Pleos 환경의 핵심 입력·추천·안내·실패 안전 종료·공개·평가·복귀 복구와 기존481개 포함 고유492개 검사가 통과했다. 최소 UI 수정과 위 제한을 근거로 Phase 6 개발의 조건부 진입을 권고한다.

실차 사용·배포 준비 완료, 전 경로 주행 성공, 접근성 인증, 모든 해상도 또는 완전 비공개의 승인은 아니다. 시각 승인 전 VISUAL_ACCEPTANCE를 변경하지 않는다.
