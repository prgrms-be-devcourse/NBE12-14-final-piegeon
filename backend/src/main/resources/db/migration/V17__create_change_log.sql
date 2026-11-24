CREATE TABLE change_log (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    plan_room_id       BIGINT       NOT NULL,
    plan_room_member_id BIGINT,
    entity_type        VARCHAR(32)  NOT NULL,
    entity_id          BIGINT       NOT NULL,
    event_kind         VARCHAR(32)  NOT NULL,
    action_status      VARCHAR(16)  NOT NULL,
    summary            TEXT         NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    modified_at        DATETIME(6)  NOT NULL,
    CONSTRAINT pk_change_log PRIMARY KEY (id),
    CONSTRAINT fk_change_log_room FOREIGN KEY (plan_room_id)
        REFERENCES plan_room (id) ON DELETE CASCADE,
    CONSTRAINT fk_change_log_actor FOREIGN KEY (plan_room_member_id)
        REFERENCES plan_room_member (id),
    KEY idx_change_log_room_id (plan_room_id, id),
    KEY idx_change_log_room_created_at (plan_room_id, created_at)
) ENGINE = InnoDB;
