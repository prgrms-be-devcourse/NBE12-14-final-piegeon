package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.room.service.RoomCleanupScheduler;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

@IntegrationTest
class RoomCleanupIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired private RoomCleanupScheduler scheduler;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    @Test
    @DisplayName("설문 응답이 없고 의견 마감 후 7일이 지나면 방과 모든 자식 데이터가 삭제된다")
    void deletesRoomWithNoResponsesAfterOpinionDeadline() {
        Fixture fixture = createRoom(LocalDate.now(SEOUL).minusDays(25));
        setOpinionDeadline(fixture.roomId(), LocalDateTime.now(clock).minusDays(8));
        seedChildren(fixture);

        scheduler.deleteExpiredRooms();

        assertThat(roomRepository.existsById(fixture.roomId())).isFalse();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from plan_room_member where id = ?",
                                Long.class,
                                fixture.memberId()))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from place_candidate where google_place_id = 'place-test'",
                                Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from schedule_item where memo = 'cleanup-test'",
                                Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from agenda_item where title = 'cleanup-test-agenda'",
                                Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from agenda_option where label = 'cleanup-test-option'",
                                Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from comment where body = 'cleanup-test'",
                                Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from vote where plan_room_member_id = ?",
                                Long.class,
                                fixture.memberId()))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from place_reaction where plan_room_member_id = ?",
                                Long.class,
                                fixture.memberId()))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from survey_response where plan_room_member_id = ?",
                                Long.class,
                                fixture.memberId()))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from change_log where summary = 'cleanup-test'",
                                Long.class))
                .isZero();
    }

    @Test
    @DisplayName("종료일로부터 14일이 지난 방은 삭제된다")
    void deletesRoomAfterEndDateRetention() {
        Fixture fixture = createRoom(LocalDate.now(SEOUL).minusDays(15));
        setOpinionDeadline(fixture.roomId(), LocalDateTime.now(clock).plusDays(30));

        scheduler.deleteExpiredRooms();

        assertThat(roomRepository.existsById(fixture.roomId())).isFalse();
    }

    @Test
    @DisplayName("설문 응답이 있거나 만료 기한이 지나지 않은 방은 유지된다")
    void keepsRoomsThatDoNotMeetCleanupConditions() {
        Fixture responseRoom = createRoom(LocalDate.now(SEOUL).minusDays(5));
        setOpinionDeadline(responseRoom.roomId(), LocalDateTime.now(clock).minusDays(8));
        insertSurveyResponse(responseRoom.memberId());

        Fixture deadlineRoom = createRoom(LocalDate.now(SEOUL).minusDays(5));
        setOpinionDeadline(deadlineRoom.roomId(), LocalDateTime.now(clock).minusDays(6));

        Fixture endDateRoom = createRoom(LocalDate.now(SEOUL).minusDays(14));
        setOpinionDeadline(endDateRoom.roomId(), LocalDateTime.now(clock).plusDays(30));

        scheduler.deleteExpiredRooms();

        assertThat(roomRepository.existsById(responseRoom.roomId())).isTrue();
        assertThat(roomRepository.existsById(deadlineRoom.roomId())).isTrue();
        assertThat(roomRepository.existsById(endDateRoom.roomId())).isTrue();
    }

    private Fixture createRoom(LocalDate endDate) {
        User user =
                userRepository.save(User.create(UUID.randomUUID() + "@example.com", "테스트", "hash"));
        LocalDate startDate = endDate.minusDays(1);
        Room room =
                roomRepository.save(
                        Room.create(
                                "삭제 테스트",
                                "부산",
                                startDate,
                                endDate,
                                UUID.randomUUID().toString().substring(0, 8),
                                RoomStatus.SURVEYING,
                                clock));
        RoomMember member = roomMemberRepository.save(RoomMember.create(room, user, clock));
        return new Fixture(room.getId(), member.getId());
    }

    private void setOpinionDeadline(Long roomId, LocalDateTime deadline) {
        jdbcTemplate.update(
                "update plan_room set opinion_deadline_at = ? where id = ?", deadline, roomId);
    }

    private void insertSurveyResponse(Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        jdbcTemplate.update(
                "insert into survey_response (plan_room_member_id, budget, stay_type, pace, act_pref, avoid_note, submitted_at, created_at, modified_at) values (?, 10000, 'HOTEL', 'SLOW', 'FOOD', '', ?, ?, ?)",
                memberId,
                now,
                now,
                now);
    }

    private void seedChildren(Fixture fixture) {
        Long roomId = fixture.roomId();
        Long memberId = fixture.memberId();
        LocalDateTime now = LocalDateTime.now(clock);
        jdbcTemplate.update(
                "insert into place_candidate (plan_room_id, google_place_id, name, suggested_by, created_at, modified_at) values (?, 'place-test', '장소', ?, ?, ?)",
                roomId,
                memberId,
                now,
                now);
        Long placeId =
                jdbcTemplate.queryForObject(
                        "select id from place_candidate where google_place_id = 'place-test'",
                        Long.class);
        jdbcTemplate.update(
                "insert into schedule_day (plan_room_id, day_index, version, created_at, modified_at) values (?, 1, 0, ?, ?)",
                roomId,
                now,
                now);
        Long dayId =
                jdbcTemplate.queryForObject(
                        "select id from schedule_day where plan_room_id = ?", Long.class, roomId);
        jdbcTemplate.update(
                "insert into schedule_item (schedule_day_id, place_candidate_id, sort_order, memo, created_at, modified_at) values (?, ?, 0, 'cleanup-test', ?, ?)",
                dayId,
                placeId,
                now,
                now);
        jdbcTemplate.update(
                "insert into agenda_item (plan_room_id, subject, round, title, status, total_voter, deadline_at, created_at, modified_at) values (?, 'BUDGET', 1, 'cleanup-test-agenda', 'VOTING', 1, ?, ?, ?)",
                roomId,
                now.plusDays(1),
                now,
                now);
        Long agendaId =
                jdbcTemplate.queryForObject(
                        "select id from agenda_item where plan_room_id = ?", Long.class, roomId);
        jdbcTemplate.update(
                "insert into agenda_option (agenda_id, label, value, display_index, created_at, modified_at) values (?, 'cleanup-test-option', 'VALUE', 0, ?, ?)",
                agendaId,
                now,
                now);
        Long optionId =
                jdbcTemplate.queryForObject(
                        "select id from agenda_option where agenda_id = ?", Long.class, agendaId);
        jdbcTemplate.update(
                "update agenda_item set status = 'RESOLVED', resolved_option_id = ?, resolved_at = ? where id = ?",
                optionId,
                now,
                agendaId);
        jdbcTemplate.update(
                "insert into agenda_item (plan_room_id, subject, round, title, status, total_voter, deadline_at, created_at, modified_at) values (?, 'STAY_TYPE', 1, 'cleanup-test-expired-agenda', 'EXPIRED', 0, ?, ?, ?)",
                roomId,
                now.plusDays(1),
                now,
                now);
        jdbcTemplate.update(
                "insert into vote (plan_room_member_id, agenda_id, agenda_option_id, created_at, modified_at) values (?, ?, ?, ?, ?)",
                memberId,
                agendaId,
                optionId,
                now,
                now);
        jdbcTemplate.update(
                "insert into comment (plan_room_member_id, agenda_id, body, created_at, modified_at) values (?, ?, 'cleanup-test', ?, ?)",
                memberId,
                agendaId,
                now,
                now);
        jdbcTemplate.update(
                "insert into place_reaction (place_candidate_id, plan_room_member_id, reaction, created_at, modified_at) values (?, ?, 'WANT', ?, ?)",
                placeId,
                memberId,
                now,
                now);
        jdbcTemplate.update(
                "insert into survey_response (plan_room_member_id, act_pref, avoid_note, submitted_at, created_at, modified_at) values (?, 'FOOD', '', ?, ?, ?)",
                memberId,
                now,
                now,
                now);
        jdbcTemplate.update(
                "insert into change_log (plan_room_id, plan_room_member_id, entity_type, entity_id, event_kind, action_status, summary, created_at, modified_at) values (?, ?, 'ROOM', ?, 'UPDATED', 'SUCCESS', 'cleanup-test', ?, ?)",
                roomId,
                memberId,
                roomId,
                now,
                now);
    }

    private record Fixture(Long roomId, Long memberId) {}
}
