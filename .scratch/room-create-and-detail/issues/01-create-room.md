# 01: 여행방 생성 API 구현

**What to build:** 인증된 사용자가 여행방을 만들면 초대 코드를 받고, 생성자 멤버와 여행 날짜별 일정이 함께 준비되어 바로 여행 조율을 시작할 수 있습니다.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [x] 인증된 사용자는 `name`, `destination`, `startDate`, `endDate`로 `POST /api/rooms`를 호출할 수 있다.
- [x] 이름과 목적지는 앞뒤 공백을 제거한 뒤 각각 1자 이상 50자 이하이어야 한다.
- [x] 시작일 자정이 현재 시각부터 24시간 이내이거나 이미 지난 경우 `400 DEPARTURE_TOO_SOON`을 반환한다. 경계 시각도 거부한다.
- [x] 시작일이 오늘부터 1년 이상 뒤인 경우 `400 DEPARTURE_TOO_FAR`를 반환한다.
- [x] 종료일이 시작일보다 빠르거나 시작일과 종료일을 포함한 기간이 31일을 초과하면 `400 INVALID_DATE_RANGE`를 반환한다.
- [x] 같은 날짜의 여행과 시작일부터 31일째 날짜까지의 여행은 허용한다.
- [x] 초대 코드는 무작위 UUID 문자열의 첫 8자를 소문자로 사용한다. 중복이 확인되면 새 코드를 생성하고, DB UNIQUE 충돌 시에는 실패한 생성 트랜잭션을 롤백한 뒤 새 코드로 다시 시도한다.
- [x] 생성 성공 시 `plan_room`은 `SURVEYING` 상태이며 의견 마감 시각은 생성 시각부터 3일 뒤다.
- [x] 요청 사용자는 생성과 동시에 멤버가 되며 최초 `joinedAt`과 `lastSeenAt`은 동일한 시각이다. 설문 응답은 생성하지 않는다.
- [x] 여행 날짜마다 `schedule_day`가 하나씩 생성된다. `dayIndex`는 1부터 시작하고 각 일정의 초기 `version`은 0이다.
- [x] 방, 생성자 멤버 또는 일정 생성 중 실패하면 일부 데이터만 저장되지 않는다.
- [x] 성공 시 HTTP 201, 본문 코드 `ROOM_CREATED`, 메시지 `여행방을 만들었습니다.`를 반환한다.
- [x] 성공 응답 데이터는 합의된 방 상세 응답 구조를 따르며 생성자 멤버 한 명을 포함한다. 멤버의 `surveySubmitted`와 방의 `readOnly`는 모두 `false`다.
- [x] 인증된 HTTP 통합 테스트와 Testcontainers MySQL로 성공 응답, 저장 결과, 날짜 검증 오류, 원자성, 당일 및 31일 경계를 확인한다.

## 구현 및 검증

- API와 생성 흐름, 일정 생성, 요청/응답 DTO, 오류 코드, 통합·단위 테스트를 추가했다.
- `./gradlew test --tests 'world.chakchak.domain.room.RoomCreateIntegrationTest' --tests 'world.chakchak.domain.room.RoomServiceTest'` 통과 (14 tests).
- `./gradlew build` 통과.
