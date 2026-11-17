CREATE TABLE survey_response (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    plan_room_member_id  BIGINT       NOT NULL,
    budget              BIGINT,
    stay_type           VARCHAR(20),
    pace                VARCHAR(20),
    act_pref            VARCHAR(100) NOT NULL,
    avoid_note          VARCHAR(100),
    submitted_at        DATETIME(6)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    modified_at         DATETIME(6)  NOT NULL,
    CONSTRAINT pk_survey_response PRIMARY KEY (id),
    CONSTRAINT uk_survey_response_room_member UNIQUE (plan_room_member_id),
    CONSTRAINT fk_survey_response_room_member FOREIGN KEY (plan_room_member_id)
        REFERENCES plan_room_member (id)
) ENGINE = InnoDB;
