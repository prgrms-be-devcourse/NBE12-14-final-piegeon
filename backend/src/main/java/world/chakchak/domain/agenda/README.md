# agenda — 안건과 투표

설문에서 의견이 갈린 항목(예산 `BUDGET`, 숙소 `STAY`, 이동 강도 `PACE`)과 최종 확정(`FINAL_PLAN`)을 안건으로 열고, 투표로 정한다. 모든 안건은 만장일치로 결정한다. 정해지지 않으면 회차를 올려 다시 투표한다. 기한이 지난 안건과 출발 전날(D-1) 최종 집계도 이 패키지가 처리한다.

## API

| 메서드 | 경로 | 하는 일 |
| --- | --- | --- |
| GET | `/api/rooms/{roomId}/agendas` | 안건 목록 (투표 중인 것은 마감 임박 순, 끝난 것은 최근 순) |
| GET | `/api/rooms/{roomId}/agendas/{agendaId}` | 안건 상세와 선지별 투표자 |
| PUT | `/api/rooms/{roomId}/agendas/{agendaId}/vote` | 투표하거나 표 바꾸기 |
| POST | `/api/rooms/{roomId}/agendas/{agendaId}/options` | 예산 선지 추가 |
| DELETE | `/api/rooms/{roomId}/agendas/{agendaId}/options/{optionId}` | 내가 추가한 예산 선지 삭제 |
| POST | `/api/rooms/{roomId}/agendas/{agendaId}/next-round` | 미결로 끝난 안건의 다음 회차 열기 |
| GET | `/api/rooms/{roomId}/agendas/{agendaId}/comments` | 안건 코멘트 목록 |
| POST | `/api/rooms/{roomId}/agendas/{agendaId}/comments` | 안건 코멘트 작성 |
| POST | `/api/rooms/{roomId}/final-review` | 최종 확정 투표 열기 |

## 주요 클래스

| 클래스 | 하는 일 |
| --- | --- |
| [`AgendaItem`](entity/AgendaItem.java) | 안건. 투표 기한 계산, 만장일치 판정(`decidableOption`), D-1 집계(`finalTallyOption`)를 가진다 |
| [`AgendaOption`](entity/AgendaOption.java) | 선지. `value`는 비교용 값, `label`은 화면 글자 |
| [`Vote`](entity/Vote.java) | 멤버 한 명이 안건 하나에 낸 표. 멤버당 1표이고 바꾸면 UPDATE한다 |
| [`AgendaService`](service/AgendaService.java) | 조회, 투표, 판정, 회차, 선지 추가·삭제, 최종 확정 |
| [`AgendaChangePublisher`](service/AgendaChangePublisher.java) | 안건 관련 활동 기록과 SSE 이벤트를 만든다 |
| [`AgendaDeadlineScheduler`](service/AgendaDeadlineScheduler.java) | 매분 설문 마감, 안건 마감, D-1 최종 집계를 차례로 실행한다 |
| [`UnvotedAgendaFinder`](service/UnvotedAgendaFinder.java) | room의 비접속 요약에 "아직 투표 안 한 안건"을 알려 준다 |

## 알아 둘 규칙과 설계

### 설문이 마감되면 1회차 안건이 열린다

`openSurveyAgendas`는 마감 전에 낸 설문 답으로 세 항목의 선지를 만든다. 선지가 하나뿐이면 이미 모두 같은 답을 낸 것이므로 투표 없이 바로 결정한다. 세 항목이 모두 그렇게 결정되면 방은 곧바로 `PLANNING`으로 넘어간다. 1회차 투표 기간은 설문 마감 뒤 4일, 다음 회차와 최종 확정은 7일이다. 모든 마감 시각은 출발 전날 00:00(D-1)을 넘지 않게 잘린다.

### 판정은 만장일치다

표가 들어올 때마다 `judge`가 결정할 수 있는지 본다. 투표 수가 자격자 수(`totalVoter`)에 도달하고 모든 표가 같은 선지면 결정된다. 투표 자격은 안건이 생긴 시각까지 방에 들어온 멤버에게만 있다. 최종 확정은 예외로, 반대가 한 표라도 들어오면 그 자리에서 반대로 결정되고 방은 `PLANNING`으로 돌아간다. 반대할 때는 이유를 코멘트로 남겨야 한다. 최종 확정 투표를 연 사람은 동의한 것으로 본다. 그래서 혼자 있는 방은 투표를 여는 요청 한 번으로 `PLANNING → REVIEWING → FINALIZED`까지 간다.

### 판정 메서드는 호출자의 트랜잭션 안에서만 돈다

`judge`, `applyExpired`, `openSurveyAgendas`, `memberLeft`는 `Propagation.MANDATORY`다. 이 설정이 보장하는 것은 "트랜잭션 안에서 불렸다"는 것까지다. 방 잠금을 잡았는지는 확인하지 않는다. 대신 이 메서드들은 잠금으로 읽은 `Room`을 인자로 받도록 되어 있다. 호출자는 `RoomService.getForUpdate`로 잠근 방을 넘겨야 한다. 안건 결정과 방 상태 변경이 한 트랜잭션에서 일어나야 "안건은 동의로 끝났는데 방은 아직 `REVIEWING`" 같은 어긋남이 남지 않는다.

### 기한과 D-1 최종 집계

기한이 지나도 결정되지 않은 안건은 미결(`EXPIRED`)이 된다. 설문 안건은 자동으로 다음 회차가 열리지 않고, 멤버가 직접 연다. 다음 회차의 선지는 남은 멤버의 설문 답과, 직전 회차에 남은 멤버가 추가한 예산 선지로 다시 만든다. 출발 전날 00:00이 되면 `runFinalTally`가 남은 안건을 남아 있는 멤버의 표로 마지막 판정한다. 설문 안건은 단독 1위가 있으면 결정되고, 동점이거나 표가 없으면 방이 `EXPIRED`가 된다. 최종 확정은 반대가 있으면 반대, 반대 없이 표가 있으면 동의로 끝난다. 표가 하나도 없으면 미결로 끝나고 방은 `PLANNING`으로 돌아간다.

### 잠금을 잡은 다음에 현재 시각을 구한다

`vote`는 `getForUpdate` 바로 다음 줄에서 `LocalDateTime.now(clock)`을 구한다. 잠금을 기다리는 사이 마감이 지났을 수 있기 때문이다. 잠금 전에 구한 시각으로 판단하면 이미 마감된 안건에 표가 들어간다.

### 예산 선지는 멤버가 직접 추가할 수 있다

회차마다 1인 1개다. 금액은 만원 단위로 반올림해 저장하고, 반올림한 값으로 중복을 확인한다. 다른 멤버의 표가 있는 선지는 지울 수 없다. 지우면 그 사람의 표가 몰래 사라지기 때문이다.

## 다른 패키지와의 관계

agenda는 room의 잠금과 상태 전이를 쓰고, survey의 응답으로 선지를 만들며, 코멘트는 comment에 맡긴다. survey와 room(나가기)이 설문 마감이나 퇴장 뒤에 agenda를 부른다. 활동 기록과 실시간 소식은 `AgendaChangePublisher`를 거쳐 changelog로 보낸다.
