CREATE TABLE place_agreement (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    place_candidate_id  BIGINT      NOT NULL,
    plan_room_member_id BIGINT      NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    modified_at         DATETIME(6) NOT NULL,
    CONSTRAINT pk_place_agreement PRIMARY KEY (id),
    CONSTRAINT uk_place_agreement_candidate_member UNIQUE (place_candidate_id, plan_room_member_id),
    CONSTRAINT fk_place_agreement_place_candidate FOREIGN KEY (place_candidate_id) REFERENCES place_candidate (id),
    CONSTRAINT fk_place_agreement_plan_room_member FOREIGN KEY (plan_room_member_id) REFERENCES plan_room_member (id)
) ENGINE = InnoDB;
