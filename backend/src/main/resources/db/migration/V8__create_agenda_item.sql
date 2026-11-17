CREATE TABLE agenda_item (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    plan_room_id       BIGINT       NOT NULL,
    subject            VARCHAR(20)  NOT NULL,
    round              INT          NOT NULL,
    title              VARCHAR(50) NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    total_voter        INT          NOT NULL,
    deadline_at        DATETIME(6)  NOT NULL,
    resolved_option_id BIGINT,
    resolved_at        DATETIME(6),
    created_at         DATETIME(6)  NOT NULL,
    modified_at        DATETIME(6)  NOT NULL,
    CONSTRAINT pk_agenda_item PRIMARY KEY (id),
    CONSTRAINT uk_agenda_item_room_subject_round UNIQUE (plan_room_id, subject, round),
    CONSTRAINT fk_agenda_item_room FOREIGN KEY (plan_room_id) REFERENCES plan_room (id),
    -- 회차는 1부터 시작한다
    CONSTRAINT ck_agenda_item_round CHECK (round >= 1),
    -- 퇴장 처리를 중복으로 해도 음수가 되지 않게 막는다
    -- 투표 중(VOTING)인 안건은 투표자가 1명 이상이어야 한다. 0명은 퇴장으로 EXPIRED가 된 안건만 가능하다
    CONSTRAINT ck_agenda_item_total_voter CHECK (
        total_voter >= 0 AND (status <> 'VOTING' OR total_voter >= 1)
    ),
    -- RESOLVED면 결정된 선지와 시각이 반드시 있고, 아니면 둘 다 없다
    CONSTRAINT ck_agenda_item_resolved CHECK (
        (status = 'RESOLVED') = (resolved_option_id IS NOT NULL AND resolved_at IS NOT NULL)
        AND (resolved_option_id IS NULL) = (resolved_at IS NULL)
    ),
    KEY idx_agenda_item_status_deadline (status, deadline_at)
) ENGINE = InnoDB;
