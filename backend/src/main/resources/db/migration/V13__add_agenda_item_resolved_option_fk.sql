-- 안건과 선지가 서로를 참조하므로 선지 테이블(V12)을 만든 뒤 FK를 건다.
-- (id, resolved_option_id)로 걸어 결정된 선지가 반드시 이 안건의 선지가 되게 한다. resolved_option_id가 NULL이면 검사하지 않는다.
ALTER TABLE agenda_item
    ADD CONSTRAINT fk_agenda_item_resolved_option
        FOREIGN KEY (id, resolved_option_id) REFERENCES agenda_option (agenda_id, id);
