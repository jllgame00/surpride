# Phase 2B-1.5 구현 계획

사용자 명세에 따라 현재 프로젝트 안에서 실행한다. 시작 HEAD `31fb37fd18355a61013fccaaa7ccec536bef6939`, clean. 기존 SDK/PoC/78개 테스트와 DEMO를 보존하고 커밋·Push하지 않는다.

설계: loopback FastAPI 서버가 환경변수의 공급자 키를 보관하고 공식 Directions HTTPS만 호출한다. POST 경로 요청에는 숫자 좌표만 사용한다. 인증/호출 제한/타임아웃/응답 상한/오류 정규화를 적용하고 데이터·키·응답 전문을 로깅하지 않는다. 서버가 발급한 단기 개발 토큰은 Git 제외 로컬 설정 및 디버그 앱 비공개 캐시에만 전달한다. 공개 서버/클라우드는 만들지 않는다.

Android는 기존 RouteProvider 계약에 LiveRouteProvider를 추가한다. 서버 응답의 source·시각·요청 지점 일치를 검증하고 원래 KakaoRouteParser와 시간 평가기를 재사용한다. 개발 HTTP는 debug에서 에뮬레이터 호스트에만 허용한다. UI는 작업 스레드로 조회하고 화면 이탈/조건 변경/주행 제한 시 결과를 폐기한다. Mock 서버 응답은 네트워크를 통과해도 MOCK이다.

- [x] FastAPI 오류/인증/제한/계약/왕복 테스트 RED→GREEN.
- [x] 최소 서버·실행/디버그 프로비저닝 도구·환경 예제 구현.
- [x] Android transport/응답 파서 테스트 RED→GREEN 및 비동기 화면 연결.
- [x] backend 테스트와 기존 전체 Gradle 테스트/빌드.
- [x] Pleos 설치·로컬 서버 미연결/키 없음/Mock 서버 연결·목적지 비공개 확인.
- [x] A(Mock), B(실제 공급자), C(Pleos), D(차후 안내) 증거와 제한 문서화 및 읽기 전용 검토.

초기 프로세스 환경변수에 KAKAO_REST_API_KEY가 없었으나, 진행 중 Git 제외 backend/.env의 키 구성을 확인했다. 사용자 로컬 파일을 수정/출력하지 않고 실제 공개 지점 편도·복귀 및 Pleos LIVE_API 추천을 검증했다. 최종 READY(로컬 데이터 연동 범위). 대표 좌표는 차량 진입점으로 승격하지 않는다. `.env.example`만 추적하며 실제 `.env`와 실행 토큰은 추적하지 않는다.

검토 기록: HTTP400 공식 오류 코드 및 run-as 인자 순서/바이트 검증, 64초 순차 요청의 신선도 기준과 최종 귀가 마감 재검사를 보완했다. Android89/Backend29 통과. 실제 호출과 Mock/HTTP Mock을 구분한 증거는 LIVE_ROUTE_API_VALIDATION.md 참조.
