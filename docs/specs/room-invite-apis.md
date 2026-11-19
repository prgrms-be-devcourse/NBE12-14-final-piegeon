# 방 초대 미리보기 및 합류 API 명세

구현 티켓 기준 상세 명세는 [`.scratch/room-invite-apis/spec.md`](../../.scratch/room-invite-apis/spec.md)를 따른다.

## R-04 초대 미리보기

`GET /api/rooms/invites/{inviteCode}`는 비로그인 요청도 허용한다. 성공은 HTTP 200, 본문 코드 `INVITE_PREVIEW_FETCHED`이며 성공 메시지는 생략한다. 응답은 방 ID·이름·목적지·여행 날짜·상태·활동 멤버 수·합류 가능 여부·합류 불가 사유·설문 필요 여부를 포함한다. 멤버 이름과 초대 코드는 응답하지 않는다.

비로그인 요청은 `LOGIN_REQUIRED`, 로그인한 활동 멤버는 `ALREADY_MEMBER`, 탈퇴 이력이 있는 사용자는 `REJOIN_NOT_ALLOWED`를 합류 불가 사유로 받는다. 초대 코드가 없으면 404 `INVITE_CODE_NOT_FOUND`다. `FINALIZED` 방만 `surveyRequired`가 false다.

초대 미리보기는 IP별 고정 1분 창에서 유효·무효 요청을 합쳐 20회까지 허용하며, 초과하면 429 `TOO_MANY_REQUESTS`를 반환한다. 제한 저장은 애플리케이션 메모리를 사용하고, 애플리케이션 로그에는 초대 코드 원문을 남기지 않는다.

## R-05 초대 코드 합류

`POST /api/rooms/invites/{inviteCode}/join`은 로그인 사용자만 호출할 수 있다. 하나의 트랜잭션에서 방 행을 잠그고 기존 멤버 이력을 확인한 뒤 멤버를 추가하고 `MEMBER_JOINED` 방 활동 내역을 기록한다. 성공은 HTTP 200, 본문 코드 `ROOM_JOINED`, 메시지 `여행방에 합류했습니다.`다.

활동 멤버는 409 `ALREADY_MEMBER`, 탈퇴 이력이 있는 사용자는 409 `REJOIN_NOT_ALLOWED`다. 신규 멤버의 `joinedAt`과 `lastSeenAt`은 동일한 현재 시각이다. `SURVEYING` 합류는 첫 의견 마감 시각을 변경하거나 제출을 막지 않는다. `FINALIZED` 합류는 허용하며 설문이 필요하지 않다.

합류 기록은 `MEMBER_JOINED`, `MEMBER`, 신규 RoomMember ID, `CREATE`, 합류자 행위자 및 `서연님이 합류했어요` 형식의 요약으로 저장한다. SSE 전파는 후속 작업이다.
