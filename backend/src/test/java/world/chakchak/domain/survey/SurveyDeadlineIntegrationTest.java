package world.chakchak.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;
import world.chakchak.domain.survey.service.SurveyDeadlineService;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/** 13-1 의견 마감 처리를 실제 DB로 확인한다. */
@IntegrationTest
class SurveyDeadlineIntegrationTest {

    @Autowired private SurveyDeadlineService surveyDeadlineService;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private SurveyResponseRepository surveyResponseRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    private final List<Long> roomIds = new ArrayList<>();

    // FK 순서대로 지운다. 안건과 결정된 선지가 서로를 참조하므로 그 연결을 먼저 끊는다 (명세 13-3)
    @AfterEach
    void tearDown() {
        for (Long roomId : roomIds) {
            String agendasOfRoom = "select id from agenda_item where plan_room_id = ?";
            String membersOfRoom = "select id from plan_room_member where plan_room_id = ?";
            jdbcTemplate.update(
                    "update agenda_item set resolved_option_id = null, resolved_at = null,"
                            + " status = 'EXPIRED' where plan_room_id = ?",
                    roomId);
            jdbcTemplate.update(
                    "delete from agenda_option where agenda_id in (" + agendasOfRoom + ")", roomId);
            jdbcTemplate.update("delete from agenda_item where plan_room_id = ?", roomId);
            jdbcTemplate.update(
                    "delete from survey_response where plan_room_member_id in ("
                            + membersOfRoom
                            + ")",
                    roomId);
            jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room where id = ?", roomId);
        }
    }

    private Room saveRoom() {
        LocalDate startDate = LocalDate.now(clock).plusDays(30);
        Room room =
                roomRepository.saveAndFlush(
                        Room.create(
                                "설문 마감 방",
                                "부산",
                                startDate,
                                startDate.plusDays(1),
                                UUID.randomUUID().toString().substring(0, 8),
                                RoomStatus.SURVEYING,
                                clock));
        roomIds.add(room.getId());
        return room;
    }

    private RoomMember saveMember(Room room, String name) {
        User user =
                userRepository.saveAndFlush(
                        User.create(UUID.randomUUID() + "@example.com", name, "hash"));
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }

    private SurveyResponse answer(RoomMember member, long budget) {
        return SurveyResponse.createForSurveying(
                member,
                budget,
                StayType.HOTEL,
                Pace.NORMAL,
                List.of(ActivityCode.FOOD),
                "없음",
                clock);
    }

    private void passOpinionDeadline(Room room) {
        jdbcTemplate.update(
                "update plan_room set opinion_deadline_at = ? where id = ?",
                LocalDateTime.now(clock).minusMinutes(1),
                room.getId());
    }

    private Room reload(Room room) {
        return roomRepository.findById(room.getId()).orElseThrow();
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("의견 마감이 지난 방은 미제출자가 있어도 설문이 마감되고 1회차 안건이 열린다")
            void closesOverdueSurveyAndOpensAgendas() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jieun = saveMember(room, "지은");
                saveMember(room, "미제출자");
                surveyResponseRepository.saveAndFlush(answer(minsu, 600_000L));
                surveyResponseRepository.saveAndFlush(answer(jieun, 1_000_000L));
                passOpinionDeadline(room);

                assertThat(surveyDeadlineService.findRoomIdsPastOpinionDeadline())
                        .contains(room.getId());
                surveyDeadlineService.closeOverdueSurvey(room.getId());

                assertThat(reload(room).getSurveyClosedAt()).isNotNull();
                AgendaItem budget =
                        agendaItemRepository
                                .findTopByRoomIdAndSubjectOrderByRoundDesc(
                                        room.getId(), AgendaSubject.BUDGET)
                                .orElseThrow();
                assertThat(budget.getStatus()).isEqualTo(AgendaStatus.VOTING);
                assertThat(budget.getTotalVoter()).isEqualTo(3);
                assertThat(reload(room).getStatus()).isEqualTo(RoomStatus.SURVEYING);
                assertThat(surveyDeadlineService.findRoomIdsPastOpinionDeadline())
                        .doesNotContain(room.getId());
            }

            @Test
            @DisplayName("의견 마감이 지났는데 제출이 0건이면 방이 EXPIRED가 되고 안건이 열리지 않는다")
            void expiresRoomWithoutResponses() {
                Room room = saveRoom();
                saveMember(room, "민수");
                passOpinionDeadline(room);

                surveyDeadlineService.closeOverdueSurvey(room.getId());

                assertThat(reload(room).getStatus()).isEqualTo(RoomStatus.EXPIRED);
                assertThat(
                                agendaItemRepository.findTopByRoomIdAndSubjectOrderByRoundDesc(
                                        room.getId(), AgendaSubject.BUDGET))
                        .isEmpty();
            }
        }
    }
}
