CREATE TABLE plan_room_member (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    plan_room_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    joined_at    DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    left_at      DATETIME(6),
    created_at   DATETIME(6) NOT NULL,
    modified_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_plan_room_member PRIMARY KEY (id),
    CONSTRAINT uk_plan_room_member_room_user UNIQUE (plan_room_id, user_id),
    CONSTRAINT fk_plan_room_member_room FOREIGN KEY (plan_room_id) REFERENCES plan_room (id),
    CONSTRAINT fk_plan_room_member_user FOREIGN KEY (user_id) REFERENCES users (id),
    KEY idx_plan_room_member_room_active (plan_room_id, left_at),
    KEY idx_plan_room_member_user_active (user_id, left_at)
) ENGINE = InnoDB;
