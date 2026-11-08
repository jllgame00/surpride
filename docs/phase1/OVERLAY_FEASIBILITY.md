# Phase 1C-1 — Pleos Navigation Overlay 타당성 검증

**판정: NOT_VIABLE — 현재 Pleos Connect v2.1.0/API 34 에뮬레이터에서 일반 앱 권한 절차로 필수 오버레이 권한을 얻지 못했다.**

Android API는 존재하지만, 권한을 선언한 SURPRIDE debug 앱의 `Settings.canDrawOverlays()`는 `false`였고 표준 권한 설정 화면을 열면 `ActivityNotFoundException`이 발생했다. 일반 설정의 특수 앱 액세스에도 해당 허용 항목이 없었다. 사용자 규칙 8에 따라 **오버레이 생성과 Navigation 위 유지·가리기 실험을 중단**했다. 다른 Pleos 버전/승인된 배포 환경까지 영구적으로 불가능하다는 판정은 아니다. 명시적 권한 거부 정책의 원인이나 마켓 심사 불허를 확인한 것으로도 해석하지 않는다.

검증 기준일: 2026-11-09(KST). 에뮬레이터 자체 시각은 호스트와 다르다.

## 1. 시작·종료 상태와 변경 범위

- 시작: `main`, HEAD `0ef939434b572b1c85423d741b83227b23f10177`, clean.
- 종료: 브랜치/HEAD 동일. 생성한 진단 소스·문서·증거만 미커밋 상태. 커밋/Push/스테이징/원격 변경 없음.
- 기존 `app/src/main`, `app/src/test`, Vehicle 2.3.2/NaviHelper 3.0.5 구현, Gradle 설정, Phase 0B 스크립트, 이전 검증 문서 변경 없음.
- 추가 권한과 진단 Activity는 **`src/debug`에만** 선언했다. 기존 MainActivity와 시작 화면은 수정하지 않았다. release 빌드는 이번에 실행하지 않았으며, 해당 debug 소스는 release 소스 집합에 포함되지 않는다.
- 앱 위에 그리는 코드, Service, 접근성 서비스, 화면 캡처 권한, 숨겨진 Settings 컴포넌트 호출, 시스템 수정은 추가하지 않았다.

## 2. 공식 Android API 34 지원과 제한

| 항목 | 공식 계약 | 현재 환경에서 확인한 범위 |
| --- | --- | --- |
| `SYSTEM_ALERT_WINDOW` | API 23+ 대상 앱은 Manifest 선언과 사용자 특별 접근 허용 필요 | debug merged Manifest/설치 패키지에 선언 확인, `granted=false` |
| `Settings.canDrawOverlays(context)` | 실제 그리기 허용 여부 확인 | 앱 자신의 사용자 컨텍스트에서 `false` |
| `ACTION_MANAGE_OVERLAY_PERMISSION` | 사용자 허용 화면 요청. 처리 Activity가 없을 수 있음. API 30+는 앱별 data URI 직접 진입에 의존하지 않음 | data 없는 일반 Intent도 앱에서 SETTINGS_UNAVAILABLE |
| `TYPE_APPLICATION_OVERLAY` | API 26+, 값 2038. 일반 Activity 위·중요 시스템 창 아래. 시스템이 위치/크기/표시를 변경할 수 있으며 소유 사용자 화면에만 표시 | API 34/상수 2038 확인. 실제 Window 생성 지원은 권한 단계에서 중단하여 미검증 |

출처: [Android 권한](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW), [Settings 권한 요청/조회](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_OVERLAY_PERMISSION), [오버레이 창 계약](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY).

비대화형 플래그만 설정한다고 안전한 터치 통과가 보장되지 않는다. Android 12+에서는 `FLAG_NOT_TOUCHABLE`인 다른 UID의 오버레이도 불투명도 조건 등에 따라 아래 앱의 터치를 차단할 수 있다. 공식 최대 가림 불투명도 기본값은 0.8이며 `InputManager.getMaximumObscuringOpacityForTouch()`로 조회할 수 있다. 완전 불투명 가림과 아래 내비 터치 보존을 동시에 가능하다고 가정하면 안 된다. 이는 문서상 제약이며 이번 단말 실험 성공 결과가 아니다. [터치 전달 계약](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE)

