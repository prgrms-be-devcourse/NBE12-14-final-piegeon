---
paths:
  - "src/test/**"
---

# 테스트 규칙

- 기능을 추가하거나 고치면 테스트를 함께 추가한다.
- 엔티티와 service의 규칙은 Spring 없이 단위 테스트로 쓴다. (예: `UserTest`)
- DB가 필요한 테스트는 `@Import(TestcontainersConfig.class)`로 Testcontainers의 MySQL을 쓴다. H2를 쓰지 않는다.
- 인증 흐름처럼 필터와 세션까지 확인해야 하는 테스트는 `AuthIntegrationTest`처럼 실제 서버를 띄워 HTTP로 호출한다.
- `@DisplayName`에 검증하는 규칙을 한국어 한 문장으로 적는다.
- 테스트 클래스 내부는 `@Nested`를 사용해 1단계로 CRUD 기능별(`Create`, `Read`, `Update`, `Delete` 등), 2단계로 `Success`와 `Failure` 케이스별로 계층을 나눈다.
