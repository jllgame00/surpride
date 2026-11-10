# SURPRIDE Phase 5C — 중단 시점 검증 기록

2026-11-10 · **미완료 / 사용자 요청으로 검증 종료**

에뮬레이터 실행이 겹친 뒤 검증용 emulator-5554 연결이 사라졌다. 사용자가 실행한 emulator-5556에는 검증을 이어서 수행하지 않았다. 사용자의 “안된다면 그냥 빌드 취소하고 종료” 지시에 따라 추가 빌드·설치·검증을 중단했다. **READY / READY_WITH_LIMITATIONS / BLOCKED의 최종 제품 판정은 하지 않았다. Phase 6 진입은 보류한다.**

- VISUAL_ACCEPTANCE = PENDING_USER_REVIEW
- DESTINATION_PRIVACY = PARTIAL (기존 증거 유지, 이번 실행으로 상향하지 않음)
- STT = PARTIAL, AI_PROVIDER = RULE_BASED 유지

## 1. Git과 보존

시작 main / `43c659442ea7894f2ff3835eb4c1f3843d5b204f`, clean. 브랜치 변경·커밋·Push·reset·clean 없음. 기존 테스트·기록·before/after 증거는 삭제하지 않았다.

원본 AVD는 기본 사용자 폴더 잠금 파일 쓰기 제한으로 직접 실행할 수 없었다. AVD 파일을 Git 제외 `.gradle/phase5c/android/avd/`로 복사하고 공식 `ANDROID_EMULATOR_HOME`, `ANDROID_AVD_HOME`으로 검증용 복사본을 실행했다. 원본 AVD를 의도적으로 수정하지 않았으며 기존 앱 기록을 삭제하거나 초기화하지 않았다. 복사본에서 조건 선택은 변경했으나 평가 기록은 저장하지 않았다.

## 2. 화면·환경

Pleos Connect v2.1.0 / API34 / Car App API4 기준 프로젝트. 검증용 AVD에서 사용자10, 2560×1440, density160, 상단 OEM 영역과 하단 시스템 조작 영역을 확인했다. 앱 창은 기본 관측에서 x853–2535, y90–1300이었다. 화면의 P / 0km/h 상태에서만 조작했다.

시작·제품 홈·조건 선택·자연어 입력과 CarIME 표시를 실제 관측했다. 준비·활성 지도·재탐색·공개·평가·기분 지도·복귀 완료의 이번 회차 실화면 검증은 완료하지 못했다. Phase 5B 증거를 Phase 5C 재실행 증거로 재사용하지 않는다.

## 3. 해상도·글자 크기

기본 해상도만 실행했다. 글자 크기 1.3을 요청하고 조건 화면 변화를 캡처했지만 전체 화면과 실제 Activity fontScale 수치를 모두 확인하지 못했다. 다중 해상도·density·2배 글자·다크 테마의 실행 행렬은 미완료다. 복사본의 font_scale 설정은 원래 null로 복원했다. size/density는 변경하지 않았다. stay-on은 원래1이었고 true 요청 후 같은 의미를 유지했다. 사용자가 실행한 AVD 설정은 건드리지 않았다.

## 4. 입력·IME

실제 `ai.umos.inputmethod/.service.CarIME`와 `mInputShown=true`를 확인했다. CarIME는 전체 화면 추출 편집 모드로 전환해 앱의 조건 확인·적용 영역을 가렸다. 키보드의 한글 자판을 시각 확인했으나 **자모 조합 입력·긴 문장·줄바꿈·키보드 종료 후 입력 유지의 전체 실조작 검증은 미완료**다.

한국어 여러 줄 문자열의 Activity 재생성 후 유지와 미검토 조건 적용 차단, 1000자 제한, 빈 입력 변경 후 재생성은 Android 계측 검사에서 통과했다. 이는 실제 자판 조합 입력과 다른 검사다. 프로세스 종료·재시작 복원까지 검증한 것은 아니다.

