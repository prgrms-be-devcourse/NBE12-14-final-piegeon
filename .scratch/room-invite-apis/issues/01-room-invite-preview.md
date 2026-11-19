# 01: 초대 코드로 방 미리보기

**What to build:** 초대받은 사용자가 로그인 전에도 방 정보를 확인하고, 합류 가능 여부와 필요한 다음 행동을 알 수 있습니다.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [x] `GET /api/rooms/invites/{inviteCode}`는 비로그인 요청도 허용하고 HTTP 200, 본문 코드 `INVITE_PREVIEW_FETCHED`를 반환한다. 성공 메시지는 생략한다.
- [x] 응답에 방 ID·이름·목적지·여행 날짜·`RoomStatus`·활동 멤버 수·`joinable`·`notJoinableReason`·`surveyRequired`를 포함한다. 초대 코드와 멤버 이름은 포함하지 않는다.
- [x] 로그인하지 않은 사용자는 `LOGIN_REQUIRED`, 활동 멤버는 `ALREADY_MEMBER`, 탈퇴 이력이 있는 사용자는 `REJOIN_NOT_ALLOWED`를 받는다. 나머지 로그인 사용자는 합류 가능하며 불가 사유는 null이다.
- [x] `FINALIZED` 방만 `surveyRequired`가 false다.
- [x] 초대 미리보기에는 IP별 요청 횟수 제한을 적용하지 않는다.
- [x] 초대 코드 원문은 애플리케이션 로그에 남기지 않는다. 필요한 로그에는 앞 두 글자만 보이고 나머지는 마스킹한다.
- [x] 유효하지 않은 코드는 HTTP 404 `INVITE_CODE_NOT_FOUND`를 반환하며, 로그인 여부에 따라 결과가 달라지지 않는다.
- [x] `RoomInviteApiIntegrationTest`의 실제 HTTP·Testcontainers MySQL 테스트로 비로그인/로그인 응답, 미리보기 필드와 사유, 설문 필요 여부, 404를 검증한다.

## Comments
- `./gradlew test --tests 'world.chakchak.domain.room.RoomInviteApiIntegrationTest' --tests 'world.chakchak.domain.room.RoomServiceTest'` 통과.
- `./gradlew build` 통과: 테스트 330개, 실패·오류·건너뜀 0개.
