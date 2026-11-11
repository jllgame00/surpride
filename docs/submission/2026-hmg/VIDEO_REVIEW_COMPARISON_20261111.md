# SURPRIDE 예선 소개 영상 출처 검토와 재제작 비교

2026년 11월 11일. 기존 영상에서 **Phase 5A 이전 UI 2장면, 총 24초**를 확인했다. 기존 앱 화면 7장면 중 나머지 5장면은 Phase 5C이며, Phase 5B 또는 현재 빌드에서 새로 캡처한 화면은 없었다. 원본을 보존하고 **현재 빌드 새 캡처 65초 + Phase 5C 검증 화면 20초 + 설명 그래픽 25초**로 별도 110초 영상을 제작했다.

- [새 110초 MP4](assets/SURPRIDE_preliminary_latestUI_110s.mp4) · [자막 SRT](assets/SURPRIDE_preliminary_latestUI_110s.srt) · [전체 장면 미리보기](assets/review_20261111/latest-contact-sheet.jpg)
- [기존 MP4](assets/SURPRIDE_preliminary_prototype_110s.mp4) · [동일 바이트 백업](assets/review_20261111/backup/SURPRIDE_preliminary_prototype_110s.mp4) · [기존 장면 미리보기](assets/review_20261111/original-contact-sheet.jpg)
- [새 영상 출처 원장](assets/review_20261111/latest-video-provenance.json) · [기존 장면 대조 결과](assets/review_20261111/original-scene-audit.json) · [전체 화면 파일 원장](assets/review_20261111/screen-source-inventory.json) · [최종 검증 결과](assets/review_20261111/verification.json)

새 영상은 **무음·자막·실제 앱 정지 화면의 편집본**이다. 버튼 조작 녹화나 한 세션의 추천→주행→도착 연속 완주 영상이 아니다. 현재 Host ACTIVE가 BLOCKED인 상태를 유지하며, 성공한 활성 안내·실차 도착·STT/TTS 장면을 새로 만들지 않았다.

## 화면 세대 판정 기준

| 구분 | 근거와 파일 집합 | 기존 영상 | 새 영상 |
|---|---|---:|---:|
| Phase 5A 이전 | `docs/phase5/evidence/before/` 17장. Phase 5A에서 수집한 기존 UI이며 5A 디자인 구현본이 아님 | 2장면 / 24초 | 없음 |
| Phase 5B | `docs/phase5/evidence/after/` PNG 69장. [5B 구현 보고서](../../phase5/UI_UX_IMPLEMENTATION.md)와 대응 | 없음 | 없음 |
| Phase 5C | `docs/phase5/evidence/final/` PNG 86장. 초기 중단 기록 9장과 재개 후 검증 화면을 모두 포함 | 5장면 / 60초 | 2장면 / 20초 |
| 현재 빌드 | 이번 정차 Pleos에서 캡처한 `current-01`부터 `current-07`까지 7장 | 없음 | 7장면 / 65초 |
| 설명 그래픽 | 실제 앱 UI와 구분한 제작 그래픽 | 2장면 / 실측 25.95초 | 3장면 / 25초 |

폴더 이름만으로 최신 성공 화면을 판단하지 않았다. [5C 최종 보고서](../../phase5/UI_UX_FINAL_VALIDATION.md), 기존 영상의 [스토리보드](DEMO_STORYBOARD.md)·[출처 원장](evidence/video-provenance.json), 원본 SHA-256, 실제 인코딩 장면을 대조했다. 기존 제작 파일 `.gradle/phase6-c/make_video.py`, `video_story.json`, `video/scene-01.png`부터 `scene-09.png`도 읽기 전용으로 확인했다. 기존 9개 장면의 디코딩 프레임과 당시 제작 프레임 PSNR은 39.90~41.84dB였다. 6개 고유 앱 원본의 해시는 기존 출처 원장과 모두 일치한다.

