# Phase 6B-5 — 성능·캐시 수명·개인정보 보존

판정: **READY_WITH_LIMITATIONS**. A09–A12의 코드 개선과 계약 회귀를 완료했다. 실제 Host ACTIVE, 실제 차량·실음향·장시간 안정성은 검증하지 않았다.

| 별도 판정 | 결과 | 증거 범위 |
|---|---|---|
| PERFORMANCE | MEASURED_IMPROVEMENT | 호스트 JVM 합성 경로. Android 프레임·메인 스레드 개선 인증 아님 |
| CACHE_POLICY | PARTIAL | 공식 정책 검토·헤더/메모리/디스크/종료 fixture 통과. 실제 OSM 응답·단말 HTTP 종료·장기 사용량 미검증 |
| DATA_RETENTION | PARTIAL | 파일 삭제·원자적 축소·nonce/TTL·실패 계약 검증. 프로세스 부재 중 즉시 삭제와 전체 저장 암호화는 보장하지 않음 |
| HOST_ACTIVE | BLOCKED | A04 기존 제약 유지, 활성 안내 시도 없음 |
| DESTINATION_PRIVACY | PARTIAL | 기존 지도 POI·Host 등 외부 표면 제한 유지 |

## 1. 기준과 Git 보존

- 시작/종료 기준: `main`, `9f63500a81dc1337128b2d639c3660ec1e6f7e6f`. 시작 작업 트리는 clean이었다. 사용자 메시지의 과거 `765b894`를 현재 HEAD로 가정하지 않았다.
- B1 도로별 거리·geometry, B2 AtomicFile·terminal 복원, B3 lifecycle/watchdog, B4 추천·LIVE 이력이 실제 현재 코드에 반영되어 있었다. 관련 감사·계획·위험 등록부 및 B1–B4 보고서를 확인했다.
- 기존 추적 테스트 파일 77개의 시작 SHA-256을 대조했다. 기존 테스트 변경·삭제·skip 없음. 제품 코드 11개·감사 이력 문서 3개가 수정 상태이고 신규 테스트/비교 helper 7개를 남겼다. HEAD·이력·커밋·Push 변경 없음.
- `.gitignore`의 기존 외부 변경을 포함한 시작 bytes를 보존했다. 해당 파일을 수정하지 않았다. 변경 범위·보존 근거는 [source-preservation.json](evidence/b5/source-preservation.json)에 있다.
- 새 보고서와 증거는 **`.gitignore:59: /docs/phase*/`**에 가려진다. 기존 감사 문서 3개는 이미 추적 중이므로 후속 추가가 status에 보인다. 새 문서는 `git status`만으로 누락 여부를 판단하면 안 된다. 강제 add·ignore 수정·스테이징을 수행하지 않았다. 신규 소스 테스트 7개는 ignore되지 않는다.

## 2. A09 측정 방법

`RoutePerformanceTest`에서 1천/1만/5만/10만 점 경로를 생성했다. 각 크기·연산마다 30회 warm-up 후 100회 측정했다. p50/p95/p99는 정렬된 표본의 49/94/98 인덱스, 최악값은 최대 표본이다. `System.nanoTime`, 호스트 `ThreadMXBean` 할당량, JVM GC collection 수를 사용했다. 좌표는 0도 부근 합성 자료로 사용자 위치가 아니다. 네트워크·OSM 요청은 없다.

`RouteMatcher.accept`, `FollowCamera.update`, matcher 생성 비용을 따로 측정했다. 경로 생성·GPS fixture 생성은 측정 구간 밖이다. GC 횟수는 JVM 관측치이며 특정 메서드가 모든 GC를 유발했다는 뜻이 아니다. 단일 실행의 표본으로 통계적 신뢰구간·기기 간 성능을 주장하지 않는다. 최초 측정본을 고정하여 후속 전체 테스트가 덮어쓰는 `route-current.json`과 구분했다.

### GPS 처리 전후

