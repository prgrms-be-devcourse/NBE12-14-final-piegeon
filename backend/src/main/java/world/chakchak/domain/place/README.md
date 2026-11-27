# place — 장소 검색과 후보 보드

Google Places로 장소를 검색하고, 방에 후보로 올리고, 멤버들이 "가고 싶어요"와 "별로예요"로 반응하게 한다. 후보는 반응 수에 따라 칸반 보드의 칸으로 나뉜다. 전원이 가고 싶다고 한 장소만 확정할 수 있고, 확정된 장소만 일정에 놓을 수 있다.

## API

| 메서드 | 경로 | 하는 일 |
| --- | --- | --- |
| GET | `/api/rooms/{roomId}/places/search` | 장소 검색 (여행지를 검색어 앞에 붙인다) |
| POST | `/api/rooms/{roomId}/place-candidates` | 후보 등록 (등록한 사람의 "가고 싶어요"가 함께 남는다) |
| GET | `/api/rooms/{roomId}/place-candidates` | 칸반 보드 |
| GET | `/api/rooms/{roomId}/place-candidates/{placeId}` | 후보 상세와 멤버별 반응 |
| PUT | `/api/rooms/{roomId}/place-candidates/{placeId}/reaction` | 반응 정하기·바꾸기 |
| DELETE | `/api/rooms/{roomId}/place-candidates/{placeId}/reaction` | 반응 지우기 |
| POST | `/api/rooms/{roomId}/place-candidates/{placeId}/confirm` | 확정 |
| DELETE | `/api/rooms/{roomId}/place-candidates/{placeId}` | 후보 삭제 (확정 전만) |
| GET | `/api/rooms/{roomId}/place-candidates/{placeId}/photo` | 장소 사진 (Google 사진 주소로 302) |
| GET | `/api/rooms/{roomId}/place-candidates/{placeId}/comments` | 장소 코멘트 목록 |
| POST | `/api/rooms/{roomId}/place-candidates/{placeId}/comments` | 장소 코멘트 작성 |

## 주요 클래스

| 클래스 | 하는 일 |
| --- | --- |
| [`PlaceCandidate`](entity/PlaceCandidate.java) | 방에 올라온 후보. `confirmedAt`이 있으면 확정된 장소다 |
| [`PlaceReaction`](entity/PlaceReaction.java) | 멤버 한 명의 반응. 멤버당 후보 하나에 1개 |
| [`PlaceStage`](dto/PlaceStage.java) | 칸 계산. DB에 저장하지 않는다 |
| [`PlaceCandidateService`](service/PlaceCandidateService.java) | 등록, 보드, 반응, 확정, 삭제, 사진, 코멘트 |
| [`PlaceSearchService`](service/PlaceSearchService.java) | 검색 |
| [`PlaceSearchClient`](service/PlaceSearchClient.java) | 외부 검색 창구 인터페이스. 구현은 [`infra/google`](../../infra/README.md)에 있다 |
| [`ActivePlaceFilter`](service/ActivePlaceFilter.java) | room의 비접속 요약에서 그 사이 지워진 장소를 빼 준다 |

## 알아 둘 규칙과 설계

### 등록은 Google 호출을 트랜잭션 밖으로 뺀다

`register`에는 `@Transactional`을 붙이지 않았다. Google 상세 조회를 트랜잭션 안에 넣으면, 외부 API를 기다리는 동안(최대 3초) DB 연결과 방 잠금을 붙잡게 된다. 그래서 세 단계로 나눈다.

1. 짧은 읽기 트랜잭션에서 활동 멤버인지, 방이 `PLANNING`인지 확인한다. Google은 유료라서, 방 멤버가 아닌 사람이 호출을 일으키지 못하게 먼저 막는다.
2. 트랜잭션 밖에서 Google 상세를 조회한다.
3. `TransactionTemplate` 안에서 방을 잠그고, 멤버와 상태, 한도(여행 일수 × 10), 중복을 다시 확인한 뒤 저장한다. Google을 기다리는 사이 상태가 바뀌었을 수 있기 때문이다.

검색(`PlaceSearchService`)도 같은 이유로 트랜잭션을 걸지 않는다.

### 칸은 저장하지 않고 매번 계산한다

칸은 네 가지다. 확정됐으면 `CONFIRMED`다. 남아 있는 멤버 전원이 가고 싶다고 했으면 `FULL`, 2명 이상이면 `PARTIAL`, 그 밖에는 `COMPLEMENT`다. 멤버가 들어오거나 나가면 같은 후보라도 칸이 바뀌므로 저장해 둘 수 없다. 반응이 바뀔 때마다 실시간 소식(`place.agreement_changed`)을 보내고, 칸까지 바뀐 경우에만 활동 기록(`PLACE_STAGE_CHANGED`)을 남긴다. 같은 버튼을 다시 누르면 아무것도 남기지 않는다.

### 확정은 잠근 뒤 다시 센다

화면에서는 전원이 누른 것으로 보였어도, 그 사이 누가 들어오거나 취소했을 수 있다. 그래서 `confirm`은 방을 잠근 뒤 인원과 반응을 다시 센다. 이미 확정된 후보면 같은 응답을 돌려줘서, 두 사람이 거의 동시에 눌러도 기록은 한 번만 남는다.

### 삭제와 코멘트가 겹치는 경우

확정된 장소는 일정 블록이 가리킬 수 있으므로 지우지 않는다. 확정 전 후보는 행을 완전히 지우기 때문에, 같은 장소를 다시 올리면 새 id가 생긴다. 코멘트 작성은 방을 잠그지 않는다. 대신 삭제할 때 후보 행을 `findByIdForUpdate`로 잠가서, 저장 중인 코멘트가 끝날 때까지 기다리게 한다. 그래도 삭제 직후 코멘트가 저장되려 하면 외래 키에 걸린다. `PlaceCandidateService.addComment`가 이때의 `DataIntegrityViolationException`을 500 대신 `PLACE_CANDIDATE_NOT_FOUND`로 바꿔 돌려준다.

## 다른 패키지와의 관계

place는 room의 잠금과 인원 세기를 쓰고, 코멘트 저장은 comment에 맡긴다. schedule은 확정된 장소만 블록으로 놓는다. room의 나가기는 `deleteReactionsByMember`로 나간 사람의 반응을 지운다. Google 호출은 `PlaceSearchClient` 인터페이스만 알고, 실제 HTTP는 infra가 맡는다. 그래서 service 테스트에서는 Google 없이 가짜 구현을 넣을 수 있다.
