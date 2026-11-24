# 착착 (Chak-Chak) 백엔드

그룹 여행 조율 서비스의 API 서버다. 여행방 생성, 설문과 조율, 장소 후보와 투표,
일차별 일정, 최종 확정 순서로 진행되고, 변경은 SSE로 같은 방 멤버에게 전파한다.

## 스택
- Java 21, Spring Boot 4.1.x, Gradle (Kotlin DSL)
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
- 도메인 패키지 안은 controller, service, repository, entity, dto, errorcode로 나눈다.

## 작업별 규칙
아래 규칙은 해당 파일을 다룰 때 읽는다. Claude Code는 `paths`가 맞는 파일을 열거나 고칠 때 자동으로 읽는다. 다른 도구는 작업 전에 해당 파일을 직접 읽는다.

| 작업 | 파일 | 대상 경로 (`backend/` 기준) |
|---|---|---|
| 코드 작성 (계층, 시각, DTO, 포맷, 엔티티, JSON) | `.claude/rules/code.md` | `src/main/java/**` |
| API (경로, 응답, 에러 코드, 검증) | `.claude/rules/api.md` | `controller`, `dto`, `errorcode`, `global/error` |
| DB (Flyway, ddl-auto, 마이그레이션) | `.claude/rules/db.md` | `src/main/resources/db/**`, `application*.yml`, `entity`, `repository` |
| 테스트 | `.claude/rules/test.md` | `src/test/**` |

## 작업 마무리
- 작업을 끝내기 전에 `./gradlew build`를 실행하고, 실제 출력 결과를 보고한다.
- `backend/`가 바뀐 PR과 `dev` push에서는 `.github/workflows/backend-ci.yml`이, `main` 머지에서는 `.github/workflows/deploy.yml`이 같은 `./gradlew build`를 실행한다.

## 하지 말 것
- API 키와 운영 환경의 비밀번호를 코드나 설정 파일에 적지 않는다. 값은 환경변수로 받는다. 로컬 DB 계정만 예외다.
- 요청받지 않은 의존성을 추가하지 않는다. 필요하면 이유를 먼저 설명한다.
- `build.gradle.kts`에서 Spring Boot가 관리하는 의존성에 버전을 직접 적지 않는다.
- 요청 범위 밖의 파일을 리팩터링하지 않는다.
- `main`, `dev` 브랜치에 직접 커밋하거나 push하지 않는다.

## Git
- 브랜치: `<type>/<이슈번호>-<설명>` (예: `feat/12-room-invite`)
- 커밋: `<type>: <요약>` (type은 feat, fix, refactor, test, docs, chore)

## Claude Code

- 파일을 3개 이상 바꾸는 작업은 plan mode로 계획을 먼저 보여준다.

## Agent skills

### Issue tracker

이 저장소의 이슈와 명세는 `.scratch/` 아래 로컬 Markdown으로 관리한다. 자세한 규칙은 `docs/agents/issue-tracker.md`를 참고한다.

### Triage labels

기본 triage 라벨 문자열(`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`)을 사용한다. 자세한 규칙은 `docs/agents/triage-labels.md`를 참고한다.

### Domain docs

이 저장소는 단일 컨텍스트이며 루트 `GLOSSARY.md`와 `docs/adr/`를 사용한다. 자세한 규칙은 `docs/agents/domain.md`를 참고한다.