시간 단위 ms. 각 셀은 **p50 / p95 / p99 / 최악**이다.

| 점 수 | 변경 전 matcher | 변경 후 matcher | 할당 B/회 전 → 후 | GC 전 → 후 |
|---:|---|---|---:|---:|
| 1,000 | 0.1542 / 0.2730 / 0.3314 / 0.3436 | 0.1102 / 0.2187 / 0.2433 / 0.2718 | 97,166 → 58,804 | 0 → 0 |
| 10,000 | 0.8295 / 1.0345 / 2.0159 / 2.2431 | 0.5382 / 0.6694 / 1.4057 / 1.5419 | 980,403 → 572,329 | 4 → 2 |
| 50,000 | 3.0997 / 4.0078 / 4.9197 / 5.3910 | 1.6411 / 2.0710 / 2.2131 / 2.2282 | 4,910,631 → 2,855,789 | 3 → 1 |
| 100,000 | 6.0842 / 7.1274 / 8.0736 / 8.2956 | 2.8012 / 3.7610 / 5.2030 / 6.4874 | 8,964,349 → 5,282,645 | 5 → 3 |

10만 점에서 p50은 약 54%, 할당량은 약 41% 감소했다. 최악값 개선도 이 JVM 표본에 한정한다.

### FollowCamera 전후

| 점 수 | 변경 전 camera | 변경 후 camera | 할당 B/회 전/후 |
|---:|---|---|---:|
| 1,000 | 0.0054 / 0.0215 / 0.0480 / 0.0602 | 0.0053 / 0.0313 / 0.0544 / 0.0688 | 941 / 941 |
| 10,000 | 0.0529 / 0.0858 / 0.1373 / 0.1431 | 0.0067 / 0.0462 / 0.0568 / 0.0718 | 2,277 / 2,277 |
| 50,000 | 0.3360 / 0.4137 / 0.4305 / 0.5482 | 0.0074 / 0.0287 / 0.0480 / 0.0838 | 3,198 / 3,198 |
| 100,000 | 1.1973 / 1.3472 / 1.4328 / 2.2799 | 0.0056 / 0.0073 / 0.0111 / 0.0136 | 5,141 / 5,141 |

1천 점의 camera tail은 소폭 악화했다. 모든 크기·모든 지표의 개선이라고 표현하지 않는다.

### 비용을 옮긴 부분과 변경 코드

- `GuidanceModels.kt`: `RouteGeometry.atFraction`은 기존 누적 배열의 lower-bound 이진 탐색, `roadIndexAtGeometry`는 기존 epsilon을 유지한 upper-bound 탐색이다.
- `RouteMatcher.kt`: 선분의 **원래 haversine 계산값**을 생성 시 한 번 보관한다. 누적 거리 차를 사용해 반올림 계약을 바꾸지 않았다. `zipWithNext` Pair 목록과 전체 정렬을 제거하고 안정적인 첫 최솟값과 전체 ambiguity 탐색을 사용한다.
- 새 matcher 생성 p50은 1천/1만/5만/10만 점에서 0.0384/0.2985/1.5062/3.1827ms, 할당량은 8,974/80,253/400,253/800,253B다. 기존 생성은 약 0.0001–0.0004ms였다. 경로당 O(n) 준비 비용과 선분당 Double 저장 비용이 늘었다. 전체 원자료에 생성 p95/p99/최악값도 포함했다.
- GPS마다 **전체 선분 탐색은 유지**한다. 공간 인덱스·검색 창 제한·GPS 빈도 변경·비동기 프레임워크를 도입하지 않았다. heading/accuracy/reverse/jump/ambiguity/재획득/이탈 안전 임계값은 변경하지 않았다.

변경 전 matcher를 테스트 전용 `LegacyRouteMatcher`로 고정했다. 직선·자기 교차·손실·재획득·역방향·jump·heading 시퀀스 322개와 각 크기의 동일 GPS 130개씩 520개에서 `MatchState` 전체의 정확한 동등성을 검사한다. 3개 road의 비균일 거리 경계와 fraction 1,001개도 기존 선형 계산과 비교한다. 기존 A01/A02 회전/TTS/Camera/도착 회귀를 유지했다.

