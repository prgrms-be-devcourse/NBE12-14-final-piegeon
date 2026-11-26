# changelog — 방 활동 기록과 실시간 전파

방에서 일어난 일을 기록으로 남기고, 같은 방 멤버에게 SSE로 바로 알린다. 타임라인, 비접속 요약, SSE 재연결이 모두 이 기록 하나를 같이 쓴다. 왜 이렇게 정했는지는 [ADR 0001](../../../../../../../../docs/adr/0001-room-activity-history.md)에 있다.

## API

| 메서드 | 경로 | 하는 일 |
| --- | --- | --- |
| GET | `/api/rooms/{roomId}/change-logs` | 활동 타임라인 (최신순, 커서 페이지, 기본 20개) |
| GET | `/api/rooms/{roomId}/events` | SSE 구독 (`Last-Event-ID` 헤더로 이어 받기) |

## 주요 클래스

| 클래스 | 하는 일 |
| --- | --- |
| [`ChangeLog`](entity/ChangeLog.java) | 기록 한 건. 무슨 일(`EventKind`), 대상(`EntityType`, id), 행위자, 그때의 요약 문구를 담는다. 고치지 않는다 |
| [`ChangeLogService`](service/ChangeLogService.java) | 다른 도메인이 기록을 남기는 입구 |
| [`ChangeLogStreamListener`](service/ChangeLogStreamListener.java) | 트랜잭션이 커밋된 뒤에만 SSE로 내보낸다 |
| [`ChangeLogStreamService`](service/ChangeLogStreamService.java) | SSE 연결 관리와 전송 |
| [`ChangeLogReplayService`](service/ChangeLogReplayService.java) | 재연결할 때 놓친 기록을 찾는다 |
| [`ChangeLogTimelineService`](service/ChangeLogTimelineService.java) | 타임라인 조회 |

## 알아 둘 규칙과 설계

### SSE 이벤트는 두 종류다

| 종류 | 이벤트 이름 | DB 기록 | SSE `id` | 재연결 때 다시 받음 |
| --- | --- | --- | --- | --- |
| 영속 이벤트 | `activity` | 남긴다 | 기록 id | 받는다 |
| 일시 이벤트 | `vote.updated`, `place.agreement_changed`, `comment.created`, `room.status_changed`, `member.left` | 남기지 않는다 | 없음 | 받지 않는다 |

멤버 합류·퇴장, 설문 마감, 조율 완료, 안건 열림·결정·미결, 장소 등록·칸 변경·확정·삭제, 일정 변경, 계획 확정은 `activity`다. 퇴장은 `activity`로 기록하면서 `member.left`도 함께 보낸다. 표 수나 반응 수처럼 금방 다시 바뀌는 숫자는 일시 이벤트로 보낸다. 일시 이벤트를 놓쳤으면 프론트가 해당 화면을 다시 조회하면 된다. `RoomEvent`는 이름이 `activity`가 아니면 id를 강제로 비운다. 재연결 커서로 쓰는 id는 기록 id뿐이기 때문이다.

### 커밋된 뒤에만 보낸다

`ChangeLogStreamListener`는 `@TransactionalEventListener(phase = AFTER_COMMIT)`이다. 커밋 전에 보내면 두 가지 문제가 생긴다. 롤백됐을 때 다른 멤버 화면에 없던 변경이 나타나고, 소식을 받은 프론트가 바로 다시 조회하면 아직 커밋되지 않은 옛 데이터를 읽는다.

### 연결 직후 들어온 소식을 잃지 않는다

재연결하면 `Last-Event-ID` 뒤의 기록을 DB에서 읽어 먼저 보낸다. 그런데 그 조회를 하는 사이에도 새 소식이 생길 수 있다. 그래서 `connect`는 DB를 읽기 전에 연결을 `connections` 맵에 먼저 등록한다. 초기화가 끝나기 전에 도착한 `activity`는 `pending` 버퍼(`TreeMap`)에 모아 둔다. 그사이 온 일시 이벤트는 버린다. 어차피 재연결 뒤 화면을 다시 조회하기 때문이다. 놓친 기록을 다 보낸 뒤에는, 마지막으로 보낸 id보다 큰 것만 순서대로 보낸다. 놓친 기록이 200건을 넘거나, 클라이언트가 보낸 id가 서버의 최신 id보다 크면 하나씩 보내지 않는다. 그때는 `resync.required` 이벤트를 보내고, 프론트가 화면을 처음부터 다시 불러온다.

### 연결 관리

멤버 한 명당 연결은 3개까지다. 넘으면 가장 오래된 연결에 `stream.replaced`를 보내고 닫는다. 연결은 30분 뒤 끊기고, 그동안 25초마다 `ping` 주석을 보내 연결을 살려 둔다. 멤버가 방을 나가면 `RoomMemberLeftEvent`로, 로그아웃하면 `closeConnectionsForUser`로 그 사람의 연결을 닫는다.

### 서버가 한 대라는 전제

연결 목록은 서버 메모리에 있다. 서버를 두 대 이상 띄우면 A 서버에서 생긴 소식이 B 서버에 붙은 멤버에게 가지 않는다. 지금 배포는 한 대라 문제가 없지만, 서버를 늘리려면 서버 사이에 소식을 전하는 장치가 따로 필요하다.

## 다른 패키지와의 관계

room, agenda, place, schedule, comment가 `ChangeLogService`로 기록을 남기거나 `RoomEvent`를 발행한다. 기록을 남기는 메서드는 `Propagation.MANDATORY`라서, 상태를 바꾼 그 트랜잭션 안에서 함께 저장된다. room의 비접속 요약은 이 기록을 읽어 만든다.