Phase 5C 화면은 현재 시점의 과거 검증 자료다. 화면 디자인이 유사해도 이번 실행 화면으로 바꾸어 부르지 않았다. 현재 기능·Host 제약은 [Phase 6 B3](../../phase6/B3_HOST_LIFECYCLE_VOICE.md), [B6](../../phase6/B6_TOOLING_CLEANUP.md)의 제한과 함께 해석한다.

## 기존 영상 장면별 출처와 교체 판단

아래 타임코드는 시작 포함·종료 제외다. 파일명은 제목과 달리 실측 **109.950초 / 2,199프레임 / 20fps**다. 마지막 계획 구간 01:38–01:50은 실제로 01:49.950에서 끝난다.

| 장면 | 기존 타임코드 | 실제 원본 파일 | 판정 | 새 영상 반영 |
|---|---|---|---|---|
| 1 | 00:00–00:08 | [F14-start-final.png](../../phase5/evidence/final/F14-start-final.png) | Phase 5C 시작 | 브랜드 타이틀을 분리하고 00:07–00:15 현재 시작 화면으로 교체 |
| 2 | 00:08–00:20 | [F16-conditions-final.png](../../phase5/evidence/final/F16-conditions-final.png) | Phase 5C 조건 | 00:24–00:36 현재 조건 화면으로 교체 |
| 3 | 00:20–00:34 | [F25-natural-corrected-dark.png](../../phase5/evidence/final/F25-natural-corrected-dark.png) | Phase 5C 규칙 해석 | 00:36–00:54 현재 입력·실제 해석 결과로 교체 |
| 4 | 00:34–00:44 | [F16-conditions-final.png](../../phase5/evidence/final/F16-conditions-final.png) 재사용 | Phase 5C 조건 반복 | 중복 장면을 통합. 현재 홈·입력 화면에 시간을 배분 |
| 5 | 00:44–01:00 | [F28-real-roundtrip-ready.png](../../phase5/evidence/final/F28-real-roundtrip-ready.png) | Phase 5C 실제 API 준비 | 00:54–01:05에 유지. 과거 실제 API 준비 기록으로 명시 |
| 6 | 01:00–01:14 | 기존 설명 그래픽 `video/scene-06.png` | 앱 화면 아님 | 01:05–01:13 무채색 구조 그래픽. 현재 Host 활성 안내 보류 표시 |
| 7 | 01:14–01:26 | [16-arrival-reveal-rating.png](../../phase5/evidence/before/16-arrival-reveal-rating.png) | **Phase 5A 이전 DEMO UI** | 01:13–01:22 Phase 5C `F64` 실제 경로 체험 평가 UI로 교체 |
| 8 | 01:26–01:38 | [12-mood-map.png](../../phase5/evidence/before/12-mood-map.png) | **Phase 5A 이전 DEMO 기분 지도** | 01:22–01:40 현재 기분 기록·상세로 교체 |
| 9 | 01:38–01:49.950 | 기존 계획 그래픽 `video/scene-09.png` | 앱 화면 아님 | 01:40–01:50 새 마무리 그래픽과 검증 계획 |

기존 앱 화면의 발췌 좌표는 2560×1440 원본에서 **[853, 91, 2560, 1303]**이다. 우측 시스템 핸들과 앱 외부 일부가 포함된다. 새 영상은 실제 앱 영역 **[853, 90, 2535, 1300]**을 기준으로 사용한다. `F16` 반복을 별개 원본으로 세지 않았으며, 기존 영상에 5B 또는 현재 빌드 화면이 있었다고 분류하지 않았다.

과거 `F28`은 최신 API 성공 장면으로 교체하지 않았다. 이번 작업에서는 프록시를 가동하거나 API를 재요청하지 않았고 활성 안내도 시작하지 않았다. 따라서 현재 성공을 새로 주장할 근거가 없다. `F64` 역시 과거 정차·GPS 재생 검증에서 얻은 UI이며 현재 완주·실도로 방문 증거로 사용하지 않는다.

## 현재 빌드 캡처와 실행 범위

