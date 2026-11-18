package world.chakchak.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.entity.Vote;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.agenda.repository.AgendaOptionRepository;
import world.chakchak.domain.agenda.repository.VoteRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/** vote 테이블 저장·조회 테스트 (V16) */
@IntegrationTest
@Transactional
class VoteRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2027, 6, 5, 12, 0);

    @Autowired private VoteRepository voteRepository;
    @Autowired private AgendaOptionRepository agendaOptionRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    private Room room;
    private AgendaItem agenda;
    private AgendaOption low;
    private AgendaOption high;
    private RoomMember minsu;
    private RoomMember jieun;

    @BeforeEach
    void setUp() {
        // invite_code는 UNIQUE라 테스트마다 다른 값을 쓴다
        String inviteCode = UUID.randomUUID().toString().substring(0, 8);
        room =
                roomRepository.save(
                        Room.create(
                                "여름 여행",
                                "부산",
                                LocalDate.of(2027, 7, 1),
                                LocalDate.of(2027, 7, 3),
                                inviteCode,
                                RoomStatus.SURVEYING,
                                clock));
        agenda = saveAgenda(1);
        low = saveOption(agenda, 600_000L, 0);
        high = saveOption(agenda, 1_000_000L, 1);
        minsu = saveMember();
        jieun = saveMember();
    }

    private AgendaItem saveAgenda(int round) {
        return agendaItemRepository.saveAndFlush(
                AgendaItem.create(
                        room, AgendaSubject.BUDGET, round, "예산을 정해주세요", 3, NOW, DEADLINE));
    }

    private AgendaOption saveOption(AgendaItem agenda, long amount, int displayIndex) {
        return agendaOptionRepository.saveAndFlush(
                AgendaOption.budget(agenda, amount, displayIndex, null));
    }

    private RoomMember saveMember() {
        User user =
                userRepository.save(
                        User.create(UUID.randomUUID() + "@example.com", "테스터", "hashed-password"));
        return roomMemberRepository.save(RoomMember.create(room, user, clock));
    }

    private Vote saveVote(AgendaItem agenda, AgendaOption option, RoomMember member) {
        return voteRepository.saveAndFlush(Vote.create(agenda, option, member, NOW));
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("표를 저장한 뒤 조회하면 안건, 선지, 멤버가 유지된다")
            void savesAndFindsVote() {
                Vote saved = saveVote(agenda, low, minsu);

                Vote found = voteRepository.findById(saved.getId()).orElseThrow();

                assertThat(found.getAgendaItem().getId()).isEqualTo(agenda.getId());
                assertThat(found.getAgendaOption().getId()).isEqualTo(low.getId());
                assertThat(found.getMember().getId()).isEqualTo(minsu.getId());
                assertThat(found.getCreatedAt()).isNotNull();
            }

            @Test
            @DisplayName("같은 멤버라도 다른 안건에는 표를 낼 수 있다")
            void savesVoteInOtherAgenda() {
                AgendaItem nextRound = saveAgenda(2);
                AgendaOption nextOption = saveOption(nextRound, 600_000L, 0);
                saveVote(agenda, low, minsu);

                Vote saved = saveVote(nextRound, nextOption, minsu);

                assertThat(saved.getId()).isNotNull();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("한 멤버가 같은 안건에 표를 두 번 저장할 수 없다")
            void rejectsSecondVoteInSameAgenda() {
                saveVote(agenda, low, minsu);

                assertThatThrownBy(() -> saveVote(agenda, high, minsu))
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("uk_vote_agenda_member");
            }

            // 엔티티가 다른 안건의 선지를 먼저 막으므로, DB 제약은 SQL로 직접 넣어 확인한다
            @Test
            @DisplayName("다른 안건의 선지를 가리키는 표는 저장할 수 없다")
            void rejectsOptionOfOtherAgenda() {
                AgendaOption otherOption = saveOption(saveAgenda(2), 600_000L, 0);

                assertThatThrownBy(() ->
                                jdbcTemplate.update(
                                        "INSERT INTO vote (plan_room_member_id, agenda_id,"
                                                + " agenda_option_id, created_at, modified_at)"
                                                + " VALUES (?, ?, ?, ?, ?)",
                                        minsu.getId(),
                                        agenda.getId(),
                                        otherOption.getId(),
                                        NOW,
                                        NOW))
                        .isInstanceOf(DataAccessException.class)
                        .hasMessageContaining("fk_vote_option");
            }
        }
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("표를 바꾸면 새 선지가 저장된다")
            void savesChangedOption() {
                Vote vote = saveVote(agenda, low, minsu);

                vote.change(high, NOW);
                voteRepository.flush();

                assertThat(voteRepository.findById(vote.getId()).orElseThrow().getAgendaOption())
                        .extracting(AgendaOption::getId)
                        .isEqualTo(high.getId());
            }
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("안건과 멤버로 조회하면 그 멤버가 낸 표를 돌려준다")
            void findsMyVote() {
                Vote mine = saveVote(agenda, low, minsu);
                saveVote(agenda, high, jieun);

                assertThat(
                                voteRepository.findByAgendaItemIdAndMemberId(
                                        agenda.getId(), minsu.getId()))
                        .get()
                        .extracting(Vote::getId)
                        .isEqualTo(mine.getId());
            }

            @Test
            @DisplayName("안건의 표 목록과 표 수는 그 안건의 표만 센다")
            void findsAndCountsVotesInAgenda() {
                Vote first = saveVote(agenda, low, minsu);
                Vote second = saveVote(agenda, high, jieun);
                AgendaItem nextRound = saveAgenda(2);
                saveVote(nextRound, saveOption(nextRound, 600_000L, 0), minsu);

                assertThat(voteRepository.findAllByAgendaItemId(agenda.getId()))
                        .extracting(Vote::getId)
                        .containsExactlyInAnyOrder(first.getId(), second.getId());
                assertThat(voteRepository.countByAgendaItemId(agenda.getId())).isEqualTo(2);
            }

            @Test
            @DisplayName("선지에 다른 멤버가 낸 표 수는 내 표를 빼고 센다")
            void countsOtherVotesOnOption() {
                RoomMember hyunwoo = saveMember();
                saveVote(agenda, high, minsu);
                saveVote(agenda, high, jieun);
                saveVote(agenda, low, hyunwoo);

                assertThat(
                                voteRepository.countByAgendaOptionIdAndMemberIdNot(
                                        high.getId(), minsu.getId()))
                        .isEqualTo(1);
                assertThat(
                                voteRepository.countByAgendaOptionIdAndMemberIdNot(
                                        low.getId(), hyunwoo.getId()))
                        .isZero();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("아직 표를 내지 않은 멤버로 조회하면 결과가 없다")
            void returnsEmptyWhenNotVoted() {
                saveVote(agenda, low, minsu);

                assertThat(
                                voteRepository.findByAgendaItemIdAndMemberId(
                                        agenda.getId(), jieun.getId()))
                        .isEmpty();
            }
        }
    }

    @Nested
    class Delete {

        @Nested
        class Success {

            @Test
            @DisplayName("선지와 멤버로 지우면 그 선지에 있는 내 표만 지워진다")
            void deletesMyVoteOnOption() {
                Vote mine = saveVote(agenda, high, minsu);
                Vote others = saveVote(agenda, high, jieun);

                voteRepository.deleteByAgendaOptionIdAndMemberId(high.getId(), minsu.getId());
                voteRepository.flush();

                assertThat(voteRepository.findById(mine.getId())).isEmpty();
                assertThat(voteRepository.findById(others.getId())).isPresent();
            }

            @Test
            @DisplayName("안건과 멤버로 지우면 그 안건의 내 표만 지워지고 다른 안건의 표는 남는다")
            void deletesMyVoteInAgenda() {
                Vote mine = saveVote(agenda, low, minsu);
                Vote others = saveVote(agenda, low, jieun);
                AgendaItem nextRound = saveAgenda(2);
                Vote mineInOtherAgenda =
                        saveVote(nextRound, saveOption(nextRound, 600_000L, 0), minsu);

                voteRepository.deleteByAgendaItemIdAndMemberId(agenda.getId(), minsu.getId());
                voteRepository.flush();

                assertThat(voteRepository.findById(mine.getId())).isEmpty();
                assertThat(voteRepository.findById(others.getId())).isPresent();
                assertThat(voteRepository.findById(mineInOtherAgenda.getId())).isPresent();
            }
        }
    }
}
