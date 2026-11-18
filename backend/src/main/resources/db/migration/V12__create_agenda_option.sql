CREATE TABLE agenda_option (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    agenda_id     BIGINT      NOT NULL,
    label         VARCHAR(50) NOT NULL,
    value         VARCHAR(20) NOT NULL,
    display_index INT         NOT NULL,
    proposed_by   BIGINT,  -- V-07로 멤버가 추가한 예산 선지만 값이 있음. 설문·최종 확정 선지는 NULL
    created_at    DATETIME(6) NOT NULL,
    modified_at   DATETIME(6) NOT NULL,
    CONSTRAINT pk_agenda_option PRIMARY KEY (id),
    -- 같은 금액·같은 값의 선지는 안건마다 하나 (V-07 동시 추가의 마지막 방어선)
    CONSTRAINT uk_agenda_option_agenda_value UNIQUE (agenda_id, value),
    -- (안건, 선지) 짝을 참조하는 복합 FK용. 결정된 선지·표가 다른 안건의 선지를 가리키지 못하게 한다
    CONSTRAINT uk_agenda_option_agenda_id UNIQUE (agenda_id, id),
    CONSTRAINT fk_agenda_option_agenda FOREIGN KEY (agenda_id) REFERENCES agenda_item (id),
    CONSTRAINT fk_agenda_option_proposed_by FOREIGN KEY (proposed_by) REFERENCES plan_room_member (id),
    -- 선지 순서는 0 이상부터 시작된다.
    CONSTRAINT ck_agenda_option_display_index CHECK (display_index >= 0)
) ENGINE = InnoDB;