또한 대상 앱은 API 31+의 `setHideOverlayWindows()`로 비시스템 오버레이를 숨길 수 있다. 기본 Pleos Navigation이 실제 사용하는지는 미검증이며 이를 무력화할 방법을 시도하지 않았다. [Android 공식 보호 API](https://developer.android.com/reference/android/view/Window#setHideOverlayWindows(boolean))

## 3. 일반 권한 절차 검증

대상은 연결된 `emulator-5554`의 foreground 사용자 **10**, Pleos Connect v2.1.0/API 34이다. 캡처에 기어 P/0 km/h가 표시됐다. Vehicle SDK의 미확인 기어·속도 API로 정차 상태를 판정한 것은 아니다. 차량 상태를 변경하지 않았다.

1. Package Manager로 표준 오버레이 설정 Intent를 조회: 앱 data 포함/미포함 모두 `No activity found`.
2. ADB의 일반 `am start`로 같은 공개 Intent 실행: `unable to resolve Intent`. 숨겨진 Activity를 지정하지 않았다.
3. 일반 설정 UI → 앱 → 특수 앱 액세스로 직접 이동해 확인. 표시 항목은 시스템 설정 수정, 알림 액세스, 프리미엄 SMS 액세스, 사용 기록에 액세스, Wi-Fi 제어였다. ‘다른 앱 위에 표시’ 항목이 없었다. [메뉴 증거](overlay-evidence/special-app-access.png), [항목 기록](overlay-evidence/special-app-access.txt)
4. debug 전용 Manifest에 SAW를 선언하고 빌드/설치한 뒤 진단 Activity 실행. `Settings.canDrawOverlays=false` 확인.
5. 진단 화면의 ‘일반 오버레이 권한 설정 열기’를 명시적으로 눌렀다. API 34에서 data 없는 표준 Intent를 요청했으나 `ActivityNotFoundException`을 잡아 `SETTINGS_UNAVAILABLE`로 표시했다. [앱 결과](overlay-evidence/permission-probe.txt), [캡처](overlay-evidence/permission-probe.png)
6. Package Manager도 해당 권한 `granted=false`. AppOps 읽기 조회는 `No operations / Default mode: default`. AppOps 기본 상태만으로 지원 불가를 추론하지 않고 앱의 실제 권한 조회와 UI 부재를 함께 근거로 삼았다. [권한 선언/상태](overlay-evidence/declared-permission.txt)

이 단계에서 필수 권한을 확보하지 못했으므로 작은 테스트 오버레이도 생성하지 않았다. `WindowManager.addView`를 권한 없이 시험하거나 강제 권한 부여를 하지 않았다. `pm grant`, `appops set`, 보안 설정 변경, 시스템 앱/파일 수정, 다른 사용자로 실행하여 권한을 얻는 시도도 하지 않았다.

따라서 **이 이미지에서 일반 앱이 사용할 수 있는 권한 획득 경로를 확인하지 못했다**는 것이 실제 차단 지점이다. SAW API 자체가 AAOS 전체에서 제거됐거나 Pleos가 특정 명시 정책으로 거부했다고 단정할 근거는 없다.

## 4. Pleos 권한·주행 UX 심사 검토

[Navigation 개발 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide)는 운전자 안전, 단일 활성 경로, 카테고리 일치, 최소 권한과 핵심 주행 정보 표시를 요구한다. 주행 중 복잡한 조작과 전체 화면 팝업 등에도 제한이 있다. 타사 내비 위 목적지 가림을 승인한다는 명시 조항은 확인하지 못했다. 그렇다고 모든 오버레이가 명시적으로 금지됐다고 단정하지 않는다.

[주행 제한 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)는 `CarUxRestrictions` 상태와 변경 알림에 따라 제한을 적용하도록 요구한다. Activity가 주행 제한으로 가려졌는데 별도 오버레이만 남는 상황도 승인된 동작이라고 가정할 수 없다. 향후 승인 환경에서 재시험하려면 제한 활성/상태 불명/화면 전환 시 즉시 제거하는 설계를 먼저 확인해야 한다.

[보안 가이드](https://document.pleos.ai/en/docs/pleos-only/app-registration-guide/app-security-guide)는 필요한 권한·공식 API만 사용하고 불필요한 컴포넌트 노출을 줄이도록 요구한다. 이번 외부 실행 진단 Activity는 debug 소스에만 있고 비밀 데이터나 권한 변경 기능을 제공하지 않는다. 실제 배포에서 SAW 사용 및 OEM Navigation 위 가림을 승인받은 것은 아니다. Android 특별 접근 허용과 Pleos 마켓/주행 UX 승인은 별개의 조건이다.

정책 결론: **오버레이 방식의 배포·주행 승인 여부는 UNVERIFIED**. 권한과 기술 동작이 나중에 해결돼도 이 상태로 FEASIBLE로 올릴 수 없다.

## 5. Navigation 유지·안전 정보·터치 실험 결과

| 검증 항목 | 결과 |
| --- | --- |
| 작은 비대화형 오버레이 생성 | 미실시 — 필수 권한 미확보로 중단 |
| NaviHelper가 OEM Navigation을 연 뒤 유지 | 미검증 — 이번 Phase에서 새 경로 요청 없음 |
| 일반 안내/TBT/속도·안전 영역 비가림 | 미검증 — 안전한 위치/영역을 확보했다고 주장하지 않음 |
| 오버레이 아래 터치 통과 | 미검증 — NOT_TOUCHABLE만으로 통과 보장 불가 |
| 화면 크기·레이아웃·맵 이동·경로 재탐색 대응 | 미검증 |
| 주행 시 자동 제거/CarUxRestrictions 연동 | 미구현·미검증 — 권한 단계 이후 개발 중단 |
| 기존 앱 회귀 | 시작 화면·Vehicle 응답·NaviHelper 연결/idle 확인 |

Phase 1B에서 이름이 TBT 영역과 하단 카드에 동시에 표시됐다. 고정 사각형으로 이름만 가려도 레이아웃이 바뀌면 안내 거리·회전 정보와 겹칠 수 있다. 지도 핀/최종 경로를 가리려면 지도 정보까지 더 넓게 가릴 위험도 있다. 이는 이전 캡처를 바탕으로 한 설계 위험이며 이번 오버레이로 시험한 결과가 아니다.

## 6. 목적지 노출 채널 구분

이전 [Phase 1B 검증](NAVIHELPER_VALIDATION.md)을 보존하며 아래 사실과 추론을 구분한다.

| 채널 | 기존 관찰 | 오버레이가 해결하는지 |
| --- | --- | --- |
| SURPRIDE 자체 화면 | 목적지 원문 생략 가능 | 자체 UI에서 처리 가능, 외부 가림 불필요 |
| OEM 목적지 이름/주소 | 경로 요청·안내 화면의 이름 노출. 최근 목록은 주소 일부도 노출 | 이번 실험 미검증. 복수 위치/화면 변화에 맞춰 가려야 함 |
| 지도 도착 핀 | 노출 확인 | 미검증. 고정 텍스트 가림으로 해결되지 않음 |
| 최종 경로 형태/도착 방향 | 전체 비공개 여부를 검증하지 않음 | 시각 가림만으로 추정 가능성까지 제거한다고 보장 불가 |
| 최근 목적지/경로 이력 | 최근 목록의 테스트 장소 노출 확인, 전체 이력은 미검증 | 오버레이는 이력 저장/조회 자체를 바꾸지 않음 |
| 음성 안내 | 발화 내용 미검증 | 시각 창은 음성 채널을 제어하지 않음. 안전 음성 끄기 미시도 |
| 알림·홈·클러스터 | 알림 한 시점에서 미노출, 홈/독립 클러스터는 미검증 | 해당 시스템 영역/다른 디스플레이까지 가려진다고 보장 불가 |
| ETA/회전 안내 | SDK 값/TBT 수 수신 확인 | 이 정보를 보존하면서 목적지 정보만 선택적으로 숨겼다는 검증 없음 |

따라서 권한이 있는 다른 환경에서 작은 창이 표시되더라도 **도착 전 모든 채널 비공개 성공과 동등하지 않다**.

## 7. 빌드·테스트·에뮬레이터 실행

```powershell
.\scripts\build-codex.ps1 -Test
```

결과: **BUILD SUCCESSFUL, 26초, 종료 코드 0**. `assembleDebug`, `testDebugUnitTest` 수행. 기존 테스트 **28개 통과 / 실패 0 / 오류 0 / 스킵 0**(Vehicle 14, Navigation 13, 기본 1). 이 권한 조회 spike에 구현을 그대로 반복하는 새 단위 테스트는 추가하지 않았으며 실제 앱 컨텍스트의 권한/Intent 동작으로 검증했다. [빌드 로그](overlay-evidence/build-tests.txt), [테스트 집계](overlay-evidence/test-summary.json)

```powershell
adb -s emulator-5554 install --user 10 -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start --user 10 -W -n com.surpride.app/.overlay.OverlayPermissionProbeActivity
adb -s emulator-5554 shell am start --user 10 -W -n com.surpride.app/.MainActivity
git diff --check
```

설치 `Success`, 진단 Activity COLD 실행 `Status: ok`, 기존 MainActivity WARM 실행 `Status: ok`. [설치/진단 실행](overlay-evidence/install-probe.txt). MainActivity에서 Vehicle RESPONDING, SOC 100%, NaviHelper CONNECTED/리스너 1/SAFE_GUIDING/활성 false를 확인했다. [회귀 상태](overlay-evidence/main-regression.txt), [화면](overlay-evidence/main-regression.png). `git diff --check` 통과. SDK 연결은 확인했지만 새 경로 탐색 회귀는 이번에 수행하지 않았다.

사용 가능한 crash 버퍼에 SURPRIDE Process 기록 0, ActivityManager 오류 버퍼에 해당 앱 ANR 기록 0. [로그 상태](overlay-evidence/runtime-health.txt). 전체 시간·장시간 안정성 보장은 아니다. 마지막 화면은 기존 SURPRIDE MainActivity이며 새 경로는 시작하지 않았다.

## 8. 생성 파일과 다음 확인 사항

생성 파일:

- `app/src/debug/AndroidManifest.xml` — debug 전용 SAW와 진단 Activity.
- `app/src/debug/java/com/surpride/app/overlay/OverlayPermissionProbeActivity.kt` — 앱 컨텍스트의 권한 조회/표준 설정 요청. 오버레이 생성 없음.
- 이 문서 및 `docs/phase1/overlay-evidence/` — 권한·설정 UI·빌드·회귀 증거. 원시 UI XML/APK/캐시는 Git 대상에 추가하지 않음.

우선 공식 담당자에게 **v2.1.0 일반 앱의 SAW 허용 UI 제공 여부**, 승인 가능한 앱 카테고리, OEM 내비의 목적지 영역 가림 허용 여부, 보호 화면/다중 디스플레이/주행 제한 적용 요구사항을 확인해야 한다. 실제 문의 전송은 하지 않았다.

정상 권한 UI와 정책 승인을 제공받기 전에는 이 방식의 개발을 확대하지 않는다. 현재는 Phase 1B의 출발 전 비공개 후 OEM 안내 공개 시제품이 구현 근거가 있는 대안이다. 도착까지 비공개가 필수라면 승인된 목적지 숨김 API 또는 독립 내비 엔진/카테고리 검토가 필요하다.
