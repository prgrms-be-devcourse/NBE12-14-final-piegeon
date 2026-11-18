-- 하루 안에 놓인 일정 블록. 확정된 장소만 놓는다. (API 명세 B-01~B-05)
-- 같은 장소를 여러 블록에 놓아도 되므로 장소에는 UNIQUE를 걸지 않는다.
-- 같은 날의 순서값은 겹치지 않는다. 순서값을 다시 매길 때는 음수 임시값을 거쳐 두 단계로 바꾼다.
CREATE TABLE schedule_item (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    schedule_day_id    BIGINT       NOT NULL,
    place_candidate_id BIGINT       NOT NULL,
    sort_order         INT          NOT NULL,
    start_time         TIME,
    memo               VARCHAR(200),
    created_at         DATETIME(6)  NOT NULL,
    modified_at        DATETIME(6)  NOT NULL,
    CONSTRAINT pk_schedule_item PRIMARY KEY (id),
    CONSTRAINT uk_schedule_item_day_order UNIQUE (schedule_day_id, sort_order),
    CONSTRAINT fk_schedule_item_schedule_day FOREIGN KEY (schedule_day_id) REFERENCES schedule_day (id),
    CONSTRAINT fk_schedule_item_place_candidate FOREIGN KEY (place_candidate_id) REFERENCES place_candidate (id)
) ENGINE = InnoDB;
