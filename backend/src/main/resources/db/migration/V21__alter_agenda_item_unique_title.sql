-- 자유 안건(CUSTOM)은 subject가 하나라 제목으로 주제를 구분한다. 회차는 (방, subject, 제목)마다 1부터 센다
-- 설문 안건과 최종 확정은 제목이 주제마다 하나로 고정이라 지금과 같은 제약이 된다
-- 새 인덱스도 plan_room_id로 시작해 fk_agenda_item_room을 받쳐 주므로 한 문장에서 바꾼다
ALTER TABLE agenda_item
    DROP INDEX uk_agenda_item_room_subject_round,
    ADD CONSTRAINT uk_agenda_item_room_subject_title_round UNIQUE (plan_room_id, subject, title, round);
