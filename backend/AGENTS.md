# 착착 (Chak-Chak) 백엔드

그룹 여행 조율 서비스의 API 서버다. 여행방 생성, 설문과 조율, 장소 후보와 투표,
일차별 일정, 최종 확정 순서로 진행되고, 변경은 SSE로 같은 방 멤버에게 전파한다.

## 스택
- Java 21, Spring Boot 4.1.x, Gradle (Groovy DSL)
- Spring MVC, Spring Data JPA, Spring Security(세션 로그인), Spring Session JDBC
- MySQL 8.4, Flyway
- 테스트: JUnit, Mockito, Testcontainers(MySQL)

## 명령
아래 명령은 모두 저장소의 `backend/` 폴더에서 실행한다. (저장소 최상위에는 `backend/`, `frontend/`가 있다.)
- 로컬 DB 실행: `docker compose up -d`
- 빌드와 전체 테스트: `./gradlew build` (Testcontainers를 쓰므로 Docker가 켜져 있어야 한다)
- 테스트만: `./gradlew test`
- 코드 포맷 맞추기: `./gradlew spotlessApply` (검사만: `./gradlew spotlessCheck`)
- 일부 테스트: `./gradlew test --tests "world.chakchak.domain.auth.*"`
- 실행: `./gradlew bootRun --args='--spring.profiles.active=local'`
- 시스템에 설치된 `gradle` 대신 항상 `./gradlew`를 쓴다.

## 패키지 구조
- `world.chakchak.global`: 설정, 보안, 에러 처리, BaseEntity
- `world.chakchak.domain.<도메인>`: auth, user, room, survey, agenda, place, schedule, comment, changelog
- `world.chakchak.infra`: 외부 API 구현체
- 도메인 패키지 안은 controller, service, repository, entity, dto로 나눈다.

## 코드 규칙
- 호출 방향은 controller → service → repository다. controller에서 repository를 쓰지 않는다.
- service에서 HttpServletRequest, HttpSession, SecurityContextHolder를 쓰지 않는다.
  controller가 `@AuthenticationPrincipal LoginUser`에서 userId를 꺼내 인자로 넘긴다.
- 다른 도메인의 repository를 직접 주입하지 않는다. 그 도메인의 service를 쓴다.
- `global`에서 `domain`을 import하지 않는다.
- 상태 전이 규칙은 엔티티 메서드에 둔다.
- 현재 시각은 주입받은 `Clock`으로 구한다. `LocalDateTime.now()`, `Instant.now()`를 인자 없이 부르지 않는다.
- 요청·응답 DTO는 record로 만들고 이름은 `XxxRequest`, `XxxResponse`로 끝낸다. 엔티티를 응답으로 내보내지 않는다.
- 코드 포맷은 Google Java Format이다. 손으로 맞추지 않고 `./gradlew spotlessApply`를 실행한다. 포맷이 어긋나면 `./gradlew build`와 CI가 실패한다.
- 의존성은 생성자 주입(`@RequiredArgsConstructor`)만 쓴다.
- 엔티티에 `@Setter`, `@Data`를 쓰지 않는다. 연관관계는 `FetchType.LAZY`로 둔다.
- JSON 라이브러리는 Jackson 3이다. ObjectMapper 등은 `tools.jackson.*`에서 import한다.
  `com.fasterxml.jackson.databind.*`는 쓰지 않는다. 어노테이션만 `com.fasterxml.jackson.annotation.*`를 쓴다.

## API 규칙
- 경로는 `/api/v1`로 시작하고, 자원 이름은 복수형 kebab-case를 쓴다.
- controller 메서드는 `ResponseEntity<XxxResponse>`를 반환하고 상태 코드를 여기서 정한다. (`ResponseEntity.ok(...)`, `ResponseEntity.status(HttpStatus.CREATED).body(...)`, 본문이 없으면 `ResponseEntity.noContent().build()`) `@ResponseStatus`는 쓰지 않는다.
- 응답 본문은 DTO 그대로다. `{ "data": ... }` 같은 공통 래퍼를 씌우지 않는다. 목록은 `{ "items": [...] }`로 감싼다.
- 실패는 `BusinessException(ErrorCode)`를 던진다. controller에서 try-catch로 에러 응답을 만들지 않는다.
- 새 에러 코드는 해당 도메인의 `XxxErrorCode` enum에 추가하고, 팀 에러 코드 표에도 추가한다.
- 입력 형식 검증은 요청 DTO의 Bean Validation으로, 업무 규칙 검증은 service와 엔티티에서 한다.
- 방에 속한 API는 service 첫 줄에서 `RoomMemberService.getActiveMember(roomId, userId)`로 멤버인지 확인한다.

## DB 규칙
- 스키마 변경은 `src/main/resources/db/migration`에 Flyway 파일을 새로 추가한다. 이름은 `V<번호>__<설명>.sql`이다.
- 이미 dev 브랜치에 머지된 마이그레이션 파일은 수정하지 않는다.
- `spring.jpa.hibernate.ddl-auto`는 `validate`다. 엔티티를 바꾸면 마이그레이션도 함께 추가한다.
- 마이그레이션 파일 하나에는 DDL을 하나만 둔다. MySQL은 DDL을 롤백하지 못한다.

## 테스트 규칙
- 기능을 추가하거나 고치면 테스트를 함께 추가한다.
- 엔티티와 service의 규칙은 Spring 없이 단위 테스트로 쓴다. (예: `UserTest`)
- DB가 필요한 테스트는 `@Import(TestcontainersConfig.class)`로 Testcontainers의 MySQL을 쓴다. H2를 쓰지 않는다.
- 인증 흐름처럼 필터와 세션까지 확인해야 하는 테스트는 `AuthIntegrationTest`처럼 실제 서버를 띄워 HTTP로 호출한다.
- `@DisplayName`에 검증하는 규칙을 한국어 한 문장으로 적는다.
- 작업을 끝내기 전에 `./gradlew build`를 실행하고, 실제 출력 결과를 보고한다.
- PR과 `dev`, `main` push에서는 `.github/workflows/backend-ci.yml`이 같은 `./gradlew build`를 실행한다.

## 하지 말 것
- API 키와 운영 환경의 비밀번호를 코드나 설정 파일에 적지 않는다. 값은 환경변수로 받는다. 로컬 DB 계정만 예외다.
- 요청받지 않은 의존성을 추가하지 않는다. 필요하면 이유를 먼저 설명한다.
- `build.gradle`에서 Spring Boot가 관리하는 의존성에 버전을 직접 적지 않는다.
- 요청 범위 밖의 파일을 리팩터링하지 않는다.
- `main`, `dev` 브랜치에 직접 커밋하거나 push하지 않는다.

## Git
- 브랜치: `<type>/<이슈번호>-<설명>` (예: `feat/12-room-invite`)
- 커밋: `<type>: <요약>` (type은 feat, fix, refactor, test, docs, chore)
