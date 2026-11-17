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
    @Autowired private JdbcTemplate jdbcTemplate;

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

            // 퇴장으로 투표자가 0명이 되어 EXPIRED가 된 안건은 저장할 수 있어야 한다
            @Test
            @DisplayName("마지막 투표자가 나가 미결 마감된 안건은 투표자 0명으로 저장된다")
            void savesExpiredAgendaWithZeroVoter() {
                AgendaItem agenda =
                        agendaItemRepository.saveAndFlush(
                                AgendaItem.create(room, AgendaSubject.STAY, 1, "제목", 1, DEADLINE));
                agenda.removeVoter();
                agendaItemRepository.flush();

                AgendaItem found = agendaItemRepository.findById(agenda.getId()).orElseThrow();

                assertThat(found.getTotalVoter()).isZero();
                assertThat(found.getStatus()).isEqualTo(AgendaStatus.EXPIRED);
            }

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

            // 엔티티가 투표자 0명 이하를 먼저 막으므로, DB 제약은 SQL로 직접 값을 바꿔 확인한다
            @Test
            @DisplayName("투표자 수가 음수인 안건은 저장할 수 없다")
            void rejectsNegativeTotalVoter() {
                AgendaItem agenda = saveAgenda(room, AgendaSubject.STAY, 1);

                assertThatThrownBy(() ->
                                jdbcTemplate.update(
                                        "UPDATE agenda_item SET status = 'EXPIRED',"
                                                + " total_voter = -1 WHERE id = ?",
                                        agenda.getId()))
                        .isInstanceOf(DataAccessException.class)
                        .hasMessageContaining("ck_agenda_item_total_voter");
            }

            @Test
            @DisplayName("투표 중인 안건의 투표자 수를 0명으로 저장할 수 없다")
            void rejectsZeroVoterWhileVoting() {
                AgendaItem agenda = saveAgenda(room, AgendaSubject.STAY, 1);

                assertThatThrownBy(() ->
                                jdbcTemplate.update(
                                        "UPDATE agenda_item SET total_voter = 0 WHERE id = ?",
                                        agenda.getId()))
                        .isInstanceOf(DataAccessException.class)
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

            // 방 나가기(R-06)에서 투표 중인 안건만 골라 removeVoter를 부르기 위한 조회
            @Test
            @DisplayName("상태로 조회하면 그 방에서 해당 상태인 안건만 돌려준다")
            void findsAgendasByStatus() {
                AgendaItem voting = saveAgenda(room, AgendaSubject.BUDGET, 1);
                AgendaItem resolved = saveAgenda(room, AgendaSubject.STAY, 1);
                resolved.resolve(10L, DEADLINE);
                AgendaItem expired = saveAgenda(room, AgendaSubject.PACE, 1);
                expired.expire();
                saveAgenda(saveRoom(), AgendaSubject.BUDGET, 1);
                agendaItemRepository.flush();

                assertThat(
                                agendaItemRepository.findAllByRoomIdAndStatus(
                                        room.getId(), AgendaStatus.VOTING))
                        .extracting(AgendaItem::getId)
                        .containsExactly(voting.getId());
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
