# 02: 초대 코드로 방 합류

**What to build:** 로그인 사용자가 초대받은 방에 합류하고, 방 멤버와 방 활동 내역에 그 결과가 일관되게 반영됩니다.

**Blocked by:** None (can start immediately)

**Status:** ready-for-human

- [x] `POST /api/rooms/invites/{inviteCode}/join`은 로그인 사용자를 허용한다. 비로그인 요청은 HTTP 401 `UNAUTHORIZED`다.
- [x] 성공 응답은 HTTP 200, 본문 코드 `ROOM_JOINED`, 메시지 `여행방에 합류했습니다.`와 `roomId`, `memberId`, `surveyRequired` 데이터를 반환한다.
- [x] 초대 코드로 방을 찾고 방 행을 잠근 뒤 같은 트랜잭션에서 기존 멤버 이력 확인, 멤버 추가, ChangeLog 저장을 수행한다.
- [x] 활동 중인 기존 멤버는 HTTP 409 `ALREADY_MEMBER`, 탈퇴 이력이 있는 사용자는 HTTP 409 `REJOIN_NOT_ALLOWED`다. 초대 코드가 없으면 HTTP 404 `INVITE_CODE_NOT_FOUND`다.
- [x] 신규 멤버의 `joinedAt`과 `lastSeenAt`을 동일한 현재 시각으로 초기화하고 `leftAt`은 null로 둔다.
- [x] `SURVEYING` 합류는 첫 의견 마감 시각과 방 상태를 변경하지 않으며 마감 시각이 지난 방에도 허용한다. `PLANNING` 합류는 확정 장소를 변경하지 않는다.
- [x] `REVIEWING` 상태를 유지하고, 새 멤버는 이미 열린 안건에 투표할 수 없으며 이후 열린 안건에는 기존 자격 규칙을 적용한다.
- [x] `FINALIZED` 방에도 합류할 수 있고 읽기 전용을 유지한다. `FINALIZED`에서만 `surveyRequired`는 false다.
- [x] `MEMBER_JOINED` ChangeLog를 같은 트랜잭션에 한 건 저장한다. 대상은 새 RoomMember, 행위자는 합류 멤버, `action_status`는 `CREATE`, 요약은 `서연님이 합류했어요` 형식이다. 활동 인원수 data는 저장하지 않는다.
- [x] SSE 발행 및 이벤트 활동 인원수 저장은 이 티켓 범위에 포함하지 않는다.
- [x] `RoomInviteApiIntegrationTest`의 실제 HTTP·Testcontainers MySQL 테스트로 정상 합류, 상태별 응답, 시간 초기화, 중복·재합류 거절, ChangeLog 저장, 동시 중복 요청, 기존·후속 안건 투표 자격을 검증한다.

## 구현 결과

- 변경 파일: `RoomController`, `RoomService`, `RoomRepository`, `RoomMemberService`, `JoinRoomResponse`, `RoomErrorCode`, `ChangeLogService`, `RoomInviteApiIntegrationTest`, `RoomServiceTest`
- DB 마이그레이션: 없음. 기존 제약과 컬럼을 재사용한다.
- 검증: `./gradlew test --tests 'world.chakchak.domain.room.RoomInviteApiIntegrationTest' --tests 'world.chakchak.domain.room.RoomServiceTest'` 통과. `./gradlew build` 통과.
- 커밋 및 푸시는 하지 않는다.

## Comments
