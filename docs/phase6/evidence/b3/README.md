# B3 공개 검증 증거

전체 해석과 제한은 [B3 결과](../../B3_HOST_LIFECYCLE_VOICE.md)를 따른다. 이미지는 공개 합성 MOCK 시험이며 실제 사용자 위치·목적지·토큰은 포함하지 않았다.

- `test-summary.json`: XML 기반 고유 검사 수. 기존 576 + 신규 26 = 602 PASS. native 반복 실행은 중복 가산하지 않는다.
- `red-summary.txt`, `build-summary.txt`: 결함 재현 11개/7실패와 최종 전체 빌드 성공. compile 오류는 RED 결함 증거에 포함하지 않았다.
- `apk-hashes.json`: 두 APK와 두 계측 APK의 빌드 output/설치본 동일 해시.
- `baseline-prepared.png`: 수정 전 재진입 오류 화면. 새 stack 없는 초기 관측이다.
- `post-menu-1.png`: 처음 세 차례 준비 메뉴 조작 중 잔상이 남은 메뉴.
- `diagnostic-menu-2s.png`: 추가 cold 진입에서 관측한 정상 준비 메뉴. 활성 안내 종료 메뉴가 아니다.
- `diagnostic-host-reentry.png`: 비활성 Home 전환/명시적 재진입 뒤 준비 화면. 목적지 비공개 상태 유지.
- `menu-trials.json`, `diagnostic-summary.json`: 요청 후 screenshot 관측 시각이다. 정밀한 Host 표시 완료 지연으로 해석하지 않는다.
- `host-event-summary.json`: 첫 구간 invalidate→앱 template 재호출 지연. Host 표시 지연이 아니다.
- `diagnostic-events-final.txt`: 추가 진단의 단조시각/유한 enum. RELEASE는 시도 이벤트이며 성공 영수증이 아니다.
- `surface-stack-safe.txt`: PoC PID 30837 안의 AndroidX onPause snapshot/PixelCopy 예외 stack. OEM 내부 stack이나 ANR stack이 아니다.
- `platform-error-summary.json`: 관측 가능한 logcat 구간의 새 ANR/own crash 0, caught snapshot 오류 11. 전체 플랫폼 무오류 보증이 아니다.
- `minimal-pause-repro.json`: 비활성 준비 화면→data 없는 결과 Activity→BACK 두 시도 중 snapshot 오류 1회 재현.
- `restoration-proof.json`: 원 private 파일/부재 상태 8건 및 GPS 복원, 시험 폴더 제거, 최종 Navigation Focus 비어 있음. force-stop 정리를 정상 종료 UX의 근거로 사용하지 않는다.
- `privacy-check.json`: 새 공개 파일 및 APK 4개의 알려진 개발 인증정보 UTF-8/UTF-16LE literal 검사. 일반적인 전체 비밀/개인정보 탐지 보증은 아니다.
- `worktree-preservation.json`: 시작 추적 파일의 바이트 비교와 동일 HEAD. Git 커밋·Push 없음.

로컬 app-automotive 1.7.0 AAR의 classes.jar를 javap로 대조했다. BaseCarAppActivity의 private snapshot 메서드는 holder Surface null/크기를 확인한 뒤 PixelCopy를 호출하고 Exception을 잡아 snapshot 이미지를 비운다. 이 메서드를 앱에서 반사/변조하지 않았다. invalid source의 선행 원인과 B2 FocusEvent ANR과의 연결은 미확정이다. 원 사용자 packet·GPS 백업·전체 logcat/bugreport는 공개 증거에서 제외했고 이번 임시 private 백업은 복원 검증 뒤 삭제했다.
