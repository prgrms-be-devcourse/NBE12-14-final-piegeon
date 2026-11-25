ALTER TABLE plan_room
    ADD KEY idx_plan_room_status_opinion_deadline (status, opinion_deadline_at),
    ADD KEY idx_plan_room_end_date (end_date);
