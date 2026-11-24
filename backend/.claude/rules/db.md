---
paths:
  - "src/main/resources/db/**"
  - "src/main/resources/application*.yml"
  - "src/main/java/**/entity/**"
  - "src/main/java/**/repository/**"
---

# DB 규칙

- 스키마 변경은 `src/main/resources/db/migration`에 Flyway 파일을 새로 추가한다. 이름은 `V<번호>__<설명>.sql`이다.
- `spring.jpa.hibernate.ddl-auto`는 `validate`다. 엔티티를 바꾸면 마이그레이션도 함께 추가한다.
- 마이그레이션 파일 하나에는 DDL을 하나만 둔다. MySQL은 DDL을 롤백하지 못한다.
