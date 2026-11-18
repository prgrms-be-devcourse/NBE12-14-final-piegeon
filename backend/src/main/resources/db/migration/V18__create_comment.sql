CREATE TABLE comment (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    plan_room_member_id BIGINT       NOT NULL,
    agenda_id           BIGINT,
    place_candidate_id  BIGINT,
    body                VARCHAR(500) NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    modified_at         DATETIME(6)  NOT NULL,
    CONSTRAINT pk_comment PRIMARY KEY (id),
    CONSTRAINT fk_comment_member FOREIGN KEY (plan_room_member_id) REFERENCES plan_room_member (id),
    CONSTRAINT fk_comment_agenda FOREIGN KEY (agenda_id) REFERENCES agenda_item (id),
    CONSTRAINT fk_comment_place_candidate FOREIGN KEY (place_candidate_id) REFERENCES place_candidate (id),
    -- 안건 코멘트(V-06)와 장소 코멘트(P-09) 중 정확히 하나
    CONSTRAINT ck_comment_target CHECK ((agenda_id IS NULL) <> (place_candidate_id IS NULL))
) ENGINE = InnoDB;
