# 01: 내 설문 조회 API 구현

**What to build:** 로그인한 활동 멤버가 방에 제출한 자신의 설문 응답을 조회할 수 있습니다.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `GET /api/rooms/{roomId}/survey/me`를 추가하고 성공 응답은 HTTP 200, `SURVEY_FETCHED`와 공통 `ApiResponse` 형식을 사용한다.
- [ ] 응답 데이터에는 설문 ID, 예산, 숙박 유형, 여행 페이스, 선호 활동, 기피 메모, 제출 시각을 포함한다. 예산·숙박 유형·여행 페이스는 저장값이 없는 경우 `null`로 반환한다.
- [ ] 라벨은 기존 enum 라벨을 사용하고, 제출 시각은 오프셋 없는 한국 시간 기준 `LocalDateTime`으로 반환한다.
- [ ] 미인증·세션 만료는 401 `UNAUTHORIZED`, 숫자가 아닌 방 ID는 400 `INVALID_INPUT`, 없는 방은 404 `ROOM_NOT_FOUND`로 응답한다.
- [ ] 방 멤버 기록이 없거나 나간 멤버는 모두 403 `FORBIDDEN`으로 응답한다. 활동 멤버가 설문을 제출하지 않았다면 404 `SURVEY_NOT_FOUND`로 응답한다.
- [ ] 방 진행 상태나 여행 날짜와 관계없이 활동 멤버가 조회할 수 있으며, 로그인 사용자 본인의 설문만 반환한다.
- [ ] 실제 세션 인증과 Testcontainers MySQL을 사용하는 단일 HTTP 통합 테스트 경계에서 성공 응답, 응답 라벨·nullable 값, 본인 응답 범위와 정의된 오류 코드를 확인한다.
- [ ] 기존 DB 스키마를 재사용하며 마이그레이션은 추가하지 않는다.

## Comments
