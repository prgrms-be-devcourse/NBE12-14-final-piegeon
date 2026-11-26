# survey — 여행 설문

방에 들어온 멤버에게서 예산, 숙소 타입, 이동 강도, 선호 활동, 피하고 싶은 것을 받는다. 설문이 마감되면 그 답으로 agenda가 1회차 안건을 연다. 응답을 모아 보여 주는 리포트도 이 패키지가 만든다.

## API

| 메서드 | 경로 | 하는 일 |
| --- | --- | --- |
| GET | `/api/survey-options` | 설문 선택지 목록 (숙소 타입, 이동 강도, 선호 활동) |
| POST | `/api/rooms/{roomId}/survey` | 설문 제출 |
| GET | `/api/rooms/{roomId}/survey/me` | 내 설문 응답 |
| GET | `/api/rooms/{roomId}/survey/report` | 설문 조율 리포트 (마감 뒤에만) |
| GET | `/api/rooms/{roomId}/surveys/status` | 누가 냈는지, 마감까지 얼마나 남았는지 |

## 주요 클래스

| 클래스 | 하는 일 |
| --- | --- |
| [`SurveyResponse`](entity/SurveyResponse.java) | 멤버 한 명의 설문. 금액 범위와 선호 활동 개수를 스스로 검증한다 |
| [`StayType`](entity/StayType.java), [`Pace`](entity/Pace.java) | 숙소 타입과 이동 강도 선택지. 값이 곧 안건 선지의 `value`가 된다 |
| [`ActivityCode`](entity/ActivityCode.java) | 선호 활동 선택지. 안건이 되지 않고 리포트에만 쓴다 |
| [`SurveySubmitService`](service/SurveySubmitService.java) | 제출과 마감 판정 |
| [`SurveyResponseService`](service/SurveyResponseService.java) | 응답 저장·조회·삭제. 다른 도메인이 응답을 다룰 때 이곳을 거친다 |
| [`SurveyReportService`](service/SurveyReportService.java) | 리포트 계산. 저장하지 않고 조회할 때마다 계산한다 |
| [`SurveyDeadlineService`](service/SurveyDeadlineService.java) | 의견 마감이 지난 방의 설문을 스케줄러가 마감할 때 쓴다 |

## 알아 둘 규칙과 설계

### 설문이 마감되는 길은 세 가지다

설문은 남아 있는 멤버 전원이 냈거나, 의견 마감 시각(방 생성 + 3일)이 지났고 제출이 1건 이상일 때 마감된다. 판정은 `Room.closeSurveyIfReady`가 한다. 이 판정을 부르는 곳은 마지막 멤버의 제출(`SurveySubmitService`), 아직 내지 않은 멤버의 퇴장(`RoomLeaveService`), 매분 도는 마감 스케줄러(`SurveyDeadlineService`) 세 곳이다. 셋 모두 방 잠금을 잡은 채로 판정하고, 마감되면 같은 트랜잭션에서 `AgendaService.openSurveyAgendas`를 부른다. 의견 마감이 지났는데 제출이 0건이면 마감하지 않고 방을 `EXPIRED`로 만든다.

### 단계에 따라 받는 항목이 다르다

`SURVEYING`에서는 예산·숙소·이동 강도가 필수다. 마감 뒤에 늦게 낸 답도 받는데, 지금 회차에는 넣지 않고 다음 회차 선지를 만들 때 쓴다. `PLANNING` 이후에 들어온 멤버는 그 세 항목이 이미 정해졌으므로 선호 활동과 피하고 싶은 것만 낸다. 그때 세 항목을 보내면 `INVALID_INPUT`이다. `FINALIZED`와 `EXPIRED` 방에서는 제출할 수 없다. 설문은 멤버당 한 번만 낼 수 있다.

### 리포트는 "마감 전 답"과 "전체 답"을 나눠 쓴다

예산·숙소·이동 강도의 분포와 만장일치 여부는 마감 시각까지 낸 답으로만 계산한다. 안건 선지가 그 답으로 만들어지기 때문이다. 선호 활동과 피하고 싶은 것은 늦게 낸 답까지 남아 있는 멤버 전원의 것을 보여 준다. 예산은 안건 선지와 똑같이 만원 단위로 반올림해서 센다. 그래서 1,226,000원과 1,234,000원은 같은 123만원으로 묶이고, 이 경우 투표 없이 결정된다.

## 다른 패키지와의 관계

survey는 room의 잠금과 멤버 확인을 쓰고, 마감되면 agenda를 부른다. 반대로 agenda는 선지를 만들 때 `SurveyResponseService.getActiveMemberResponses`로 응답을 읽고, room의 나가기는 `deleteForMember`로 나간 사람의 응답을 지운다. `deleteForMember`, `countActiveResponses`, `save`는 `Propagation.MANDATORY`라서 트랜잭션 없이 부르면 예외가 난다. 이 설정은 트랜잭션이 있는지만 확인하고, 방 잠금을 잡았는지는 확인하지 않는다. 그래서 호출하는 쪽이 방 잠금을 먼저 잡아야 한다.
