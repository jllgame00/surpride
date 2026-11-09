# SURPRIDE Phase 2B-1.5 — 실제 길찾기 API / 인증 프록시 검증

검증일 2026-11-09. **최종 판정: READY (로컬 실제 경로 데이터 연동 범위)**.

실제 카카오 자동차 길찾기의 편도·복귀 요청이 각각 성공했고, 최종 Android APK가 Pleos에서 로컬 FastAPI 프록시를 통해 LIVE_API 후보 추천을 생성했다. 실제 길 안내·차량 진입점 검증·상용 앱 승인을 뜻하지 않는다.

## 1. Git / 기존 구현 보존

- 시작: `main`, HEAD `31fb37fd18355a61013fccaaa7ccec536bef6939`, clean. 종료도 같은 HEAD이며 변경사항은 미커밋 상태다. 자동 커밋·Push·원격 변경 없음.
- 기존 RouteProvider/KakaoRouteParser/DestinationEngine·DEMO 카탈로그/저장·Vehicle/NaviHelper·nav-host-poc·Phase 0B 스크립트와 이전 검증 문서를 보존했다.
- RouteRecommendationEngine에 현재 시각 의존성 및 최종 마감 재검사를 추가했다. 기존 단위 테스트에는 고정 시각을 주입해 결정성을 유지했다. 원래 DestinationEngine과 SDK 버전은 변경하지 않았다.
- 처음 프로세스 환경변수의 키는 없었다. 이후 Git 제외 `backend/.env`에 키가 구성된 것을 확인하고 사용했다. 실제 `.env`를 생성·수정하거나 키 값을 출력하지 않았다. `.env.example`만 추가했다.

## 2. 공식 API 계약 / 이용 조건

