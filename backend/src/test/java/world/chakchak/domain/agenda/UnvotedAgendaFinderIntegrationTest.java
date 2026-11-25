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
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.entity.Vote;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.agenda.repository.AgendaOptionRepository;
import world.chakchak.domain.agenda.repository.VoteRepository;
import world.chakchak.domain.room.dto.SummaryResponse.UnvotedAgenda;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.room.service.UnvotedAgendaProvider;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/**
 * 비접속 요약(H-02)의 "투표 안 한 안건"을 실제 DB로 확인한다.
 *
 * <p>인터페이스로 주입받아, 빈 목록을 돌려주던 기본 구현이 아니라 투표 도메인의 구현이 쓰이는지도 함께 확인한다.
 */
@IntegrationTest
class UnvotedAgendaFinderIntegrationTest {

    @Autowired private UnvotedAgendaProvider unvotedAgendaProvider;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private AgendaOptionRepository agendaOptionRepository;
    @Autowired private VoteRepository voteRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    private final List<Long> roomIds = new ArrayList<>();

    // FK 순서대로 지운다. 안건과 결정된 선지가 서로를 참조하므로 그 연결을 먼저 끊는다
    @AfterEach
    void tearDown() {
        for (Long roomId : roomIds) {
            String agendasOfRoom = "select id from agenda_item where plan_room_id = ?";
            jdbcTemplate.update(
                    "delete from vote where agenda_id in (" + agendasOfRoom + ")", roomId);
            jdbcTemplate.update(
                    "update agenda_item set resolved_option_id = null, resolved_at = null,"
                            + " status = 'EXPIRED' where plan_room_id = ?",
                    roomId);
            jdbcTemplate.update(
                    "delete from agenda_option where agenda_id in (" + agendasOfRoom + ")", roomId);
            jdbcTemplate.update("delete from agenda_item where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room where id = ?", roomId);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private Room saveRoom() {
        LocalDate startDate = LocalDate.now(clock).plusDays(10);
        Room room =
                roomRepository.saveAndFlush(
                        Room.create(
                                "투표 안 한 안건",
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

    /** 투표 중인 안건. 생성 시각은 저장한 시각이다 */
    private AgendaItem saveVoting(
            Room room, AgendaSubject subject, int round, String title, LocalDateTime deadlineAt) {
        return agendaItemRepository.saveAndFlush(
                AgendaItem.create(room, subject, round, title, 2, now().minusDays(1), deadlineAt));
    }

    private AgendaOption savePaceOption(AgendaItem agenda) {
        return agendaOptionRepository.saveAndFlush(AgendaOption.pace(agenda, Pace.NORMAL, 0));
    }

    private AgendaItem reload(AgendaItem agenda) {
        return agendaItemRepository.findById(agenda.getId()).orElseThrow();
    }

    private List<Long> unvotedIds(Room room, RoomMember member) {
        return unvotedAgendaProvider.findUnvotedAgendas(room.getId(), member.getId()).stream()
                .map(UnvotedAgenda::agendaId)
                .toList();
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("투표 중이고 마감 전인 안건 중 아직 투표하지 않은 것만 마감 임박 순으로 돌려준다")
            void findsOpenAgendasNotVotedYet() {
                Room room = saveRoom();
                RoomMember me = saveMember(room, "민수");
                AgendaItem stay =
                        saveVoting(room, AgendaSubject.STAY, 1, "숙소를 정해주세요", now().plusDays(2));
                AgendaItem budget =
                        saveVoting(room, AgendaSubject.BUDGET, 1, "예산을 정해주세요", now().plusDays(1));

                // 아래 안건은 모두 빠져야 한다
                AgendaItem voted =
                        saveVoting(room, AgendaSubject.PACE, 1, "이미 투표함", now().plusDays(1));
                voteRepository.saveAndFlush(Vote.create(voted, savePaceOption(voted), me, now()));
                AgendaItem resolved =
                        saveVoting(room, AgendaSubject.PACE, 2, "결정됨", now().plusDays(1));
                resolved.resolve(savePaceOption(resolved), now());
                agendaItemRepository.saveAndFlush(resolved);
                AgendaItem expired =
                        saveVoting(room, AgendaSubject.BUDGET, 2, "미결", now().plusDays(1));
                expired.expire();
                agendaItemRepository.saveAndFlush(expired);
                // 마감이 지났지만 스케줄러가 아직 닫지 않아 VOTING에 남은 안건
                saveVoting(room, AgendaSubject.STAY, 2, "마감 지남", now().minusMinutes(1));
                saveVoting(saveRoom(), AgendaSubject.BUDGET, 1, "다른 방", now().plusDays(1));

                List<UnvotedAgenda> unvoted =
                        unvotedAgendaProvider.findUnvotedAgendas(room.getId(), me.getId());

                assertThat(unvoted)
                        .extracting(UnvotedAgenda::agendaId)
                        .containsExactly(budget.getId(), stay.getId());
                assertThat(unvoted.getFirst().title()).isEqualTo("예산을 정해주세요");
                assertThat(unvoted.getFirst().deadlineAt())
                        .isEqualTo(reload(budget).getDeadlineAt());
            }

            @Test
            @DisplayName("안건이 열린 뒤 합류한 멤버는 투표 자격이 없어 넣지 않고, 합류 시각과 안건 생성 시각이 같으면 넣는다")
            void includesOnlyMembersJoinedBeforeAgendaOpened() {
                Room room = saveRoom();
                RoomMember early = saveMember(room, "먼저 합류");
                AgendaItem agenda =
                        saveVoting(room, AgendaSubject.BUDGET, 1, "예산을 정해주세요", now().plusDays(1));
                RoomMember late = saveMember(room, "늦게 합류");
                RoomMember sameTime = saveMember(room, "같은 시각 합류");
                jdbcTemplate.update(
                        "update plan_room_member set joined_at = ? where id = ?",
                        reload(agenda).getCreatedAt(),
                        sameTime.getId());

                assertThat(unvotedIds(room, early)).containsExactly(agenda.getId());
                assertThat(unvotedIds(room, late)).isEmpty();
                assertThat(unvotedIds(room, sameTime)).containsExactly(agenda.getId());
            }
        }
    }
}
