-- 장소 "동의"를 "반응(WANT/NOPE)"으로 바꾼다. (API 명세 P-04·P-05·P-10)
-- 기존 행은 모두 동의였으므로 WANT로 채운다. 이후에는 엔티티가 항상 값을 넣는다.
-- FK 이름(fk_place_agreement_...)은 MySQL에서 이름만 바꿀 수 없어 그대로 둔다. 동작에는 영향이 없다.
ALTER TABLE place_agreement
    RENAME TO place_reaction,
    ADD COLUMN reaction VARCHAR(10) NOT NULL DEFAULT 'WANT' AFTER plan_room_member_id,
    RENAME INDEX uk_place_agreement_candidate_member TO uk_place_reaction_candidate_member;
