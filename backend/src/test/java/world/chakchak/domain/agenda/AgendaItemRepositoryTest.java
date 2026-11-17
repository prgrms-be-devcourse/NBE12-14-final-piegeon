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
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomRepository;

@IntegrationTest
@Transactional
class AgendaItemRepositoryTest {

    private static final LocalDateTime DEADLINE = LocalDateTime.of(2027, 6, 5, 12, 0);

    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private Clock clock;

    private Room room;

    @BeforeEach
    void setUp() {
        room = saveRoom();
    }

    private Room saveRoom() {
        // invite_code는 UNIQUE라 테스트마다 다른 값을 쓴다
        String inviteCode = UUID.randomUUID().toString().substring(0, 8);
        return roomRepository.save(
                Room.create(
                        "여름 여행",
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        inviteCode,
                        RoomStatus.SURVEYING,
                        clock));
    }

    private AgendaItem saveAgenda(Room room, AgendaSubject subject, int round) {
        return agendaItemRepository.saveAndFlush(
                AgendaItem.create(room, subject, round, "예산을 정해주세요", 3, DEADLINE));
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("안건을 저장한 뒤 조회하면 enum 문자열과 결정 정보가 유지된다")
            void savesAndFindsAgenda() {
                AgendaItem saved = saveAgenda(room, AgendaSubject.BUDGET, 1);
                saved.resolve(10L, DEADLINE);
                agendaItemRepository.flush();

                AgendaItem found = agendaItemRepository.findById(saved.getId()).orElseThrow();

                assertThat(found.getSubject()).isEqualTo(AgendaSubject.BUDGET);
                assertThat(found.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(found.getRound()).isEqualTo(1);
                assertThat(found.getTitle()).isEqualTo("예산을 정해주세요");
                assertThat(found.getTotalVoter()).isEqualTo(3);
                assertThat(found.getDeadlineAt()).isEqualTo(DEADLINE);
                assertThat(found.getResolvedOptionId()).isEqualTo(10L);
                assertThat(found.getResolvedAt()).isEqualTo(DEADLINE);
                assertThat(found.getCreatedAt()).isNotNull();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("같은 방에 같은 주제와 회차의 안건을 두 번 저장할 수 없다")
            void rejectsDuplicateRound() {
                saveAgenda(room, AgendaSubject.STAY, 1);

                assertThatThrownBy(() -> saveAgenda(room, AgendaSubject.STAY, 1))
                        .isInstanceOf(DataIntegrityViolationException.class);
            }

            @Test
            @DisplayName("회차가 1보다 작은 안건은 저장할 수 없다")
            void rejectsRoundBelowOne() {
                assertThatThrownBy(() -> saveAgenda(room, AgendaSubject.STAY, 0))
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("ck_agenda_item_round");
            }

            @Test
            @DisplayName("투표자 수가 음수인 안건은 저장할 수 없다")
            void rejectsNegativeTotalVoter() {
                AgendaItem agenda =
                        AgendaItem.create(room, AgendaSubject.STAY, 1, "제목", -1, DEADLINE);

                assertThatThrownBy(() -> agendaItemRepository.saveAndFlush(agenda))
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("ck_agenda_item_total_voter");
            }

            @Test
            @DisplayName("결정된 선지 없이 RESOLVED 상태로 저장할 수 없다")
            void rejectsResolvedWithoutOption() {
                AgendaItem agenda = saveAgenda(room, AgendaSubject.STAY, 1);
                agenda.resolve(null, DEADLINE);

                assertThatThrownBy(() -> agendaItemRepository.flush())
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("ck_agenda_item_resolved");
            }
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("주제별 최근 회차 조회는 회차가 가장 큰 안건을 돌려준다")
            void findsLatestRound() {
                saveAgenda(room, AgendaSubject.PACE, 1);
                AgendaItem round2 = saveAgenda(room, AgendaSubject.PACE, 2);
                saveAgenda(room, AgendaSubject.BUDGET, 3);

                AgendaItem latest =
                        agendaItemRepository
                                .findTopByRoomIdAndSubjectOrderByRoundDesc(
                                        room.getId(), AgendaSubject.PACE)
                                .orElseThrow();

                assertThat(latest.getId()).isEqualTo(round2.getId());
            }

            @Test
            @DisplayName("방 id와 안건 id로 조회하면 그 방의 안건을 돌려준다")
            void findsAgendaInRoom() {
                AgendaItem saved = saveAgenda(room, AgendaSubject.BUDGET, 1);

                assertThat(agendaItemRepository.findByIdAndRoomId(saved.getId(), room.getId()))
                        .isPresent();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("다른 방의 id로 안건을 조회하면 결과가 없다")
            void returnsEmptyForOtherRoom() {
                AgendaItem saved = saveAgenda(room, AgendaSubject.BUDGET, 1);
                Room otherRoom = saveRoom();

                assertThat(agendaItemRepository.findByIdAndRoomId(saved.getId(), otherRoom.getId()))
                        .isEmpty();
            }
        }
    }
}
