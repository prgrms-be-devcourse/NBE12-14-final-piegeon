# comment — 코멘트

안건과 장소 후보에 붙는 코멘트를 저장하고 읽는다. 최종 확정에 반대할 때 남기는 이유도 안건 코멘트로 저장된다. 수정과 삭제 API는 없다.

## API

이 패키지에는 controller가 없다. 코멘트 API는 대상의 경로 아래에 있고, 각 도메인 controller가 받는다.

| 메서드 | 경로 | 받는 곳 |
| --- | --- | --- |
| GET, POST | `/api/rooms/{roomId}/agendas/{agendaId}/comments` | [agenda](../agenda/README.md) |
| GET, POST | `/api/rooms/{roomId}/place-candidates/{placeId}/comments` | [place](../place/README.md) |

## 주요 클래스

| 클래스 | 하는 일 |
| --- | --- |
| [`Comment`](entity/Comment.java) | 코멘트 한 건. 안건과 장소 후보 중 정확히 하나만 값이 있다 (DB CHECK) |
| [`CommentService`](service/CommentService.java) | 저장, 조회, 장소 후보 삭제 때 함께 지우기 |
| [`CommentCreatedEvent`](event/CommentCreatedEvent.java) | SSE `comment.created`의 내용 |

## 알아 둘 규칙과 설계

### 다른 도메인의 service를 주입받지 않는다

`CommentService`는 대상이 이 방의 것인지, 쓰는 사람이 활동 멤버인지 직접 확인하지 않는다. 그 확인은 부르는 쪽인 `AgendaService`와 `PlaceCandidateService`가 먼저 하고, 확인된 엔티티를 넘긴다. comment가 agenda와 place를 둘 다 부르면, 그 둘이 comment를 부르는 것과 맞물려 의존이 순환하기 때문이다.

### 방 잠금을 잡지 않는다

코멘트는 방이나 안건의 상태를 바꾸지 않는다. 그래서 작성할 때 방을 잠그지 않는다. 그 대신 장소 후보가 지워지는 순간과 겹칠 수 있는데, 이 경우의 처리는 [place README](../place/README.md#삭제와-코멘트가-겹치는-경우)에 있다. 코멘트는 `SURVEYING`, `PLANNING`, `REVIEWING` 단계에서만 쓸 수 있고, 본문은 500자까지다.

### 기록은 남기지 않고 알리기만 한다

코멘트는 활동 기록(`change_log`)에 남기지 않는다. 저장한 뒤 `comment.created` 일시 이벤트만 보낸다. 그래서 SSE `id`가 없고, 재연결 때 다시 받지 않는다.