기존 `emulator-5554`의 Pleos Connect / `Connect_on_x86_64_emulator` / Android 사용자 10을 사용했다. 원본 2560×1440 캡처의 계기 영역에서 **P·0km/h**, 앱에서 **정차 중 이용**을 확인했다. 새 AVD 생성·설치·빌드·GPS 이동·주행 재생·기록 저장·테마 변경을 하지 않았다.

설치 APK를 읽어 해시를 계산하고 로컬 최신 Debug 산출물 `app/build/intermediates/apk/debug/app-debug.apk`와 일치를 확인했다. 버전은 `versionName=1.0`, `versionCode=1`이며 이 버전 문자열만으로 최신 여부를 판단하지 않았다.

**설치본 및 로컬 Debug APK SHA-256:** `b26e9a35877bb7d82e3623dd7bf9aa58a1fdeee40c4e66390bb9fa3f52293242`

| 새 캡처 | 실제 관측 | 사용 시 주의 |
|---|---|---|
| [current-01-launch.png](assets/review_20261111/current-01-launch.png) | 현재 브랜드 시작 화면 | 앱의 오프화이트·녹색·라임을 원래 그대로 유지 |
| [current-02-home.png](assets/review_20261111/current-02-home.png) | 홈, 조건·문장·실제 경로 준비 진입 | 준비 진입 버튼이 있다는 사실과 실제 경로 성공을 구분 |
| [current-03-conditions.png](assets/review_20261111/current-03-conditions.png) | 조건 선택 1/3 및 요약 | 나머지 단계는 영상에서 생략했다고 표시 |
| [current-04-natural-input.png](assets/review_20261111/current-04-natural-input.png) | 입력·규칙 해석·검토·닫기 UI | 빈 입력 상태. STT 또는 해석 성공 장면이 아님 |
| [current-07-natural-interpreted.png](assets/review_20261111/current-07-natural-interpreted.png) | 내장 개발용 텍스트 예문을 실제 해석. 힐링·90분·왕복·물가 선호 정리 | 앱의 “텍스트 예시 넣기 · 음성 인식 아님”을 사용. 음성·LLM 성공으로 표시하지 않음. 조건 적용 없이 닫음 |
| [current-05-mood-records.png](assets/review_20261111/current-05-mood-records.png) | 여섯 기분의 DEMO 개념도와 별도 실제 경로 체험 기록 | 원래 있던 개발 시험 기록. 이번에 방문·평가를 추가하지 않음 |
| [current-06-record-detail.png](assets/review_20261111/current-06-record-detail.png) | 기존 3/5 기록의 상세 대화상자 | 에뮬레이터 체험·실제 방문 인증 아님. 기분·이동 시간을 이 LIVE 기록에 저장하지 않았다는 원문 유지 |

캡처 중 일시적인 `input` 서비스 오류와 시스템 부팅 화면을 관측했으나 원인은 확정하지 않았다. 부팅 화면은 사용하지 않았고, 복구 후 정차 화면과 앱을 다시 확인했다. 앱 내부 비공개 Activity 직접 진입은 거부되어 정상 메인 진입과 화면 버튼을 사용했으며 권한을 우회하지 않았다. 마지막에는 입력을 적용하지 않고 닫고 시작 화면으로 돌아갔다. 이 과정 전체를 안정적인 단일 연속 실행 증거로 제시하지 않는다.

## Phase 5C 최종 증거 86장 활용

86장 모두 PNG 무결성·크기·SHA-256을 확인하고, 앱 영역 연락표로 시각 검토했다. [5C 최종 검증 보고서](../../phase5/UI_UX_FINAL_VALIDATION.md)의 의미와 대조했으며 번호를 합격 순서로 해석하지 않았다.

- [1–24장 인덱스](assets/review_20261111/phase5c-index-1.jpg): 초기 9장, 입력·IME·큰 글자·시작·조건
- [25–48장 인덱스](assets/review_20261111/phase5c-index-2.jpg): 해석·준비·지도·권한·공개·GPS
- [49–72장 인덱스](assets/review_20261111/phase5c-index-3.jpg): 재탐색·실패 종료·도착·평가·복귀 준비
- [73–86장 인덱스](assets/review_20261111/phase5c-index-4.jpg): 복귀 복구·완료·기분 기록·복원

