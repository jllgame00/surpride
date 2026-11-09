# SURPRIDE 로컬 경로 프록시

Python 3.13에서 검증한 **개발 전용 loopback 서버**다. 공개 서버로 배포하지 않는다. 실제 길 안내를 시작하지 않으며 출발지/도착지 대표 좌표의 차량 진입 가능성도 보증하지 않는다.

## 설치 / 테스트

프로젝트 루트에서 실행한다. 기존 가상환경은 삭제하지 않는다.

```powershell
python -m venv --without-pip backend/.venv
python -m pip --python backend/.venv/Scripts/python.exe install -r backend/requirements.txt
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
```

의존성은 프로젝트 가상환경에만 설치한다. 제한된 Codex 환경의 최초 PyPI 다운로드에는 네트워크 실행 승인이 필요할 수 있다. 이번 검증에서는 승인된 설치로 해결했고 전역 Python/Gradle 캐시·설정을 수정하지 않았다. `.venv` 안의 pip가 없어도 위 `pip --python` 설치와 venv Python 실행이 가능하다.

## 키 / 실행

실제 `.env`가 이미 있으면 보존한다.

```powershell
if (-not (Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
# 개인 편집기에서 backend/.env에 KAKAO_REST_API_KEY 설정. 키를 터미널/채팅에 출력하지 않는다.
backend/.venv/Scripts/python.exe -m backend.dev serve --mode live
```

환경변수가 `.env`보다 우선한다. 간단한 NAME=VALUE/따옴표 값만 지원하며 shell 확장·실행을 하지 않는다. `--mode mock`은 별도의 합성 서버 모드이며 응답 source가 반드시 MOCK이다. LIVE 키 없음/오류 시 자동 전환하지 않는다.

서버는 `127.0.0.1:8765`, worker 1, access log OFF로 실행된다. 임의 URL은 받지 않고 Kakao의 공식 HTTPS endpoint만 호출한다. 토큰을 생략하면 1시간 유효한 난수 개발 토큰이 생성된다. 공급자 키는 서버에만 있으며 개발 토큰과도 다르다. `.gradle/route-proxy/session.json`은 Git 제외 로컬 자격 파일이다. 공유/업로드하지 않는다.

분당 30개·프로세스 세션 100개 요청 상한, 상류 connect 3초/동작 8초/전체 10초 timeout, 응답 2MB/요청 4KB 제한을 둔다. 재시도·영구 캐시·CORS·공개 문서 endpoint는 없다. `Cache-Control: no-store`. 분산 호출 제한이나 과금 중복 방지는 운영 구현이 아니다.

## Pleos 디버그 연결

표준 빌드 및 APK 설치 후, 서버와 다른 터미널에서 실행한다.

```powershell
.\scripts\build-codex.ps1 -Test
adb -s emulator-5554 install --user 10 -r app/build/outputs/apk/debug/app-debug.apk
backend/.venv/Scripts/python.exe -m backend.dev provision-android --adb adb
adb -s emulator-5554 shell am start --user 10 -W -n com.surpride.app/.MainActivity
```

`adb`가 PATH에 없으면 `--adb`에 SDK의 adb.exe 경로를 전달한다. 프로비저닝은 **emulator-5554 / 사용자10 / com.surpride.app 디버그 패키지**만 대상으로 한다. 토큰을 argv·로그에 싣지 않고 stdin으로 `run-as com.surpride.app --user 10`의 private cache에 기록하며 바이트 수를 확인한다. 일반 디버거 접근이며 권한/OS 우회가 아니다. 다른 기기·사용자에는 자동 연결하지 않는다. 이 대상과 다르면 도구를 검토해 명시적으로 변경해야 한다.

에뮬레이터의 `10.0.2.2:8765`로 호스트 loopback에 접근한다. debug Network Security Config만 해당 호스트의 개발 HTTP를 허용하며 release 구성은 로컬 설정을 읽지 않는다. API 키는 APK에 넣지 않는다. 이 방식은 공용 네트워크나 실제 사용자 위치 전송에 사용하면 안 된다.

앱: DEMO 시작 → 실제 장소·경로 검증 → 필요 시 ‘검증 조건: 혼자·장소 제한 없음’ → 180분 → ‘서버 프록시로 경로 평가’. 응답 출처가 LIVE_API인지 MOCK인지 확인한다. 대표 좌표/개발용 공개 출발지, 주차·진입점 UNKNOWN 안내를 유지한다. 공개 전 이름·좌표는 화면에 표시하지 않는다. 서버 재시작 시 토큰이 바뀌므로 다시 프로비저닝한다.

```powershell
# 실제 외부 호출 2건을 발생시키는 명시적 공개 좌표 smoke 검사 (키/geometry 전문 출력 없음)
backend/.venv/Scripts/python.exe -m backend.smoke --request-public-roundtrip
# 별도 터미널에서 종료. 서버는 로컬 session 자격 파일도 제거한다.
backend/.venv/Scripts/python.exe -m backend.dev stop
```

## 계약 / 한계

`POST /v1/routes`, Bearer 개발 토큰, body `{origin:{longitude,latitude},destination:{longitude,latitude}}`. 정상 응답: schema_version 1, source KAKAO_DIRECTIONS 또는 MOCK, 요청 지점, duration_seconds, distance_meters, geometry(경도/위도 쌍), measured_at_epoch_ms. 이름·주소·공급자 키·원문 메시지/응답은 반환하지 않는다. 오류는 `{error: AUTH|NOT_CONFIGURED|RATE_LIMIT|NETWORK|INVALID_COORDINATE|NO_ROUTE|API|INVALID_RESPONSE}`만 반환한다. 서버 프로세스가 종료되어 연결이 안 되면 Android는 NETWORK로 표시한다.

원격 사용에는 HTTPS 서버, 사용자 인증/짧은 액세스 토큰·권한, 사용자별/전체 호출·비용 제한, 분산 rate limiter, 비밀 저장소/키 회전, 개인정보 처리·보관 정책 및 공급자/Pleos 사용 승인이 별도로 필요하다. 현재 정적 개발 토큰은 운영 사용자 인증이 아니다. API 요금·쿼터·캐싱/geometry 이용 조건은 [검증 문서](../docs/phase2/LIVE_ROUTE_API_VALIDATION.md) 참조.
