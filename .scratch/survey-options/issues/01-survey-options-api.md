# 01: 설문 선택지 조회 API 구현

**What to build:** 로그인한 사용자가 설문 화면에 필요한 enum 선택지와 입력 한도를 방과 무관하게 조회할 수 있습니다.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `GET /api/survey-options`는 로그인 세션을 요구하고, 미인증 또는 세션 만료 요청에 HTTP 401 `UNAUTHORIZED`를 반환한다.
- [ ] 성공 요청은 HTTP 200, 공통 응답 `success: true`, code `SURVEY_OPTIONS_FETCHED`를 반환한다.
- [ ] 응답의 `data.stayTypes`, `data.paces`, `data.actPrefs`는 기존 enum의 `value`와 `label`을 선언 순서대로 제공한다.
- [ ] `data.limits`는 `budgetMin=1`, `budgetMax=99999999`, `actPrefsMin=1`, `actPrefsMax=3`, `avoidNoteMaxLength=100`을 포함한다.
- [ ] API는 방 ID나 방 멤버십에 의존하지 않고 DB 스키마 변경을 추가하지 않는다.
- [ ] HTTP 통합 테스트에서 로그인 및 비로그인 응답, enum 값·라벨·순서, 한도, 성공 code를 검증한다.

## Comments
