CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    name          VARCHAR(30)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    modified_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB;
