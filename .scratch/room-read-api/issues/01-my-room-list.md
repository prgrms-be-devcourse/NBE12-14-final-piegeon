# 01: 내 방 목록 조회 API 구현

**What to build:** 로그인 사용자가 활동 멤버로 남아 있는 모든 방과 목록에서 필요한 멤버 수 및 설문 제출 상태를 확인할 수 있습니다.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [x] `GET /api/rooms`는 HTTP 200, 본문 코드 `ROOMS_FETCHED`, 성공 메시지 없는 `data.rooms` 응답을 반환한다.
- [x] 로그인한 사용자의 활동 멤버십이 있는 방만 반환하고, 탈퇴한 방은 제외한다. 방 상태와 여행 날짜에는 관계없이 FINALIZED 방과 지난 여행도 포함한다.
- [x] 응답 항목은 `roomId`, `name`, `destination`, `startDate`, `endDate`, 기존 `RoomStatus`, `memberCount`, `opinionDeadlineAt`, `mySurveySubmitted`를 포함한다. `unvotedAgendaCount`는 포함하지 않는다.
- [x] `memberCount`는 방의 활동 멤버 수이며, `mySurveySubmitted`는 현재 사용자의 해당 방 설문 응답 `submittedAt` 존재 여부다.
- [x] 페이지네이션 없이 전체 목록을 반환한다. 목록은 출발일 오름차순, 동률이면 `roomId` 오름차순이다.
- [x] 목록과 집계값은 한 번의 집계 조회로 가져오며 방별 N+1 쿼리를 만들지 않는다.
- [x] 활동 중인 방이 없으면 `rooms`에 빈 배열을 반환한다.
- [x] GET 조회는 `last_seen_at`을 갱신하지 않는다.
- [x] 인증 포함 HTTP 통합 테스트와 Testcontainers MySQL로 목록 응답, 활동 멤버 필터, 멤버 수, 설문 제출 상태, 정렬, 빈 목록, 미인증 응답을 검증한다. SQL 횟수 assertion은 추가하지 않는다.

## Comments

- `./gradlew test --tests 'world.chakchak.domain.room.RoomReadApiIntegrationTest'` 통과 (3 tests).
- `./gradlew build` 통과.
- 코드 리뷰에서 명세 누락과 프로젝트 표준 위반은 발견되지 않았다.
