CREATE TABLE place_candidate (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    plan_room_id    BIGINT       NOT NULL,
    google_place_id VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    place_type      VARCHAR(50),
    address         VARCHAR(255),
    suggested_by    BIGINT       NOT NULL,
    confirmed_at    DATETIME(6),
    created_at      DATETIME(6)  NOT NULL,
    modified_at     DATETIME(6)  NOT NULL,
    CONSTRAINT pk_place_candidate PRIMARY KEY (id),
    CONSTRAINT uk_place_candidate_room_google UNIQUE (plan_room_id, google_place_id),
    CONSTRAINT fk_place_candidate_plan_room FOREIGN KEY (plan_room_id) REFERENCES plan_room (id),
    CONSTRAINT fk_place_candidate_suggested_by FOREIGN KEY (suggested_by) REFERENCES plan_room_member (id)
) ENGINE = InnoDB;
