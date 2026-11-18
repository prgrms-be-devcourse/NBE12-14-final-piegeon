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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.agenda.repository.AgendaOptionRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/** agenda_option 테이블 저장·조회 테스트 (V12) */
@IntegrationTest
@Transactional
class AgendaOptionRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2027, 6, 5, 12, 0);

    @Autowired private AgendaOptionRepository agendaOptionRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private Clock clock;

    private Room room;
    private AgendaItem budgetAgenda;

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
        budgetAgenda = saveAgenda(AgendaSubject.BUDGET, 1);
    }

    private AgendaItem saveAgenda(AgendaSubject subject, int round) {
        return agendaItemRepository.saveAndFlush(
                AgendaItem.create(room, subject, round, "예산을 정해주세요", 3, NOW, DEADLINE));
    }

    private RoomMember saveMember() {
        User user =
                userRepository.save(
                        User.create(UUID.randomUUID() + "@example.com", "테스터", "hashed-password"));
        return roomMemberRepository.save(RoomMember.create(room, user, clock));
    }

    private AgendaOption saveBudget(
            AgendaItem agenda, long amount, int displayIndex, RoomMember proposedBy) {
        return agendaOptionRepository.saveAndFlush(
                AgendaOption.budget(agenda, amount, displayIndex, proposedBy));
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("선지를 저장한 뒤 조회하면 라벨, 값, 순서, 추가한 멤버가 유지된다")
            void savesAndFindsOption() {
                RoomMember member = saveMember();
                AgendaOption saved = saveBudget(budgetAgenda, 1_200_000L, 1, member);

                AgendaOption found = agendaOptionRepository.findById(saved.getId()).orElseThrow();

                assertThat(found.getAgendaItem().getId()).isEqualTo(budgetAgenda.getId());
                assertThat(found.getLabel()).isEqualTo("120만원");
                assertThat(found.getValue()).isEqualTo("1200000");
                assertThat(found.getDisplayIndex()).isEqualTo(1);
                assertThat(found.getProposedBy().getId()).isEqualTo(member.getId());
                assertThat(found.getCreatedAt()).isNotNull();
            }

            @Test
            @DisplayName("같은 금액이라도 다른 안건이면 선지를 저장할 수 있다")
            void savesSameValueInOtherAgenda() {
                AgendaItem nextRound = saveAgenda(AgendaSubject.BUDGET, 2);
                saveBudget(budgetAgenda, 1_000_000L, 0, null);

                AgendaOption saved = saveBudget(nextRound, 1_000_000L, 0, null);

                assertThat(saved.getId()).isNotNull();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("한 안건에 같은 값의 선지를 두 번 저장할 수 없다")
            void rejectsDuplicateValue() {
                saveBudget(budgetAgenda, 1_000_000L, 0, null);

                assertThatThrownBy(() -> saveBudget(budgetAgenda, 1_000_000L, 1, saveMember()))
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("uk_agenda_option_agenda_value");
            }
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("안건의 선지 목록은 화면 순서대로 돌려준다")
            void findsOptionsInDisplayOrder() {
                AgendaOption third = saveBudget(budgetAgenda, 1_300_000L, 2, null);
                AgendaOption first = saveBudget(budgetAgenda, 600_000L, 0, null);
                AgendaOption second = saveBudget(budgetAgenda, 800_000L, 1, null);
                saveBudget(saveAgenda(AgendaSubject.BUDGET, 2), 500_000L, 0, null);

                assertThat(
                                agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(
                                        budgetAgenda.getId()))
                        .extracting(AgendaOption::getId)
                        .containsExactly(first.getId(), second.getId(), third.getId());
            }

            @Test
            @DisplayName("안건 id와 선지 id로 조회하면 그 안건의 선지를 돌려준다")
            void findsOptionInAgenda() {
                AgendaOption saved = saveBudget(budgetAgenda, 1_000_000L, 0, null);

                assertThat(
                                agendaOptionRepository.findByIdAndAgendaItemId(
                                        saved.getId(), budgetAgenda.getId()))
                        .isPresent();
            }

            @Test
            @DisplayName("값으로 조회하면 그 안건에서 같은 값의 선지를 돌려준다")
            void findsOptionByValue() {
                AgendaOption saved = saveBudget(budgetAgenda, 1_000_000L, 0, null);

                assertThat(
                                agendaOptionRepository.findByAgendaItemIdAndValue(
                                        budgetAgenda.getId(), "1000000"))
                        .get()
                        .extracting(AgendaOption::getId)
                        .isEqualTo(saved.getId());
            }

            @Test
            @DisplayName("이 안건에 멤버가 추가한 선지가 있는지 확인한다")
            void checksProposedOptionExists() {
                RoomMember proposer = saveMember();
                RoomMember other = saveMember();
                saveBudget(budgetAgenda, 1_000_000L, 0, proposer);

                assertThat(
                                agendaOptionRepository.existsByAgendaItemIdAndProposedById(
                                        budgetAgenda.getId(), proposer.getId()))
                        .isTrue();
                assertThat(
                                agendaOptionRepository.existsByAgendaItemIdAndProposedById(
                                        budgetAgenda.getId(), other.getId()))
                        .isFalse();
            }

            @Test
            @DisplayName("멤버가 추가한 선지만 골라 조회한다")
            void findsProposedOptions() {
                saveBudget(budgetAgenda, 600_000L, 0, null);
                AgendaOption proposed = saveBudget(budgetAgenda, 1_500_000L, 1, saveMember());

                assertThat(
                                agendaOptionRepository.findAllByAgendaItemIdAndProposedByIsNotNull(
                                        budgetAgenda.getId()))
                        .extracting(AgendaOption::getId)
                        .containsExactly(proposed.getId());
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("다른 안건의 id로 선지를 조회하면 결과가 없다")
            void returnsEmptyForOtherAgenda() {
                AgendaOption saved = saveBudget(budgetAgenda, 1_000_000L, 0, null);
                AgendaItem other = saveAgenda(AgendaSubject.BUDGET, 2);

                assertThat(
                                agendaOptionRepository.findByIdAndAgendaItemId(
                                        saved.getId(), other.getId()))
                        .isEmpty();
            }
        }
    }
}