[공식 Directions 문서](https://developers.kakaomobility.com/guide/navi-api/directions)에 따라 서버만 `GET https://apis-navi.kakaomobility.com/v1/directions`를 호출한다. 헤더는 `Authorization: KakaoAK …`, 지점 순서는 **경도,위도**, priority=RECOMMEND, summary=false, alternatives=false다. 차량 통제/안전 정보를 비활성화하는 옵션은 사용하지 않는다. duration은 초, distance는 미터, roads.vertexes는 경도/위도 쌍이다. API 호출 성공과 경로 result_code=0을 별도로 검사한다.

[가격/쿼터](https://developers.kakaomobility.com/price/)는 자동차 길찾기 일일 10,000건, 무료 쿼터 초과 월간 1,000,000건 이하 구간 건당 8원을 안내한다. 공통 월간 쿼터와 계정 사용량/과금 설정도 적용되므로 무조건 무료가 아니다. 계정의 과금 설정을 변경하거나 쿼터를 고의로 소진하지 않았다.

[오류 계약](https://developers.kakaomobility.com/guide/navi-api/solution.html)과 [결과 코드](https://developers.kakaomobility.com/guide/navi-api/reference.html)를 확인했다. HTTP 401/403은 AUTH, 429 및 HTTP400/code=-10은 RATE_LIMIT, -2는 지점/인자 오류, -903은 AUTH로 정규화한다. 경로 코드 1/105~107은 경로 없음/통행 장애, 101~104는 지점/도로 선택 문제로 구분한다. 원문 오류 메시지와 헤더·응답 전문은 전달/로그 출력하지 않는다.

[운영 정책](https://developers.kakao.com/terms/ko/site-policies)은 캐시 목적·최신성·권리 고지 제한을 둔다. 이번 프록시는 영구 경로 캐시가 없고 no-store 응답을 반환한다. 증거에는 공개 테스트 지점의 집계 숫자만 남기고 geometry 전문을 저장하지 않았다. **자동차 내장형 상업 서비스, 독립 내비게이션, geometry 재배포/장기 저장 및 별도 지도 표시·목적지 비공개 UX의 허용 범위는 아직 확정하지 않았다.** 일반 API 접근 성공을 공급자 제휴나 Pleos 심사 승인으로 해석하지 않는다. 실제 배포 전 공급자와 Pleos에 확인해야 한다.

## 3. 프록시 / Android 구현

- `backend/app.py`: FastAPI, 환경변수 키, 고정된 공식 HTTPS 호스트, TLS 검증 유지, redirect OFF, 공급자 키/원문/이름/주소가 없는 최소 응답 모델.
- 로컬 loopback `127.0.0.1:8765`, worker 1, 난수 개발 Bearer 토큰 및 서버/앱 모두 1시간 만료, 분당30/세션100개 공유 요청 상한. 운영용 계정별·분산 rate limit이나 실제 요금 상한은 아니다.
- 상류 connect3초/동작8초/전체10초 timeout, 요청4KB·응답2MB·geometry100,000점 상한. 자동 재시도·자동 Mock 전환 없음. 잘못된 요청의 값도 422 응답에 반사하지 않는다. access log 및 HTTPX/HTTPCore 요청 로그 OFF.
- `backend/dev.py`: `.env`보다 환경변수 우선, 실행/종료, stdin 기반 디버그 설정 전달. 공급자 키와 개발 토큰을 구분한다. 개발 토큰은 `.gradle/route-proxy/session.json`과 앱 private cache에만 전달하며 argv/출력/BuildConfig에 넣지 않는다.
- 프로비저닝 대상은 emulator-5554 / 사용자10 / com.surpride.app으로 고정하고 일반 디버그 `run-as`를 사용했다. 인자 순서 오류를 수정하고 기록 바이트 수 검증·회귀 테스트를 추가했다. 권한 우회 없음.
- `LiveRouteProvider`: 기존 RouteProvider 유지, 작업 스레드에서 HTTP POST, 3초 연결/12초 read timeout, 크기 제한/redirect OFF, 서버 source·스키마·시각·요청 지점 일치 검사. 기존 KakaoRouteParser로 초/미터/geometry를 다시 검증한다. HTTP를 거쳐도 source=MOCK이면 MOCK이다.
- 디버그 Network Security Config만 10.0.2.2의 개발 HTTP를 허용한다. release 구성은 개발 설정을 읽지 않는다. release APK 빌드/상용 HTTPS 인증은 이번 검증 대상이 아니다. 앱에는 INTERNET 일반 권한만 추가했다.
- Data Lab은 기존 DEMO와 분리된 메모리 세션이다. 비동기 조회 중 중복 입력을 막고 취소/화면 이탈/주행 UX 제한 시 진행 요청 및 결과 적용을 중단한다. 네트워크 읽기 종료는 timeout까지 지연될 수 있으며 이미 보낸 외부 요청을 되돌리지는 못한다.
- 추천은 편도와 복귀를 별도 조회하고 체류20분/안전 여유10분 등 표시된 가정을 합산한다. API 값으로 경관 점수나 SOC 기반 range를 만들지 않는다. 긴 순차 조회 후에도 현재 시각 기준으로 응답 신선도와 귀가 마감을 재검사한다.

## A. Mock/계약 테스트로 검증한 기능

FastAPI **29개 통과**, Android **89개 통과(기존78 + 신규11)**. 실패/오류0. [집계 / APK SHA-256](evidence/live-route/test-summary.txt).

- FastAPI: 정상 공식 요청 헤더/좌표 순서·최소 변환, 인증 거부/만료·키 미설정·요청 상한, HTTP/공식 코드별 quota/auth/좌표/경로 없음, timeout·오류 메시지 비노출, 잘못된 JSON/숫자/geometry·크기 상한, 별도 복귀 및 source=MOCK, stdin 설정 전달.
- Android: proxy 계약·단위/geometry, 잘못된 서버/지점/출처/시각, 미구성·네트워크·서버 오류 분리, token/응답 진단 문자열 비노출, 만료/허용 호스트, 실제 관찰 집계값의 60분 제외/180분 허용, 목적지 비공개.
- 검토에서 발견한 **64초 순차 조회의 마지막 신선한 응답 오제외**를 먼저 실패 테스트로 재현한 뒤 수정했다. 조회 중 귀가 마감 감소도 테스트했다. 최종 읽기 전용 검토에서 중요한 추가 문제는 발견되지 않았다.
- HTTP 400의 공통 오류 코드를 API로 뭉뚱그리던 문제도 실패 테스트 후 정규화했다. quota 초과·상류 인증 실패/timeout 등은 **합성 transport 테스트**이며 실제 공급자 제한을 일으킨 실험이 아니다.
- FastAPI/Starlette TestClient의 httpx 사용 중단 예정 경고 1개가 남았다. 현재 테스트는 통과하며 미래 의존성 변경 시 갱신이 필요하다. SDK/AGP/Gradle/Kotlin 업그레이드와 관계없다.

## B. 실제 외부 API에서 검증한 기능

고정 공개 출발지는 광화문 대표 좌표, 공개 검증 목적지는 이촌한강공원 대표 좌표다. 개인 GPS/사용자 위치가 아니다. **차량 진입점·주차장은 UNKNOWN**이며 대표 좌표를 차량 접근 검증값으로 바꾸지 않았다.

첫 smoke 관찰:

| 항목 | 편도 | 별도 복귀 |
| --- | --- | --- |
| 공급자 | KAKAO_DIRECTIONS | KAKAO_DIRECTIONS |
| duration | 1,247초 | 2,553초 |
| distance | 7,437m | 11,149m |
| geometry 점 수 | 122 | 215 |

왕복+체류20분+여유10분 = **5,600초(93분20초)**, 60분 예산 초과. 복귀를 편도의 2배로 추정하지 않았다. [공개 지점 집계 증거](evidence/live-route/public-roundtrip-summary.json). 해당 숫자를 이용한 평가기 회귀 테스트는 실제 HTTP 요청을 추가 발생시키지 않는 fixture 검사다.

실제 응답 수신/파싱/왕복 비대칭/도로 geometry 제공을 확인했다. 공급자의 교통 반영 방식을 별도의 교통 관측으로 대조하지는 않았다. 미래 귀가 시각의 교통 예측을 호출하지 않았으며, 현재 복귀 조회를 미래 도착 보장으로 표시하지 않는다. 후보 전체의 차량 진입 성공/운영·주차 가능 여부와 경로 안전성은 미검증이다.

## C. Pleos 앱에서 실제 확인한 기능

대상: emulator-5554, Pleos Connect v2.1.0 / API34, 사용자10, P/0km/h 정차 화면. APK 설치 `Success`, MainActivity COLD `Status: ok`. 최종 APK 설치 시 TotalTime1151ms/WaitTime1159ms. 앱 화면에서 시작한 로컬 HTTP가 **FastAPI→Kakao 실제 HTTPS**를 거쳐 LIVE_API 추천으로 돌아왔다.

- 초기 실제 조회에서는 이촌한강공원 코스 83분/18.4km, 최종 APK 재검증에서는 84분/17.9km였다. 시점별 경로/예측값으로 고정 보장값이 아니다. 가용180분 조건을 사용했다. 공개 전 후보 이름·좌표 비노출을 UI 계층에서 검사했다.
- [최종 LIVE 비공개 화면](evidence/live-route/live-secret.png), [명시 공개 화면](evidence/live-route/live-revealed.png). 실제 길 안내 아님/고정 공개 출발지/진입점 UNKNOWN 표시를 유지했다.
- [서버 중지 NETWORK](evidence/live-route/server-offline.png), [서버 프로세스에서만 키를 공백으로 마스킹한 NOT_CONFIGURED](evidence/live-route/server-key-missing.png), 서버 재시작으로 달라진 개발 토큰에 대한 AUTH를 확인했다. `.env`를 수정하지 않았다.
- [HTTP Mock 서버](evidence/live-route/proxy-mock.png)는 동일 Android provider로 52분/8.4km를 반환했지만 화면과 모델은 MOCK을 유지했다. 자동 fallback 없음.
- 기존 MVP의 연인/자연/30분 선택이 유지됐다. Vehicle RESPONDING / NaviHelper CONNECTED / OEM 활성 경로 false. 새 실제 길 안내·차량 제어 명령 없음. 최종 앱 PID crash buffer 및 앱 exit-info ANR 관찰 범위는 [런타임 증거](evidence/live-route/ui-runtime.txt) 참조. 장시간/실차 안정성 검증은 아니다.
- Git 추적 대상 파일과 최종 APK 전체 항목에서 실제 공급자 키·현재 개발 토큰의 바이트 일치 0을 확인했다. `.env`, `.venv`, `.gradle` 자격 파일은 Git 제외다. 기본 Navigation/클러스터/음성에서 비밀 목적지를 숨기는 검증은 수행하지 않았다.

## D. 향후 독립 내비게이션 연결에 필요한 기능

1. 검증된 차량 진입점·주차장과 영업/접근성/동행 조건을 확보하고 경로 지점과 관광지 대표 위치를 분리한다. 현재는 UNKNOWN이며 실차 출발용 추천이 아니다.
2. 실제 사용자 동의·전경 위치/연속 업데이트·정확도 정책, 미래 출발/귀가 시각·교통 갱신/재계산·호출 예산을 검증한다. 현재는 공개 고정 좌표만 서버에 보냈다.
3. 상용 HTTPS 프록시, 사용자 인증/권한·짧은 토큰/서버 비밀 관리, 분산 호출·요금 제한과 보관 정책을 설계한다. 현재 로컬 개발 토큰을 운영 인증으로 재사용하지 않는다.
4. 공급자와 Pleos의 내장형 자동차 서비스/지도 geometry 표시·보관·미스터리 UX 승인 조건을 확정한다.
5. NavigationTemplate 지도/TBT·지도 엔진·음성·경로 이탈/재탐색·도착·단일 활성 경로 소유권과 안전 UX를 별도로 구현/검증한다. nav-host-poc와 NaviHelper는 이번 단계에서 통합/변조하지 않았다.

## 4. 재실행 명령 / 환경 제약

설치와 환경 예제는 [backend/README.md](../../backend/README.md) 참조. 프로젝트 루트에서:

```powershell
backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider
.\scripts\build-codex.ps1 -Test
# 터미널 1: 로컬 .env 또는 환경변수의 키 사용, 공급자 키는 출력하지 않음
backend/.venv/Scripts/python.exe -m backend.dev serve --mode live
# 터미널 2: 이미 빌드·설치된 debug APK에 새 개발 세션 전달
backend/.venv/Scripts/python.exe -m backend.dev provision-android --adb adb
# 명시적으로 실제 공개 좌표 요청 2건 수행할 경우에만
backend/.venv/Scripts/python.exe -m backend.smoke --request-public-roundtrip
backend/.venv/Scripts/python.exe -m backend.dev stop
```

최초 Python venv ensurepip/패키지 조회가 제한된 실행 환경에서 실패했다. 프로젝트 가상환경에만 PyPI 설치를 승인받아 해결했다. 글로벌 Python/Gradle 설정·캐시/권한은 변경하지 않았다. 실행 결과를 Mock 성공으로 대신하지 않았다. 의존성 버전은 실제 설치된 항목을 requirements.txt에 고정했다.

검증 종료 시 서버를 중지하고 임시 호스트 세션 및 앱의 개발 자격 cache 파일을 제거했다. 실제 `.env`는 보존했다. 현재 화면은 기존 MainActivity다. 이후 사용 시 서버 실행 및 프로비저닝을 다시 해야 한다.

## 5. 변경 파일

- 수정: `.gitignore`, main/debug Manifest, `RealRouteActivity.kt`, `RouteRecommendationEngine.kt`, 기존 `RealRouteTest.kt`의 시각 주입.
- 생성 Android: `LiveRouteProvider.kt`, debug/release `ProxyConfiguration.kt`, debug `proxy_network_security_config.xml`, `LiveRouteProviderTest.kt`.
- 생성 backend: `app.py`, `dev.py`, `smoke.py`, `__init__.py`, `.env.example`, `requirements.txt`, `README.md`, `tests/test_proxy.py`, `tests/test_dev.py`. 실제 `.env`는 사용자 로컬 파일로 Git 제외·보존.
- 생성 문서: 본 보고서, `LIVE_ROUTE_IMPLEMENTATION_PLAN.md`, `evidence/live-route/`의 집계·테스트 결과·화면. 기존 Phase 0~2B-1 문서/증거 변경 없음.
- 종료 Git 상태: 기존 파일 6개 수정·신규 24개. `git diff --check` 및 신규 텍스트 줄 끝 공백 검사 통과. 설치/검증한 최종 APK SHA-256이 기록된 증거와 일치한다.

READY는 **실제 편도·복귀 요청과 Android 로컬 연동 성공**을 기준으로 한 판정이다. 독립 안내·차량 진입점/주차/상업적 승인·운영 프록시는 다음 단계의 미완료 항목이다.