Android 공식 [EditorInfo.IM​E_FLAG_NO_FULLSCREEN](https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_FULLSCREEN)은 전체 화면을 피하도록 요청할 수 있으나 IME가 무시할 수 있다. 이 플래그의 제품 적용과 실제 CarIME 개선 확인은 **수행하지 않았다**.

## 5. 접근성

기본 조건 화면 트리에서 한국어 버튼 이름·선택 상태·활성 상태·bounds를 확인했다. 공통 버튼의 선택 상태 설명·포커스 가능 여부·비활성 접근성 노드와 제목 heading은 계측 검사에서 통과했다.

설치된 활성 서비스는 CarSafetyAccessibilityService와 RotaryService였고 TalkBack은 목록에서 확인되지 않았다. **실제 TalkBack 음성 읽기·순서·전환 포커스 검증을 수행하지 않았다.** 전체 D-pad/로터리 경로·오류 안내·아이콘·모든 화면 터치 목표의 감사는 미완료다.

## 6. 발견 사항·작업 중 변경

| 항목 | 관측 / 조치 | 상태 |
|---|---|---|
| 긴 한국어 버튼 | 260dp 폭의 실제 Android Button 검사에서 줄 수가1로 남아 실패 | 공통 `styleButton`에 `setSingleLine(false)` 한 줄 추가. 빌드와 JVM 테스트 통과, 설치본 GREEN 검증 미완료 |
| CarIME 전체 화면 | 앱 CTA와 조건 영역이 가려짐 | 실제 캡처 확보, 개선 미적용 |
| 자연어 개인정보 설명 | 마지막 음절이 별도 줄로 내려가는 장면 | 캡처 확보, 문구/줄바꿈 미수정 |
| 기분 선택 후 포커스 | 전체 View 재생성 코드 경로 확인 | 새 재현 검사 작성·빌드만 완료, 실행 미완료; 확정 결함으로 판정하지 않음 |

수정 제품 파일: `app/src/main/java/com/surpride/app/ui/SurprideUi.kt` 한 줄. 새 검사: `app/src/androidTest/java/com/surpride/app/AutomotiveUiValidationTest.kt` 8개. **이 두 파일은 검증 중단 시점의 WIP이며 최종 승인된 수정이 아니다.** 기존 디자인·지도·추천·저장·음성·소유권 엔진은 수정하지 않았다.

## 7. 지도와 Host 제한

지도·NavigationTemplate·Native ETA 카드의 이번 회차 최종 실화면 검증은 미완료다. Phase 5B가 보고한 ETA 카드 잘림·첫 action만 표시·전환 잔상은 해결 판정 없이 유지한다. Host 내부 UI·OEM Navigation·시스템 권한을 변조하지 않았다. 실제 지도·GPS·Follow Camera·회전·도로명·경로 갱신·비공개에 대한 기존 회귀 검사는 통과했지만 이번 실행의 시각 합격을 뜻하지 않는다.

## 8. 테스트와 빌드

| 검사 | 결과 |
|---|---|
| 기준선 app JVM | 196 PASS, 강제 재실행 및 assembleDebug 성공 |
| 기준선 PoC JVM | 195 PASS, testDebugUnitTest --rerun 성공, assembleDebug 성공 |
| FastAPI | 90 PASS, 실패0, 기존 Starlette deprecation 경고1 |
| 기존 합계 | **481 PASS** |
| 초기 새 Android 계측6개 | **5 PASS / 1 FAIL**: 긴 한국어 버튼 줄바꿈 검사 |
| 버튼 수정 후 app | assembleDebug·assembleDebugAndroidTest·testDebugUnitTest 빌드 성공. 새 계측 GREEN은 미확인 |
| 추가 IME·선택 포커스 검사 포함8개 | 테스트 APK 빌드 성공, 전체 실행 미완료 |

후속 설치에서 TEST_ONLY 설치 거절과 APK 경로 부재를 관측했다. 새 앱 설치 성공으로 처리하지 않았다. 구 설치본에서 줄바꿈 검사 재실패는 수정본 GREEN의 증거가 아니다. 이후 emulator-5554 연결이 사라져 검증을 종료했다.

