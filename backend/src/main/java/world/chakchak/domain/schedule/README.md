# schedule — 일차별 일정 보드

확정된 장소를 날마다 블록으로 놓고, 순서를 바꾸고, 시간과 메모를 적는 보드다. 여러 사람이 같은 날을 동시에 고칠 수 있어서, 순서 계산과 동시 수정 충돌을 이 패키지가 처리한다.

## API

| 메서드 | 경로 | 하는 일 |
| --- | --- | --- |
| GET | `/api/rooms/{roomId}/schedule` | 보드 조회 (날마다 날짜, 버전, 블록) |
| POST | `/api/rooms/{roomId}/schedule-days/{dayId}/items` | 블록 추가 |
| PATCH | `/api/rooms/{roomId}/schedule-items/{itemId}/position` | 같은 날 안에서 블록 이동 |
| PATCH | `/api/rooms/{roomId}/schedule-items/{itemId}` | 블록의 시간과 메모 수정 |
| DELETE | `/api/rooms/{roomId}/schedule-items/{itemId}` | 블록 삭제 |

쓰기 API는 모두 `dayVersion`(요청한 사람이 보고 있던 그날의 버전)을 함께 보낸다. 추가·이동·수정은 요청 본문에, 삭제는 쿼리 파라미터에 담는다.

## 주요 클래스

| 클래스 | 하는 일 |
| --- | --- |
| [`ScheduleDay`](entity/ScheduleDay.java) | 여행의 하루. 날짜는 저장하지 않고 "시작일 + (일차 - 1)"로 계산한다. `version`을 가진다 |
| [`ScheduleItem`](entity/ScheduleItem.java) | 하루에 놓인 블록. 화면 순서는 `sortOrder` 오름차순이다 |
| [`ScheduleBoardService`](service/ScheduleBoardService.java) | 보드 조회와 블록 추가·이동·수정·삭제 |
| [`ScheduleDayService`](service/ScheduleDayService.java) | 방을 만들 때 여행 일수만큼 날을 만든다 |

## 알아 둘 규칙과 설계

### 잠금과 버전을 둘 다 쓴다

쓰기 요청은 모두 `lockBoard`에서 방을 잠근다. 덕분에 두 사람이 같은 자리에 블록을 넣어도 순서값이 겹치지 않는다. 그런데 잠금은 서버에 동시에 도착한 요청의 순서만 정할 뿐이다. A가 10초 전 화면을 보고 드래그했는데 그사이 B가 이미 고쳤다면, 잠금만으로는 A가 낡은 화면을 기준으로 덮어쓰는 것을 막지 못한다. 그래서 요청의 `dayVersion`을 DB의 `ScheduleDay.version`과 비교한다. 다르면 `SCHEDULE_VERSION_CONFLICT`와 함께 지금 버전을 돌려주고, 프론트는 보드를 다시 불러온다. 제자리 이동이나 같은 값으로의 수정처럼 실제로 바뀐 게 없으면 버전을 올리지 않는다. 버전을 올리면 같은 날을 보던 다른 사람이 쓸데없이 충돌을 맞기 때문이다.

### 순서값은 1000 간격이고, 모자라면 다시 매긴다

맨 뒤에 넣으면 앞 블록 + 1000, 사이에 넣으면 앞뒤의 가운데 값을 쓴다. 이렇게 하면 블록 하나를 넣을 때 그날의 다른 블록을 고치지 않아도 된다. 1과 2 사이처럼 가운데에 넣을 정수가 없으면, 그날 블록을 1000, 2000, 3000…으로 다시 매긴 뒤 계산한다.

### 다시 매길 때는 음수로 한 번 비켜 둔다

같은 날 같은 순서값은 DB 유니크 제약이 막는다. 바로 새 값으로 바꾸면 UPDATE 순서에 따라 중간에 값이 겹칠 수 있다. 그래서 `renumber`는 먼저 모든 블록을 -1, -2, -3…으로 바꿔 `flush`하고, 그다음 진짜 값으로 바꿔 다시 `flush`한다.

### 시간이 어긋나도 막지 않는다

수정한 블록의 시간이 바로 앞 블록보다 이르거나 바로 뒤 블록보다 늦어도 저장은 된다. 대신 응답의 `warnings`에 `TIME_ORDER_MISMATCH`를 담아 알려 준다. 하루에 놓을 수 있는 블록은 10개다. 보드는 방이 `PLANNING`일 때만 고칠 수 있고, 그 밖의 단계에서는 `BOARD_LOCKED`가 난다.

## 다른 패키지와의 관계

schedule은 room의 잠금과 멤버 확인을 쓰고, 블록으로 놓을 장소가 확정됐는지는 place의 후보로 확인한다. 바뀐 내용은 "1일차 광장시장: 11:00 → 15:00" 같은 문구로 changelog에 남는다. room의 비접속 요약은 이 기록을 문구 대신 "2026-12-01 일정 3건 변경"처럼 변경이 기록된 날짜별 건수로 묶어 보여 준다.