Android main thread trace, ART allocation, PSS·전력·장시간 GPS 부하, ANR 인과관계는 측정하지 않았다. 이 변경을 B2/B3 FocusEvent ANR 해결로 간주하지 않는다. [변경 전](evidence/b5/route-before.json), [변경 후](evidence/b5/route-after.json).

## 3. A10 OSM 정책과 캐시

공식 정책의 HTTPS 타일 URL, 식별 가능한 User-Agent, 출처 표시, 서버 캐시 지시 우선, 조건부 재검증 및 헤더를 해석할 수 없는 경우의 최소 7일 캐시를 확인했다. 대량 다운로드·미리 받기·오프라인 팩을 만들지 않았다. [OSM 공식 타일 정책](https://operations.osmfoundation.org/policies/tiles/).

| 항목 | 구현·검증 |
|---|---|
| URL·식별·출처 | 기존 `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, `SURPRIDE/0.1` 및 프로젝트 URL User-Agent, OSM/ODbL 출처 표시 보존. redirect 금지 |
| 서버 신선도 | max-age/Age·Expires 처리, no-cache 즉시 재검증, 헤더 없는 경우 7일. 304는 이전 Cache-Control/Expires/validator를 병합하고 200 새 응답은 이전 validator를 상속하지 않음 |
| 조건부 요청 | ETag/Last-Modified를 저장해 만료 후 If-None-Match/If-Modified-Since 전송. 304는 기존 bytes를 사용함 |
| no-store | 이전 디스크 body/meta/temp 제거. 디코딩 결과는 현재 화면의 1회 소비 버퍼로만 쓰고 viewport 이탈 시 폐기함 |
| 메모리 신선도 | 성공한 decode도 HTTP 만료를 따름. no-cache/must-revalidate는 검증된 응답의 최초 paint만 허용하고 다음 만료 재사용은 차단. 강제 재검증 실패 시 stale bytes를 제공하지 않음 |
| 일반 네트워크 오류 | 허용 가능한 stale tile은 명시적 stale/failure 상태. 재시도 30초 backoff. 디코딩 실패는 긴 HTTP TTL과 관계없이 실패 backoff를 따름 |
| 반복 요청 방지 | 즉시 만료 응답의 완료 repaint가 재요청을 계속 만들던 루프를 차단. 실제 renderer callback→Canvas render→want 회귀 추가 |
| 디스크 정리 | 시간당 기회적 검사. 서버 만료 타일 중 30일 지난 항목 삭제 또는 64MiB soft 목표를 초과하면 만료된 항목부터 정리. 신선한 타일은 목표 크기를 맞추기 위해 버리지 않음. 오래된 orphan body/temp는 7일 이후 정리 |
| viewport·종료 | 기존 2 worker·viewport 시작 전 확인 유지. close 시 queued work·pending·decode 목록 폐기, transport close 1회, 늦은 worker 결과 발행 금지. 진행 중 연결 disconnect는 별도 daemon으로 수행해 Host main callback을 막지 않음 |

디스크 정리 lock과 종료 전이 lock을 분리했다. 디렉터리 I/O 중 close가 해당 lock을 기다리지 않는 회귀가 통과했다. 캐시가 모두 서버 신선 상태라면 **64MiB를 초과할 수 있다**. 캐시 TTL은 위치 유추 위험과 서버 재요청 절감 사이의 절충이며 지도 이력을 완전히 지우는 정책은 아니다. 손상된 `.meta`의 정리와 실패한 unlink의 자동 재시도/운영 관측은 추가 보강 여지가 있다.

검증은 합성 HTTP 응답·Robolectric renderer와 실제 Android provider/저장 경계를 구분했다. 실제 OSM 타일을 B5에서 요청하지 않았다. 단말 DNS·native socket 종료의 유한 시간, 장기 disk 크기·실제 트래픽은 미검증이라 CACHE_POLICY는 PARTIAL로 남긴다.

## 4. A11 저장 항목·보존 정책

| 항목·위치 | 데이터·보호 | 수명·정리·필요한 보존 |
|---|---|---|
| 메인 `cache/prepared_routes/UUID.json` | 목적지·원출발지·geometry·단기 proxy 인증정보. 앱 private, 평문, cache는 Android backup 대상 아님 | 최대 기존 1시간. 유효 pending nonce/candidate/prepared 시각과 일치할 때만 읽음. FD 1회 open 시 unlink+URI grant revoke. 취소·평가 완료·다른 pending 세션 준비·만료 재진입에서도 정리 |
| PoC `cache/route-guidance.json`와 AtomicFile sidecar | 동일 민감 route·인증정보·explicit outcome. 앱 private 평문. PoC allowBackup=false | 기존 measured/prepared/packet/terminal TTL의 최솟값 유지. onStop에서 삭제하지 않음. 만료 load는 base/bak/new 삭제, 최초 commit 이전 abandoned new도 제거. 손상 JSON은 거부하고 오래된 손상 파일은 삭제 |
| PoC 복귀 완료 영수증 | source·candidate·nonce·기존 시각·명시적 공개/도착/복귀 완료와 label. geometry·좌표·인증정보·request waypoint 없음 | 기존 만료를 연장하지 않는 작은 원자적 receipt. 해당 완료 세션 복원은 가능하나 route/TBT/GPS/Focus/TTS 권한 없음 |
| 메인 `cache/route-proxy-session.json` | 개발용 단기 proxy token. debug만 사용, private/cache | load 시 만료·잘못된 설정을 거부하고 파일 삭제. release는 기존 비활성 유지 |
| `shared_prefs/live_navigation_emulator.xml` | 공개 후보 ID·평점·완료 시각·arrival 확인, opaque pending/completed nonce. **평문**, MODE_PRIVATE | 방문 30개 유지, B4 최근 방문 7일 판정 보존. pending/completed 영수증은 1시간 이후 read에서 제거. 평점·실제 방문 기록은 유지. cloud backup/device transfer 제외 유지 |
| DEMO `no_backup/surpride-demo-v1.bin` | 기존 Keystore AES-GCM 암호화 | 기존 DemoStateStore·호환·기록 보존. 변경 없음 |
| PoC `files/osm-viewport-tiles` | 타일 bytes·HTTP cache metadata, 타일 좌표로 방문 지역 유추 가능. private 평문, PoC backup 비활성 | 서버 TTL 우선 및 위 캐시 정리 정책. 정확한 GPS track을 새로 수집하지 않음 |

### 파일 삭제·접근 거부·실패를 구분

- `NavigationFileProvider`: 허용 경로 하나와 read-only 모드를 유지하고 canonical 경로·크기·JSON TTL·pending identity를 검사한다. 두 동시 open은 직렬화해 FD 하나만 성공한다. 이미 열린 descriptor는 unlink 후에도 읽을 수 있음을 실제 Android에서 확인했다. 원시 좌표·토큰을 외부 Intent extra에 넣지 않는다.
- 전달 파일은 PoC가 atomic copy를 완료하기 전이라도 FD 확보 시 소비된다. 이후 읽기/파싱/commit 실패는 자동 재사용하지 않고 SURPRIDE에서 명시적으로 재준비한다. 기존 정상 PoC packet은 AtomicFile 실패 계약으로 보호한다. 실제 앱 간 Binder/Host 전달의 전체 성공은 이 local provider 계측만으로 증명하지 않는다.
- 복귀 완료는 `rememberArrival`에서 좌표 없는 receipt로 **원자적 축소**한다. 쓰기 실패 시 기존 bytes·메모리 outcome 보존, 성공 후에만 in-memory route를 제거한다. 같은 nonce의 옛 handoff로 완료 경로를 되살릴 수 없다.
- 도착 후 복귀 준비에 필요한 원출발지는 기존 TTL까지 보존한다. 명시적 `안내 종료`는 세션 취소로 처리하여 현재 committed raw와 일치하는 packet만 삭제한다. lifecycle stop·Focus loss·Surface 재생성은 취소로 취급하지 않는다.
- 만료 proxy 인증정보는 정상 outcome hash를 갱신하면서 AtomicFile로 제거한다. cleanup 쓰기 실패는 정상 packet commit으로 표시하지 않고 재준비로 이어진다. 잘못된 nonce는 유효한 다른 packet을 삭제하지 않는다.
- unlink 실패 시 `PACKET_DELETE_FAILED` 등 유한 코드만 기록한다. 파일명·예외 상세·좌표·토큰은 로그에 넣지 않는다. 삭제 실패 회귀는 실제 파일이 남아 있음을 확인하면서 접근 거부와 실패를 검사한다. 성공한 삭제와 혼동하지 않는다.
- 프로세스가 실행되지 않으면 정확한 TTL 시각의 물리 삭제를 보장하지 않는다. 정리 trigger는 읽기·재진입·명시적 종료·준비·평가 완료다. WorkManager·상시 서비스·새 암호화 프레임워크는 추가하지 않았다. 파일 unlink는 flash 블록의 secure erase 보증도 아니다.

### LIVE 평문 저장 판단

LIVE journal에는 정확한 원출발지/GPS geometry/token/기분·음성 원문이 없다. 그러나 장소 ID·평가·시각은 방문 패턴을 유추하는 개인정보다. MODE_PRIVATE와 backup 제외는 암호화의 대체가 아니다. 기존 DEMO Keystore 설계를 확인했지만, 이번에는 기존 LIVE 기록을 삭제하거나 무근거로 새 형식으로 이관하지 않았다. 상용·공개 배포 전 Keystore 적용 필요성, 기존 평문 이관·키 손실·삭제 요청·보존 기간을 별도 결정해야 한다. 이 잔여 위험 때문에 DATA_RETENTION을 전체 VERIFIED로 승격하지 않는다.

## 5. A12 FastAPI HTTP client 수명

변경 전은 `fetch_route` 요청마다 AsyncClient 생성/종료였다. 새 client는 `create_app`의 lifespan에서 생성해 앱 수명 동안 재사용하고 shutdown 시 닫는다. DEMO·미설정 상태에는 불필요한 upstream client를 생성하지 않는다. lifespan이 없는 잘못된 진입은 요청별 client를 몰래 생성하는 fallback 대신 오류로 끝낸다.

| 8회 연속 요청, MockTransport | 변경 전 | 변경 후 |
|---|---:|---:|
| client 생성 | 8 | 1 |
| 요청 중 close | 8 | 0 |
| shutdown까지 총 close | 8 | 1 |

Mock 요청 지연 원표본도 [전](evidence/b5/server-before.json)·[후](evidence/b5/server-after.json)에 남겼다. 표본 8개와 메모리 transport로 TCP/TLS 연결 생성 절감량이나 실제 Kakao latency 향상을 주장하지 않는다. 측정상 확인한 것은 **client 자원 수명의 감소**다.

기존 connect 3초·HTTP 8초·전체 I/O 10초, redirect OFF, trust_env OFF, 고정 HTTPS upstream host, 응답 크기·입력 제한·인증·quota를 보존했다. transport 주입을 유지한다. ReadTimeout 후 다음 요청 성공, 요청 취소 후 다음 요청 성공, response header 수신 후 body 소비 중 취소 시 stream.close, shutdown 시 client.close를 별도 검사했다. 실제 원격 API 요청·배포는 하지 않았다.

## 6. 회귀와 실패 재현

| 검증 | 수정 전/중 실패 | 최종 결과 |
|---|---|---|
| A09 기준 측정·동등성 | 기존 matcher 기준 측정, 논리 오류 수정 대상 아님 | 동일 입력 결과 일치, 위 표의 성능 비교 |
| 최초 캐시 회귀 | 6개 중 6개 실패: no-cache, validator, 304 TTL, no-store 파일, old disk, decode memory 만료 | 통과 |
| 최초 PoC 보존 회귀 | 6개 중 3개 실패: 만료 파일 잔존, 인증정보 잔존, 완료 route 잔존 | 통과 |
| 메인 provider/삭제 계측 | 6개 중 5개 실패 | 통과 |
| 만료 opaque 영수증 | native 7개 중 추가 1개 실패 | read 정리 후 통과 |
| HTTP client 수명 | 신규 3개 실패, 8 client 생성 재현 | 신규 4개 포함 전체 106개 통과 |
| 별도 최종 리뷰 | Important 3개·Minor 1개 | 세 문제 RED 후 한 번의 수정 pass, 전체 회귀 통과 |

리뷰 실패는 긴 TTL decode 재시도 차단, 즉시 만료 repaint 루프, prune/close lock 공유였다. 처음에는 Robolectric이 불량 이미지도 기본 허용하여 decode 회귀의 입력이 실제 decoder 실패를 만들지 않았다. `ShadowBitmapFactory.setAllowInvalidImageData(false)`로 강화한 뒤 **수정 전 eligibility 조건에서 동일 테스트의 실패를 다시 확인**했다. 그 일시적인 소스 재현은 APK 설치 없이 수행하고 fixed source를 복원했다. 기존 테스트 기대값을 완화하지 않았다.

### 고유 검사 집계

| 영역 | 기존 | 신규 | 최종 PASS |
|---|---:|---:|---:|
| 메인 JVM | 231 | 0 | 231 |
| Navigation PoC JVM | 281 | 27 | 308 |
| FastAPI | 102 | 4 | 106 |
| Android 계측 | 앱 20 + PoC 3 | 앱 8 | 앱 28 + PoC 3 = 31 |
| **합계** | **637** | **39** | **676** |

기존 테스트 삭제·skip·비활성화·완화 없음. 재실행 횟수와 내부 GPS 표본을 고유 테스트 수에 더하지 않았다. 신규 frozen matcher helper 자체도 테스트 개수에 넣지 않았다. 기존 A01/A02/A03/A06/A07/A08/A14 및 ownership/watchdog 회귀가 포함된다.

JVM/빌드: `-PnavHostPoc=true :app:testDebugUnitTest :nav-host-poc:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :nav-host-poc:assembleDebug :nav-host-poc:assembleDebugAndroidTest --offline --no-daemon --console=plain -Pkotlin.compiler.execution.strategy=in-process`. 지정된 기존 GRADLE_USER_HOME/ANDROID_USER_HOME을 사용했다. 서버: `backend/.venv/Scripts/python.exe -m pytest backend/tests -q -p no:cacheprovider`.

SDK deprecated API·JDK native access 경고, 기존 Starlette/httpx deprecation 경고가 있다. 경고를 없애기 위한 의존성 변경은 하지 않았다. [JVM 집계](evidence/b5/jvm-counts.json), [검증 로그 요약](evidence/b5/verification.txt).

## 7. APK·정차 AVD·자료 보존

- 기존 `emulator-5554`, Pleos `crp_avd`, user 10만 사용했다. 기기 초기화·다른 serial 자동 선택·OEM 수정 없음.
- 두 메인 APK와 계측 APK를 빌드·설치했다. `outputs/apk` 파일의 SHA-256과 `pm path` 설치 APK bytes가 일치했다. 최신 PoC 변경 뒤 해당 APK를 다시 설치하고 계측 3개를 재실행했다.
- 메인 28개·PoC 3개 전체 계측 통과. 메인 전체 계측 두 실행은 44.81초와 195.75초, PoC는 1.19초와 1.39초였다. 지연 편차는 있으나 해당 실행은 둘 다 PASS다. 캐시 변경의 개선/악화로 원인을 확정하지 않는다.
- 두 앱의 정적 진입 `am start -W`는 Status ok. 실제 추천 조건/평가 재생성은 기존 Activity 계측의 MOCK 입력 범위다. 실제 Kakao 추천·지도 HTTP·GPS 활성 안내 E2E는 실행하지 않았다. ActivityManager 시작 성공을 실제 Host 조작/시각 승인으로 과장하지 않는다.
- 해당 최종 관찰 구간의 두 앱 `am_crash`/`am_anr` 이벤트는 0회였다. B3의 미확정 FocusEvent ANR 원인이 해소됐다는 뜻은 아니다. 실제 Focus 획득·반환 성공률·활성 TBT/TTS/종료 조작은 **BLOCKED** 그대로다.
- 실행 전 private 기록·설정·GPS를 임시 보존했다. DEMO 암호문·LIVE journal·appearance 3개 원본 bytes를 검증 복원했다. 원래부터 만료된 proxy credential/PoC packet 2개는 이번 허용된 정책에 따라 삭제하고 재존재하지 않음을 검사했다. GPS·location mode 복원, 테스트가 만든 고립 export 8개 정리. 사용자 기록·평가는 삭제하지 않았다.
- 검증용 private 원본 백업은 복원·노출 검사 뒤 제거했다. 공개 증거에는 원시 위치·token·private packet·GPS snapshot을 포함하지 않는다. [기기 증거](evidence/b5/device-final.json), [복원 요약](evidence/b5/restoration-summary.json), [민감값 검사](evidence/b5/privacy-check.json).

## 8. 잔여 위험·다음 단계

| 위험 | 남은 범위·다음 증거 |
|---|---|
| A09 장기 부하 | 전체 선분 O(n)과 후보 할당은 남아 있다. Android ART·메인 스레드/Surface/GPS가 섞인 장기 profile 필요. 이번에 공간 검색 안전 계약을 변경하지 않음 |
| A10 cache | 실제 서버 헤더·native transport 종료·장기 disk 증가·손상 metadata 정리/삭제 실패 재시도 관측 필요. fresh-only 집합은 soft cap 초과 가능 |
| A11 개인정보 | off-process 즉시 물리 삭제 없음. LIVE public 후보 기반 방문 정보 평문·기간 삭제 요청 정책, 단말 침해/flash 잔존·key migration은 미완료. URI FD를 이미 받은 소비자 접근을 나중에 소급 철회하는 보증 없음 |
| A12 운영 | 실제 TCP/TLS reuse·네트워크 취소/부하·다중 worker·상용 quota 운영 미측정. 새 외부 인프라를 넣지 않음 |
| Host/비공개 | A04 활성 종료 경로 미보장, DESTINATION_PRIVACY PARTIAL. 활성 Host 시연·실차 안전성 승격 금지 |

**6B-6/제출 준비 진입 가능:** 이번 변경·39개 신규 회귀·두 APK·ignore된 증거를 검토 가능한 기준으로 삼는다. 제출 전에는 문서/증거를 추적하는 방식을 사용자 저장소 정책에 따라 결정하고, 개인정보 보존의 남은 범위와 실제 Host 지원 범위를 명시해야 한다. Host ACTIVE 차단을 유지하는 정적·추천·계약 시연 준비는 가능하다. 완전 비공개·실차 내비·상용 운영·장시간 안정성 인증으로 발표할 수 없다.

기존 [감사](CODEBASE_AUDIT.md), [개선 계획](REFACTOR_PLAN.md), [위험 등록부](RELEASE_RISK_REGISTER.md)의 과거 판정을 덮어쓰지 않고 이 후속 결과를 연결했다.

미검증: 실제 Host ACTIVE·실제 타일/경로 네트워크·Android 장기 profile·실차·실음향. 잔여 개인정보 및 플랫폼 위험은 위 PARTIAL/BLOCKED 범위에 남아 있다.
