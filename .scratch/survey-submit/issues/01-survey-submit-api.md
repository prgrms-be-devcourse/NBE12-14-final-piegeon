# 01: 설문 제출 API 구현

**What to build:** 여행방 활동 멤버가 방 상태와 마감 시점에 맞춰 설문 응답을 제출하고, 마감 뒤 합류자 응답은 1차 안건 집계에서 분리해 저장할 수 있습니다.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `POST /api/rooms/{roomId}/survey`는 로그인 세션을 요구하고, 성공 응답을 `ApiResponse`의 `SURVEY_SUBMITTED` 코드로 감싼다.
- [ ] 일반 성공은 HTTP 201, 마감 시각 뒤 성공은 HTTP 200이며 응답 데이터에 설문 ID, `submittedAt`, `surveyClosed`를 담는다.
- [ ] 방 행 잠금 후 방 존재, 활동 멤버, 기존 제출 여부, 방 상태를 검증한다. 재제출은 HTTP 409 `ALREADY_SUBMITTED`다.
- [ ] `SURVEYING`에서만 예산·숙박·페이스를 필수로 받고, 다른 방 상태에서는 세 필드가 요청에 포함되면 HTTP 400 `INVALID_INPUT`으로 거부한다. `FINALIZED`는 HTTP 409 `INVALID_STATUS`다.
- [ ] 예산은 1~99,999,999, 활동 선호는 1~3개 중복 불가, 기피 메모는 선택이며 최대 100자다. 활동 개수 오류는 `INVALID_ACTIVITY_COUNT`다.
- [ ] `surveyClosedAt`이 있어도 미제출 활동 멤버의 첫 제출은 허용한다. 마감 뒤 첫 제출은 저장하되, 최초 마감 전이면 기존 응답으로 먼저 마감·안건 오픈을 처리해 늦은 응답을 1차 집계에서 제외한다.
- [ ] 출발 전날 00:00 이후에는 설문 마감만 기록하고 안건은 오픈하지 않는다. 5-1 마감은 방 상태를 변경하지 않는다.
- [ ] 기존 설문 집계 기반 안건 오픈 메서드가 없다면 연결 지점에 TODO를 남긴다. 방 잠금은 제출·합류·퇴장의 동시 요청을 직렬화한다.
- [ ] 인증, 검증 오류, 단계별 동작, 마감 시각 경계, DB 저장, 재제출 및 동시성을 실제 HTTP/Testcontainers 통합 테스트로 검증하고, 응답 도메인 검증은 기존 단위 테스트 경계를 따른다.

## Comments
