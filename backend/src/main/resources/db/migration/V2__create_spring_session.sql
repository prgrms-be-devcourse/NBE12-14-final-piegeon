-- Spring Session JDBC가 세션을 저장하는 테이블.
-- spring-session-jdbc 4.1.1의 org/springframework/session/jdbc/schema-mysql.sql을 옮긴 것이다.
-- 원본은 인덱스를 별도 CREATE INDEX로 만들지만, 파일 하나에 DDL 하나만 두기 위해 테이블 정의 안에 넣었다.
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INT      NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID),
    UNIQUE KEY SPRING_SESSION_IX1 (SESSION_ID),
    KEY SPRING_SESSION_IX2 (EXPIRY_TIME),
    KEY SPRING_SESSION_IX3 (PRINCIPAL_NAME)
) ENGINE = InnoDB ROW_FORMAT = DYNAMIC;
