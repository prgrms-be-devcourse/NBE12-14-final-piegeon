package world.chakchak.domain.room.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.room.repository.RoomRepository;

/** 만료된 여행방과 그 자식 데이터를 방별 트랜잭션으로 삭제한다. */
@Service
@RequiredArgsConstructor
public class RoomCleanupService {

    private final JdbcTemplate jdbcTemplate;
    private final RoomRepository roomRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteRoom(Long roomId) {
        if (!roomRepository.existsById(roomId)) {
            return;
        }

        // 1. 멤버와 방을 참조하는 말단 데이터
        jdbcTemplate.update(
                "delete from comment where plan_room_member_id in (select id from plan_room_member where plan_room_id = ?)",
                roomId);
        jdbcTemplate.update(
                "delete from vote where plan_room_member_id in (select id from plan_room_member where plan_room_id = ?)",
                roomId);
        jdbcTemplate.update(
                "delete from place_reaction where plan_room_member_id in (select id from plan_room_member where plan_room_id = ?)",
                roomId);
        jdbcTemplate.update(
                "delete from schedule_item where schedule_day_id in (select id from schedule_day where plan_room_id = ?)",
                roomId);

        // 2. agenda_item과 agenda_option 사이의 순환 FK를 먼저 해제
        jdbcTemplate.update(
                "update agenda_item set status = 'VOTING', resolved_option_id = null, resolved_at = null where plan_room_id = ?",
                roomId);

        // 3. 방에 직접 속하거나 멤버를 참조하는 데이터
        jdbcTemplate.update(
                "delete from agenda_option where agenda_id in (select id from agenda_item where plan_room_id = ?)",
                roomId);
        jdbcTemplate.update("delete from agenda_item where plan_room_id = ?", roomId);
        jdbcTemplate.update("delete from schedule_day where plan_room_id = ?", roomId);
        jdbcTemplate.update("delete from place_candidate where plan_room_id = ?", roomId);
        jdbcTemplate.update(
                "delete from survey_response where plan_room_member_id in (select id from plan_room_member where plan_room_id = ?)",
                roomId);
        jdbcTemplate.update("delete from change_log where plan_room_id = ?", roomId);

        // 4. 마지막으로 멤버와 방 삭제
        jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
        jdbcTemplate.update("delete from plan_room where id = ?", roomId);
    }
}