최종 사용은 `F28-real-roundtrip-ready.png`와 `F64-live-rating-unselected.png` 두 장이다. `F25`, `F79`, `F80`도 교체 후보로 확인했지만 현재 빌드에서 더 최근의 실제 화면을 확보했으므로 새 캡처를 우선했다. `F42` 활성 지도·`F48` 재탐색·`F78` 복귀 완료 등은 당시 제한을 갖는 검증 기록이며 현재 Host ACTIVE 성공으로 오해할 장면은 넣지 않았다. 전체 파일별 분류·원본 해시·기존/새 영상 사용 장면은 [화면 파일 원장](assets/review_20261111/screen-source-inventory.json)에 있다. 연락표 검토는 86장의 모든 작은 글자에 대한 OCR·개인정보 전수 감사를 뜻하지 않는다.

## 새 영상 타임코드별 출처

| 장면 | 타임코드 | 내용 | 원본 파일과 세대 |
|---|---|---|---|
| 1 | 00:00–00:07 | SURPRIDE 브랜드 소개 | 신규 블랙·차콜·오프화이트 타이틀 그래픽. 앱 UI 아님 |
| 2 | 00:07–00:15 | 기분 따라 조금 낯선 곳으로 | [current-01-launch.png](assets/review_20261111/current-01-launch.png) · 현재 빌드 |
| 3 | 00:15–00:24 | 출발 준비와 입력 선택 | [current-02-home.png](assets/review_20261111/current-02-home.png) · 현재 빌드 |
| 4 | 00:24–00:36 | 기분·취향 조건과 시간·복귀 요약 | [current-03-conditions.png](assets/review_20261111/current-03-conditions.png) · 현재 빌드 |
| 5 | 00:36–00:46 | 문장으로 조건 정리 | [current-04-natural-input.png](assets/review_20261111/current-04-natural-input.png) · 현재 빌드 |
| 6 | 00:46–00:54 | 실제 규칙 해석 결과 | [current-07-natural-interpreted.png](assets/review_20261111/current-07-natural-interpreted.png) · 현재 빌드, 내장 텍스트 예문 |
| 7 | 00:54–01:05 | 비밀 코스의 실제 도로 준비 | [F28-real-roundtrip-ready.png](../../phase5/evidence/final/F28-real-roundtrip-ready.png) · **과거 Phase 5C API 기록** |
| 8 | 01:05–01:13 | 조건→경로→지도·GPS·안내 구조 | 신규 설명 그래픽. 현재 Host ACTIVE BLOCKED. 성공한 안내 재현 아님 |
| 9 | 01:13–01:22 | 공개 후 만족도 선택 | [F64-live-rating-unselected.png](../../phase5/evidence/final/F64-live-rating-unselected.png) · **과거 Phase 5C 정차 평가 UI**, 평가 미선택 |
| 10 | 01:22–01:33 | 기분별 기록과 체험 기록 구분 | [current-05-mood-records.png](assets/review_20261111/current-05-mood-records.png) · 현재 빌드 |
| 11 | 01:33–01:40 | 기록의 상세·한계 확인 | [current-06-record-detail.png](assets/review_20261111/current-06-record-detail.png) · 현재 빌드 |
| 12 | 01:40–01:50 | 다음 검증과 마무리 | 신규 브랜드·계획 그래픽. 실차·상용 운영 미검증 |

모든 앱 장면은 원본 **2560×1440에서 [853, 90, 2535, 1300]**만 발췌하고 가로세로 비율을 유지해 **1118×804**로 축소했다. 실제 삽입 좌표와 원본 해시는 [새 영상 출처 원장](assets/review_20261111/latest-video-provenance.json)에 있다. 앱 영역 안에는 자막·가짜 컨트롤·마스킹을 덮지 않았고, 재색칠·명암 보정·상태 조작·선택 합성·포인터 애니메이션을 하지 않았다. 외부 설명 영역만 무채색 미니멀 스타일이다.

