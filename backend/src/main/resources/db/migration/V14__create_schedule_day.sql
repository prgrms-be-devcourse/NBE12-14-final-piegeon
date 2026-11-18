-- 여행방의 하루. 방을 만들 때 여행 일수만큼 생긴다. (API 명세 B-01~B-05)
-- 날짜는 저장하지 않고 "시작일 + (day_index - 1)"로 계산한다.
-- version은 이 날의 블록이 바뀔 때마다 1씩 오르고, 요청의 dayVersion과 비교한다.
CREATE TABLE schedule_day (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    plan_room_id BIGINT      NOT NULL,
    day_index    INT         NOT NULL,
    version      BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    modified_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_schedule_day PRIMARY KEY (id),
    CONSTRAINT uk_schedule_day_room_index UNIQUE (plan_room_id, day_index),
    CONSTRAINT fk_schedule_day_plan_room FOREIGN KEY (plan_room_id) REFERENCES plan_room (id)
) ENGINE = InnoDB;
