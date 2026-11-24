# 방 목록 및 상세 조회 API 명세

구현 티켓 기준 상세 명세는 [`.scratch/room-read-api/spec.md`](../../.scratch/room-read-api/spec.md)를 따른다.

## 공통 규칙

- 두 API 모두 로그인한 사용자만 호출할 수 있으며 성공 시 HTTP 200을 반환한다.
- 성공 응답은 기존 `ApiResponse` 형식을 사용한다. `message`는 생략한다.
- 방에 대한 GET 조회는 멤버의 마지막 확인 시각을 갱신하지 않는다. 마지막 확인 시각은 H-03 `POST /api/rooms/{roomId}/seen`에서 `seenUntil`을 기준으로 갱신한다.
- 방 상태 타입은 기존 `RoomStatus`를 사용한다.

## R-02 내 방 목록

`GET /api/rooms`

현재 로그인 사용자가 활동 멤버로 속한 방을 모두 반환한다. 방 상태와 여행 날짜에 관계없이 활동 멤버십이 있으면 포함하며, 탈퇴한 방은 제외한다. 페이지네이션은 두지 않는다.

방은 `startDate` 오름차순으로 정렬하고, 출발일이 같으면 `roomId` 오름차순으로 정렬한다. 방별 멤버 수 집계를 위해 방마다 쿼리를 실행하지 않고 한 번의 집계 쿼리로 목록을 조회한다.

응답 코드: `ROOMS_FETCHED`

```json
{
  "success": true,
  "code": "ROOMS_FETCHED",
  "data": {
    "rooms": [
      {
        "roomId": 3,
        "name": "부산 연말 여행",
        "destination": "부산",
        "startDate": "2026-12-24",
        "endDate": "2026-12-26",
        "status": "PLANNING",
        "memberCount": 4,
        "opinionDeadlineAt": "2026-11-19T21:00:00",
        "mySurveySubmitted": true
      }
    ]
  }
}
```

`memberCount`는 해당 방의 활동 멤버 수다. `mySurveySubmitted`는 현재 사용자의 방 멤버에 연결된 설문 응답의 `submittedAt`이 있는지 나타낸다. 미투표 안건 수는 목록 응답에 포함하지 않는다.

응답 DTO 형태:

```java
public record RoomListResponse(List<RoomSummaryResponse> rooms) {
    public record RoomSummaryResponse(
            Long roomId,
            String name,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            RoomStatus status,
            int memberCount,
            LocalDateTime opinionDeadlineAt,
            boolean mySurveySubmitted) {}
}
```

활동 중인 방이 없으면 `rooms`는 빈 배열이다.

## R-03 방 상세

`GET /api/rooms/{roomId}`

요청 사용자가 해당 방의 활동 멤버인 경우에만 상세를 반환한다. 방이 없으면 HTTP 404 `ROOM_NOT_FOUND`, 방이 있지만 요청 사용자가 활동 멤버가 아니면 HTTP 403 `FORBIDDEN`을 반환한다.

응답 코드: `ROOM_FETCHED`

```json
{
  "success": true,
  "code": "ROOM_FETCHED",
  "data": {
    "roomId": 3,
    "name": "부산 연말 여행",
    "destination": "부산",
    "startDate": "2026-12-24",
    "endDate": "2026-12-26",
    "inviteCode": "K7MP2QXR",
    "status": "PLANNING",
    "readOnly": false,
    "opinionDeadlineAt": "2026-11-19T21:00:00",
    "surveyClosedAt": "2026-11-14T10:00:00",
    "finalizedAt": null,
    "members": [
      {
        "memberId": 10,
        "name": "민수",
        "joinedAt": "2026-11-12T21:00:00",
        "surveySubmitted": true
      },
      {
        "memberId": 11,
        "name": "지은",
        "joinedAt": "2026-11-12T22:10:00",
        "surveySubmitted": false
      }
    ]
  }
}
```

`readOnly`는 방 상태가 `FINALIZED`일 때만 `true`다. `members`에는 활동 멤버만 포함하며 `joinedAt` 오름차순, 동률이면 `memberId` 오름차순으로 정렬한다. `memberId`는 사용자 ID가 아니라 방 멤버 레코드 ID다. `surveySubmitted`는 해당 멤버의 설문 응답 `submittedAt`이 있는지 나타낸다.

상세 조회는 방 정보 쿼리 한 번과 활동 멤버 및 설문 제출 여부를 함께 읽는 일괄 쿼리 한 번으로 수행한다. 멤버마다 설문을 따로 조회하는 N+1 쿼리는 사용하지 않는다.