로컬 로그는 Git 제외 `.gradle/phase5c/`에 있다. 기존 481개 통과와 새 계측 실패/미실행을 합쳐 “전체 통과”라고 표현하지 않는다. 최종 종료 시점의 두 APK 재빌드·해시 일치·재설치는 미완료다.

## 9. 실제 실행·Crash/ANR

기준 메인 debug APK는 검증용 AVD 사용자10에 설치·실행 성공했다. PoC APK 빌드는 성공했으나 이번 회차 최종 설치·활성 안내 실조작은 미완료다.

중간 exit-info에서 앱의 USER REQUESTED / FORCE STOP을 관측했다. Crash buffer에는 OEM profile 서비스와 중복 UiAutomation 등록 오류가 있었다. 병렬 dump 시도가 겹친 구간은 UI 검증 증거로 인정하지 않는다. **최종 앱/Host Crash·ANR 전수 검사를 마치지 않았으므로 0건이라고 주장하지 않는다.** 실제 차량 주행은 하지 않았다.

## 10. 화면 증거

`evidence/final/`은 실제 ADB PNG 원본이다. 합성·이미지 보정 없음. 아래 표가 화면의 실제 의미이며 번호는 성공 순서를 뜻하지 않는다.

| 파일 | 해석 |
|---|---|
| [01-launch-baseline](evidence/final/01-launch-baseline.png) | 시작 화면 |
| [02-launch-transition](evidence/final/02-launch-transition.png) | 시작 화면 전환 중, 홈 완료 증거 아님 |
| [03-home-baseline](evidence/final/03-home-baseline.png) | 제품 홈 |
| [04-oem-foreground-transition](evidence/final/04-oem-foreground-transition.png), [05](evidence/final/05-oem-foreground-transition.png) | OEM 지도 전면 구간, 자연어/IME 성공 증거 아님 |
| [06-natural-input](evidence/final/06-natural-input.png) | 자연어 입력과 하단 CTA, 긴 한국어 고지 줄바꿈 한계 |
| [07-ime-open-baseline](evidence/final/07-ime-open-baseline.png) | 실제 전체 화면 CarIME, 앱 CTA 가려짐 |
| [08-conditions-baseline](evidence/final/08-conditions-baseline.png) | 기본 조건 화면, 체크·선택 테두리·넓은 버튼 |
| [09-conditions-font130](evidence/final/09-conditions-font130.png) | 1.3 요청 후 조건 화면, 전체 큰 글자 합격 증거 아님 |

Before와 Phase 5B는 기존 `evidence/before/`, `evidence/after/`에 보존돼 있다. 이번 캡처는 기본 화면의 읽기 가능성만 제한적으로 보여 준다. 전 화면 Before/5B/Final 비교와 최종 디자인 만족도 평가는 미완료다.

## 11. 시나리오 A–O

정상 추천→안내→공개→평가(A), 조건 변경→재추천(B), 자연어 오류 수정(C), 경로 실패 복구(D), GPS 상실(E), 재탐색 성공/실패(F/G), 즉시 공개(H), 정차 확인 공개(I), 복귀(J), 안내 취소(K), Host 종료(L), 프로세스 재시작(M), 서버 미연결(N), 위치 권한 없음(O)의 **전체 실화면 행렬은 완료하지 않았다**.

기존 도메인·개인정보·소유권·도착·복귀 검사가 회귀 통과했다는 사실만 유지한다. M 중 Activity 화면 재생성의 입력 유지 일부는 새 계측 검사로 통과했다. 그 외 시나리오의 과거 증거를 이번 실화면 검증으로 대체하지 않는다.

## 12. 사용자 검토·후속 Gate

검증 중단 시점의 한 줄 수정과 새 검사8개의 재실행, 실제 CarIME 개선, 큰 글자/해상도/포커스/접근성 전체 행렬, 지도·Host·상태 전이 A–O, 최종 Crash/ANR·설치 해시, 누락 화면 캡처가 남는다. 최종 시각 만족도는 별도 사용자 검토가 필요하다. **현재 자료로 Phase 5C 완료 또는 Phase 6 진입을 권고하지 않는다.**
