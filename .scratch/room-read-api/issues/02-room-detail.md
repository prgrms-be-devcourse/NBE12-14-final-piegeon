# 02: 방 상세 조회 API 구현

**What to build:** 활동 멤버가 방의 상세 정보와 현재 참여 중인 멤버 및 설문 제출 상태를 확인할 수 있습니다.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [x] `GET /api/rooms/{roomId}`는 HTTP 200, 본문 코드 `ROOM_FETCHED`, 성공 메시지 없는 상세 응답을 반환한다.
- [x] 방이 존재하지 않으면 404 `ROOM_NOT_FOUND`, 방이 존재하지만 요청 사용자가 활동 멤버가 아니면 403 `FORBIDDEN`을 반환한다. 미인증 요청은 401 `UNAUTHORIZED`를 반환한다.
- [x] 상세 응답은 방 ID, 이름, 목적지, 여행 시작일·종료일, 초대 코드, 기존 `RoomStatus`, `readOnly`, 의견 마감·설문 종료·최종 확정 시각을 포함한다. 설문 종료·최종 확정 시각이 없으면 null이다.
- [x] `readOnly`는 방 상태가 `FINALIZED`일 때만 true다.
- [x] 멤버 목록에는 활동 멤버만 포함하며 각 항목은 RoomMember ID인 `memberId`, 사용자 이름, `joinedAt`, `surveySubmitted`를 포함한다. `surveySubmitted`는 SurveyResponse `submittedAt` 존재 여부다.
- [x] 멤버 목록은 `joinedAt` 오름차순, 동률이면 `memberId` 오름차순으로 정렬한다.
- [x] 첫 조회에서 방 존재 여부와 요청자의 활동 멤버 여부를 확인하고, 두 번째 조회에서 멤버와 설문 제출 상태를 일괄 조회한다. 총 두 번의 DB 조회를 사용하며 멤버별 N+1은 발생시키지 않는다.
- [x] GET 조회는 `last_seen_at`을 갱신하지 않는다.
- [x] 인증 포함 HTTP 통합 테스트와 Testcontainers MySQL로 정상 응답, 활동 멤버 목록, 읽기 전용 상태, 설문 제출 여부, 멤버 정렬, 403·404·401 응답을 검증한다.

## Comments

- `./gradlew test --tests 'world.chakchak.domain.room.RoomReadApiIntegrationTest'` 통과 (R-02·R-03 통합 테스트 8개).
- 두 축의 코드 리뷰에서 명세 누락과 프로젝트 표준 위반이 발견되지 않았다.
