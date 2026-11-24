CREATE TABLE plan_room (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    name                VARCHAR(50) NOT NULL,
    destination         VARCHAR(50) NOT NULL,
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    invite_code         VARCHAR(8)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    opinion_deadline_at DATETIME(6),
    survey_closed_at    DATETIME(6),
    finalized_at        DATETIME(6),
    created_at          DATETIME(6)  NOT NULL,
    modified_at         DATETIME(6)  NOT NULL,
    CONSTRAINT pk_plan_room PRIMARY KEY (id),
    CONSTRAINT uk_plan_room_invite_code UNIQUE (invite_code),
    KEY idx_plan_room_status (status)
) ENGINE = InnoDB;