## 화질과 자막 및 장면 전환 검증

| 항목 | 검증 내용 |
|---|---|
| 파일 형식 | 1920×1080, H.264, YUV420p, 30fps, faststart MP4, 무음 |
| 정확한 길이 | 110.000초, 디코딩 3,300프레임. 마지막 01:50까지 유지 |
| 전체 디코딩 | FFmpeg 전체 디코딩 오류 0 |
| 장면 전환 | 00:07 / 00:15 / 00:24 / 00:36 / 00:46 / 00:54 / 01:05 / 01:13 / 01:22 / 01:33 / 01:40의 직접 전환. 앱 상태를 섞는 디졸브 없음 |
| 프레임 배치 | 3,300프레임을 예정 장면과 대조해 장면 불일치 0. 다른 장면·검은 공백·전환 누락 없음 |
| 화면 잘림 | 앱의 실제 보이는 영역 전체를 비율 유지해 삽입. 제목·설명·출처·자막을 앱 밖에 배치 |
| 자막 | 한글 자막을 영상에 직접 삽입하고 같은 타임코드의 UTF-8 SRT도 제공. 12개 자막의 문구·구간을 출처 원장과 일치 검사. 화면 안 64px 좌우 여백 유지 |
| 글자 경계 | 제목·본문·출처·자막의 텍스트 경계 130개를 안전 영역 안에 배치하고 실제 인코딩 장면을 시각 검토 |
| 원본 픽셀 | 손실 없는 제작 프레임의 앱 영역이 원본 crop·비율 축소 결과와 정확히 일치하는지 검사 |
| 압축 품질 | 최종 인코딩 각 장면을 손실 없는 제작 프레임과 비교한 PSNR 42.21~47.28dB. 개별 수치는 [검증 결과](assets/review_20261111/verification.json) 참조 |
| 보고서 링크 | 상대 링크 47개 검사, 끊긴 링크 0 |

기록 목록 하단처럼 원래 앱의 스크롤 영역 밖인 내용은 새로 합성하거나 펼치지 않았다. 원본 앱의 작은 보조 글자는 1080p 편집에서 약 2/3로 줄어든다. 핵심 소개·한계는 외부 설명과 자막으로 읽을 수 있게 했으며, 모든 보조 글자가 작은 모바일 화면에서도 읽힌다는 보장은 하지 않는다.

## 보존과 변경 범위

| 항목 | 결과 |
|---|---|
| 기존 MP4 SHA-256 | `b8c08955be85e987530d6ec0b456e1a5148b962da585aef50a3d846ebd543a92` |
| 새 MP4 SHA-256 | `5f972084aff027fe118401a32e2bbe92e4bed124dc9333cc22299ccd824b6386` |
| 백업 | 기존 MP4·SRT·출처 JSON을 `assets/review_20261111/backup/`에 복사. 원래 위치도 유지하며 바이트 일치 검사 |
| Git | 시작 HEAD `40ca62877e60deb353e476648c9b08ab558a90fc`. 읽기 전용 상태·HEAD 확인만 수행. 커밋·Push·stage·브랜치 변경 없음 |
| 제품 소스 | `app/src`, `nav-host-poc/src`, `backend`, `scripts`의 보호 대상 1,327파일 해시 비교. 변경·삭제 없음 |
| 산출물 범위 | 새 영상·SRT·비교 보고서와 그 근거인 캡처·백업·출처·검증 파일. 작업용 렌더와 검사 스크립트는 `.gradle/video-review-20261111/`에만 저장 |

기존 제출 스토리보드·매니페스트를 새 영상 경로로 덮어쓰지 않았다. 제출할 때는 새 MP4를 명시적으로 선택해야 한다. 외부 업로드·공개 링크 생성·실차 주행·현재 Host 활성 안내·음성 실청취·앱 전체 회귀 검사는 이번 영상 제작 범위에서 수행하지 않았다.
