CREATE TABLE vote (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    plan_room_member_id BIGINT      NOT NULL,
    agenda_id           BIGINT      NOT NULL,
    agenda_option_id    BIGINT      NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    modified_at         DATETIME(6) NOT NULL,
    CONSTRAINT pk_vote PRIMARY KEY (id),
    -- 멤버당 안건 하나에 1표. 표를 바꾸면 UPDATE한다 (V-03)
    CONSTRAINT uk_vote_agenda_member UNIQUE (agenda_id, plan_room_member_id),
    CONSTRAINT fk_vote_member FOREIGN KEY (plan_room_member_id) REFERENCES plan_room_member (id),
    CONSTRAINT fk_vote_agenda FOREIGN KEY (agenda_id) REFERENCES agenda_item (id),
    -- (안건, 선지) 짝으로 걸어 표가 다른 안건의 선지를 가리키지 못하게 한다 (V12의 uk_agenda_option_agenda_id)
    CONSTRAINT fk_vote_option FOREIGN KEY (agenda_id, agenda_option_id)
        REFERENCES agenda_option (agenda_id, id)
) ENGINE = InnoDB;
