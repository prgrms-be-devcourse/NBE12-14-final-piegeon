package world.chakchak.domain.agenda;

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
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.entity.FinalPlanChoice;
import world.chakchak.domain.agenda.entity.Vote;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.agenda.repository.AgendaOptionRepository;
import world.chakchak.domain.agenda.repository.VoteRepository;
import world.chakchak.domain.agenda.service.AgendaService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.room.service.RoomLeaveService;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/**
 * D-1 최종 집계를 실제 DB로 확인한다.
 *
 * <p>서버는 실제 시각으로 동작하므로 D-1 방은 출발일을 내일로 두고, 안건과 표는 며칠 전 시각으로 만든다.
 */
@IntegrationTest
class AgendaFinalTallyIntegrationTest {

    @Autowired private AgendaService agendaService;
    @Autowired private RoomLeaveService roomLeaveService;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private AgendaOptionRepository agendaOptionRepository;
    @Autowired private VoteRepository voteRepository;
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
                    "delete from vote where agenda_id in (" + agendasOfRoom + ")", roomId);
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
            jdbcTemplate.update("delete from change_log where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room where id = ?", roomId);
        }
    }

    private LocalDateTime past() {
        return LocalDateTime.now(clock).minusDays(5);
    }

    private Room saveRoom(LocalDate startDate, RoomStatus status) {
        Room room =
                roomRepository.saveAndFlush(
                        Room.create(
                                "집계 방",
                                "부산",
                                startDate,
                                startDate.plusDays(1),
                                UUID.randomUUID().toString().substring(0, 8),
                                status,
                                clock));
        roomIds.add(room.getId());
        return room;
    }

    /** 출발일이 내일이라 D-1이 된, 설문이 마감된 방 */
    private Room surveyClosedRoomAtFinalTally() {
        Room room = saveRoom(LocalDate.now(clock).plusDays(1), RoomStatus.SURVEYING);
        room.closeSurveyIfReady(1, 1, past());
        return roomRepository.saveAndFlush(room);
    }

    private RoomMember saveMember(Room room, String name) {
        User user =
                userRepository.saveAndFlush(
                        User.create(UUID.randomUUID() + "@example.com", name, "hash"));
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }

    private AgendaItem saveAgenda(Room room, AgendaSubject subject, int totalVoter) {
        LocalDateTime openedAt = past();
        return agendaItemRepository.saveAndFlush(
                AgendaItem.create(
                        room, subject, 1, "안건 제목", totalVoter, openedAt, openedAt.plusDays(1)));
    }

    private void saveVote(AgendaItem agenda, AgendaOption option, RoomMember member) {
        voteRepository.saveAndFlush(Vote.create(agenda, option, member, past()));
    }

    /** 만장일치로 이미 결정된 이동 강도 항목 */
    private void saveResolvedPace(Room room) {
        AgendaItem pace = saveAgenda(room, AgendaSubject.PACE, 2);
        AgendaOption option =
                agendaOptionRepository.saveAndFlush(AgendaOption.pace(pace, Pace.NORMAL, 0));
        pace.resolve(option, past());
        agendaItemRepository.saveAndFlush(pace);
    }

    private AgendaItem reload(AgendaItem agenda) {
        return agendaItemRepository.findById(agenda.getId()).orElseThrow();
    }

    private RoomStatus statusOf(Room room) {
        return roomRepository.findById(room.getId()).orElseThrow().getStatus();
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("투표 중 항목과 미결 항목을 최다 득표로 결정하면 방이 PLANNING이 된다")
            void talliesSurveyAndStartsPlanning() {
                Room room = surveyClosedRoomAtFinalTally();
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jieun = saveMember(room, "지은");
                RoomMember hyunwoo = saveMember(room, "현우");
                AgendaItem budget = saveAgenda(room, AgendaSubject.BUDGET, 3);
                AgendaOption low =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.budget(budget, 600_000L, 0, null));
                AgendaOption high =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.budget(budget, 1_000_000L, 1, null));
                saveVote(budget, high, minsu);
                saveVote(budget, high, jieun);
                saveVote(budget, low, hyunwoo);
                AgendaItem stay = saveAgenda(room, AgendaSubject.STAY, 3);
                AgendaOption hotel =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.stay(stay, StayType.HOTEL, 0));
                saveVote(stay, hotel, minsu);
                stay.expire();
                agendaItemRepository.saveAndFlush(stay);
                saveResolvedPace(room);

                assertThat(agendaService.findRoomIdsAtFinalTally()).contains(room.getId());
                agendaService.runFinalTally(room.getId());

                assertThat(reload(budget).getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(reload(budget).getResolvedOptionId()).isEqualTo(high.getId());
                assertThat(reload(stay).getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(reload(stay).getResolvedOptionId()).isEqualTo(hotel.getId());
                assertThat(statusOf(room)).isEqualTo(RoomStatus.PLANNING);
                assertThat(agendaService.findRoomIdsAtFinalTally()).doesNotContain(room.getId());
            }

            @Test
            @DisplayName("미결 회차에 남은 나간 멤버의 표를 빼고 남은 멤버의 표로 결정한다")
            void excludesLeftMemberVotes() {
                Room room = surveyClosedRoomAtFinalTally();
                RoomMember minsu = saveMember(room, "민수");
                RoomMember leftA = saveMember(room, "나간A");
                RoomMember leftB = saveMember(room, "나간B");
                AgendaItem budget = saveAgenda(room, AgendaSubject.BUDGET, 3);
                AgendaOption low =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.budget(budget, 600_000L, 0, null));
                AgendaOption high =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.budget(budget, 1_000_000L, 1, null));
                saveVote(budget, low, leftA);
                saveVote(budget, low, leftB);
                saveVote(budget, high, minsu);
                budget.expire();
                agendaItemRepository.saveAndFlush(budget);
                leftA.leave(LocalDateTime.now(clock));
                leftB.leave(LocalDateTime.now(clock));
                roomMemberRepository.saveAllAndFlush(List.of(leftA, leftB));
                AgendaItem stay = saveAgenda(room, AgendaSubject.STAY, 1);
                AgendaOption hotel =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.stay(stay, StayType.HOTEL, 0));
                saveVote(stay, hotel, minsu);
                saveResolvedPace(room);

                agendaService.runFinalTally(room.getId());

                assertThat(reload(budget).getResolvedOptionId()).isEqualTo(high.getId());
                assertThat(statusOf(room)).isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("표가 0개인 항목이 있으면 그 항목은 미결로 끝나고 다른 항목은 결정되며 방이 EXPIRED가 된다")
            void expiresRoomWithZeroVoteItem() {
                Room room = surveyClosedRoomAtFinalTally();
                RoomMember minsu = saveMember(room, "민수");
                AgendaItem budget = saveAgenda(room, AgendaSubject.BUDGET, 2);
                AgendaOption low =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.budget(budget, 600_000L, 0, null));
                saveVote(budget, low, minsu);
                AgendaItem stay = saveAgenda(room, AgendaSubject.STAY, 2);
                agendaOptionRepository.saveAndFlush(AgendaOption.stay(stay, StayType.HOTEL, 0));
                saveResolvedPace(room);

                agendaService.runFinalTally(room.getId());

                assertThat(reload(budget).getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(reload(stay).getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                assertThat(statusOf(room)).isEqualTo(RoomStatus.EXPIRED);
            }

            @Test
            @DisplayName("집계로 방이 EXPIRED가 되면 투표 중인 자유 안건도 미결로 끝난다")
            void expiresVotingCustomAgendaWithRoom() {
                Room room = surveyClosedRoomAtFinalTally();
                AgendaItem budget = saveAgenda(room, AgendaSubject.BUDGET, 2);
                agendaOptionRepository.saveAndFlush(AgendaOption.budget(budget, 600_000L, 0, null));
                saveResolvedPace(room);
                AgendaItem custom = saveAgenda(room, AgendaSubject.CUSTOM, 2);

                agendaService.runFinalTally(room.getId());

                assertThat(statusOf(room)).isEqualTo(RoomStatus.EXPIRED);
                assertThat(reload(custom).getStatus()).isEqualTo(AgendaStatus.EXPIRED);
            }

            @Test
            @DisplayName("투표 중 항목과 미결 항목의 1위가 동점이면 두 항목 모두 미결로 남고 방이 EXPIRED가 된다")
            void expiresRoomWithTiedItems() {
                Room room = surveyClosedRoomAtFinalTally();
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jieun = saveMember(room, "지은");
                AgendaItem budget = saveAgenda(room, AgendaSubject.BUDGET, 2);
                AgendaOption low =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.budget(budget, 600_000L, 0, null));
                AgendaOption high =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.budget(budget, 1_000_000L, 1, null));
                saveVote(budget, low, minsu);
                saveVote(budget, high, jieun);
                AgendaItem stay = saveAgenda(room, AgendaSubject.STAY, 2);
                AgendaOption hotel =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.stay(stay, StayType.HOTEL, 0));
                AgendaOption personal =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.stay(stay, StayType.PERSONAL, 1));
                saveVote(stay, hotel, minsu);
                saveVote(stay, personal, jieun);
                stay.expire();
                agendaItemRepository.saveAndFlush(stay);
                saveResolvedPace(room);

                agendaService.runFinalTally(room.getId());

                assertThat(reload(budget).getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                assertThat(reload(budget).getResolvedOptionId()).isNull();
                assertThat(reload(stay).getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                assertThat(reload(stay).getResolvedOptionId()).isNull();
                assertThat(statusOf(room)).isEqualTo(RoomStatus.EXPIRED);
                assertThat(agendaService.findRoomIdsAtFinalTally()).doesNotContain(room.getId());
            }

            @Test
            @DisplayName("최종 투표는 미투표자가 있어도 낸 표가 모두 동의면 방이 FINALIZED가 된다")
            void finalizesReviewingRoom() {
                Room room = saveRoom(LocalDate.now(clock).plusDays(1), RoomStatus.REVIEWING);
                RoomMember minsu = saveMember(room, "민수");
                saveMember(room, "미투표자");
                AgendaItem finalPlan = saveAgenda(room, AgendaSubject.FINAL_PLAN, 2);
                AgendaOption agree =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.finalPlan(finalPlan, FinalPlanChoice.AGREE));
                agendaOptionRepository.saveAndFlush(
                        AgendaOption.finalPlan(finalPlan, FinalPlanChoice.REJECT));
                saveVote(finalPlan, agree, minsu);

                agendaService.runFinalTally(room.getId());

                assertThat(reload(finalPlan).getResolvedOptionId()).isEqualTo(agree.getId());
                assertThat(statusOf(room)).isEqualTo(RoomStatus.FINALIZED);
            }

            @Test
            @DisplayName("최종 투표 중 마지막 멤버가 나가 REVIEWING에 남은 방은 D-1에 PLANNING으로 돌아간다")
            void returnsAbandonedReviewingRoomToPlanning() {
                Room room = saveRoom(LocalDate.now(clock).plusDays(1), RoomStatus.REVIEWING);
                RoomMember minsu = saveMember(room, "민수");
                AgendaItem finalPlan = saveAgenda(room, AgendaSubject.FINAL_PLAN, 1);
                AgendaOption agree =
                        agendaOptionRepository.saveAndFlush(
                                AgendaOption.finalPlan(finalPlan, FinalPlanChoice.AGREE));
                saveVote(finalPlan, agree, minsu);

                roomLeaveService.leave(room.getId(), minsu.getUser().getId());

                assertThat(reload(finalPlan).getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                assertThat(statusOf(room)).isEqualTo(RoomStatus.REVIEWING);

                agendaService.runFinalTally(room.getId());

                assertThat(statusOf(room)).isEqualTo(RoomStatus.PLANNING);
                assertThat(agendaService.findRoomIdsAtFinalTally()).doesNotContain(room.getId());
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("D-1까지 설문이 마감되지 않은 방은 안건 없이 EXPIRED가 된다")
            void expiresRoomWithOpenSurvey() {
                Room room = saveRoom(LocalDate.now(clock).plusDays(1), RoomStatus.SURVEYING);
                saveMember(room, "민수");

                agendaService.runFinalTally(room.getId());

                assertThat(statusOf(room)).isEqualTo(RoomStatus.EXPIRED);
            }
        }
    }
}
