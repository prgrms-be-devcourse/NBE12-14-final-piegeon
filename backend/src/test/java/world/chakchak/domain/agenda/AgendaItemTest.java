package world.chakchak.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.errorcode.AgendaErrorCode;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.global.error.BusinessException;

class AgendaItemTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    private static final LocalDate START_DATE = LocalDate.of(2027, 7, 1);
    // 출발 전날 00:00
    private static final LocalDateTime D_MINUS_1 = LocalDateTime.of(2027, 6, 30, 0, 0);

    private static Room room() {
        return Room.create(
                "여름 여행",
                "부산",
                START_DATE,
                LocalDate.of(2027, 7, 3),
                "ABCD1234",
                RoomStatus.SURVEYING,
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    }

    private static AgendaItem votingAgenda(AgendaSubject subject, int totalVoter) {
        return AgendaItem.create(room(), subject, 1, "안건 제목", totalVoter, NOW.plusDays(4));
    }

    private static AgendaItem expiredAgenda(AgendaSubject subject) {
        AgendaItem agenda = votingAgenda(subject, 3);
        agenda.expire();
        return agenda;
    }

    private static void assertErrorCode(Runnable action, AgendaErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("안건을 열면 투표 중 상태이고 받은 제목을 쓴다")
            void opensVotingAgenda() {
                AgendaItem agenda =
                        AgendaItem.create(
                                room(), AgendaSubject.BUDGET, 1, "예산을 정해주세요", 4, NOW.plusDays(4));

                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.VOTING);
                assertThat(agenda.getSubject()).isEqualTo(AgendaSubject.BUDGET);
                assertThat(agenda.getRound()).isEqualTo(1);
                assertThat(agenda.getTitle()).isEqualTo("예산을 정해주세요");
                assertThat(agenda.getTotalVoter()).isEqualTo(4);
                assertThat(agenda.getDeadlineAt()).isEqualTo(NOW.plusDays(4));
                assertThat(agenda.getResolvedOptionId()).isNull();
                assertThat(agenda.getResolvedAt()).isNull();
            }

            // capDeadline이 private이 되어, create가 마감을 줄이는지로 검증한다
            @Test
            @DisplayName("마감 후보가 출발 전날 00시보다 늦으면 안건을 만들 때 출발 전날 00시로 줄인다")
            void capsDeadlineToDayBeforeDeparture() {
                AgendaItem agenda =
                        AgendaItem.create(
                                room(),
                                AgendaSubject.BUDGET,
                                1,
                                "제목",
                                4,
                                LocalDateTime.of(2027, 6, 30, 9, 0));

                assertThat(agenda.getDeadlineAt()).isEqualTo(D_MINUS_1);
            }

            @Test
            @DisplayName("마감 후보가 출발 전날 00시보다 이르면 그대로 둔다")
            void keepsDeadlineBeforeDayBeforeDeparture() {
                LocalDateTime candidate = LocalDateTime.of(2027, 6, 29, 23, 0);

                AgendaItem agenda =
                        AgendaItem.create(room(), AgendaSubject.BUDGET, 1, "제목", 4, candidate);

                assertThat(agenda.getDeadlineAt()).isEqualTo(candidate);
            }
        }

        // 투표자 0명 안건 생성 거부
        @Nested
        class Exception {

            @Test
            @DisplayName("투표자가 0명인 안건은 만들 수 없다")
            void rejectsZeroVoter() {
                assertThatThrownBy(() ->
                                AgendaItem.create(
                                        room(), AgendaSubject.BUDGET, 1, "제목", 0, NOW.plusDays(4)))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("다음 회차도 투표자가 0명이면 열 수 없다")
            void rejectsZeroVoterInNextRound() {
                AgendaItem expired = expiredAgenda(AgendaSubject.BUDGET);

                assertThatThrownBy(() -> expired.openNextRound(0, NOW))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("투표 중이고 마감 전이면 투표를 받을 수 있다")
            void isOpenBeforeDeadline() {
                AgendaItem agenda = votingAgenda(AgendaSubject.STAY, 3);

                assertThat(agenda.isOpenAt(NOW)).isTrue();
            }

            @Test
            @DisplayName("마감 시각이 되면 투표를 받을 수 없다")
            void isClosedAtDeadline() {
                AgendaItem agenda = votingAgenda(AgendaSubject.STAY, 3);

                assertThat(agenda.isOpenAt(agenda.getDeadlineAt())).isFalse();
            }

            @Test
            @DisplayName("결정된 안건은 마감 전이어도 투표를 받을 수 없다")
            void isClosedWhenResolved() {
                AgendaItem agenda = votingAgenda(AgendaSubject.STAY, 3);
                agenda.resolve(10L, NOW);

                assertThat(agenda.isOpenAt(NOW)).isFalse();
            }
        }
    }

    @Nested
    class Resolve {

        @Nested
        class Success {

            @Test
            @DisplayName("투표 중인 안건을 결정하면 선택지와 결정 시각이 기록된다")
            void resolvesVotingAgenda() {
                AgendaItem agenda = votingAgenda(AgendaSubject.PACE, 3);

                agenda.resolve(10L, NOW);

                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(agenda.getResolvedOptionId()).isEqualTo(10L);
                assertThat(agenda.getResolvedAt()).isEqualTo(NOW);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("이미 결정된 안건은 다시 결정할 수 없다")
            void rejectsResolvedAgenda() {
                AgendaItem agenda = votingAgenda(AgendaSubject.PACE, 3);
                agenda.resolve(10L, NOW);

                assertErrorCode(() -> agenda.resolve(11L, NOW), AgendaErrorCode.AGENDA_CLOSED);
            }

            @Test
            @DisplayName("미결 마감된 안건은 결정할 수 없다")
            void rejectsExpiredAgenda() {
                AgendaItem agenda = expiredAgenda(AgendaSubject.PACE);

                assertErrorCode(() -> agenda.resolve(10L, NOW), AgendaErrorCode.AGENDA_CLOSED);
            }
        }
    }

    @Nested
    class Expire {

        @Nested
        class Success {

            @Test
            @DisplayName("투표 중인 안건을 마감하면 미결(EXPIRED)이 된다")
            void expiresVotingAgenda() {
                AgendaItem agenda = votingAgenda(AgendaSubject.BUDGET, 3);

                agenda.expire();

                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                assertThat(agenda.getResolvedOptionId()).isNull();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("이미 결정된 안건은 미결로 마감할 수 없다")
            void rejectsResolvedAgenda() {
                AgendaItem agenda = votingAgenda(AgendaSubject.BUDGET, 3);
                agenda.resolve(10L, NOW);

                assertErrorCode(agenda::expire, AgendaErrorCode.AGENDA_CLOSED);
            }
        }
    }

    @Nested
    class RemoveVoter {

        @Nested
        class Success {

            @Test
            @DisplayName("투표 자격자가 나가면 투표자 수가 1 줄어든다")
            void decreasesTotalVoter() {
                AgendaItem agenda = votingAgenda(AgendaSubject.STAY, 3);

                agenda.removeVoter();

                assertThat(agenda.getTotalVoter()).isEqualTo(2);
                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.VOTING);
            }

            @Test
            @DisplayName("마지막 투표 자격자가 나가면 안건은 미결(EXPIRED)이 된다")
            void expiresWhenNoVoterLeft() {
                AgendaItem agenda = votingAgenda(AgendaSubject.STAY, 1);

                agenda.removeVoter();

                assertThat(agenda.getTotalVoter()).isZero();
                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.EXPIRED);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("투표 중이 아닌 안건에서는 투표자를 뺄 수 없다")
            void rejectsClosedAgenda() {
                AgendaItem agenda = expiredAgenda(AgendaSubject.STAY);

                assertErrorCode(agenda::removeVoter, AgendaErrorCode.AGENDA_CLOSED);
            }
        }
    }

    // openNextRound는 출발일을 room에서 꺼내므로 startDate 인자 없이 부른다
    @Nested
    class NextRound {

        @Nested
        class Success {

            @Test
            @DisplayName("미결 설문 안건의 다음 회차는 제목을 물려받고 회차가 1 오르며 7일 뒤 마감된다")
            void opensNextRound() {
                AgendaItem expired = expiredAgenda(AgendaSubject.BUDGET);

                AgendaItem next = expired.openNextRound(4, NOW);

                assertThat(next.getSubject()).isEqualTo(AgendaSubject.BUDGET);
                assertThat(next.getRound()).isEqualTo(2);
                assertThat(next.getTitle()).isEqualTo(expired.getTitle());
                assertThat(next.getStatus()).isEqualTo(AgendaStatus.VOTING);
                assertThat(next.getTotalVoter()).isEqualTo(4);
                assertThat(next.getDeadlineAt()).isEqualTo(NOW.plusDays(7));
                assertThat(next.getRoom()).isSameAs(expired.getRoom());
            }

            @Test
            @DisplayName("다음 회차의 7일 뒤가 출발 전날 00시보다 늦으면 출발 전날 00시에 마감된다")
            void capsNextRoundDeadline() {
                AgendaItem expired = expiredAgenda(AgendaSubject.BUDGET);
                LocalDateTime now = LocalDateTime.of(2027, 6, 26, 10, 0);

                AgendaItem next = expired.openNextRound(4, now);

                assertThat(next.getDeadlineAt()).isEqualTo(D_MINUS_1);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("최종 확정 안건은 다음 회차로 열 수 없다")
            void rejectsFinalPlan() {
                AgendaItem expired = expiredAgenda(AgendaSubject.FINAL_PLAN);

                assertErrorCode(
                        () -> expired.openNextRound(4, NOW), AgendaErrorCode.AGENDA_NOT_REOPENABLE);
            }

            @Test
            @DisplayName("미결(EXPIRED)이 아닌 안건은 다음 회차로 열 수 없다")
            void rejectsVotingAgenda() {
                AgendaItem voting = votingAgenda(AgendaSubject.BUDGET, 3);

                assertErrorCode(
                        () -> voting.openNextRound(4, NOW), AgendaErrorCode.AGENDA_NOT_REOPENABLE);
            }

            @Test
            @DisplayName("출발 전날 00시가 지나면 다음 회차를 열 수 없다")
            void rejectsAfterDayBeforeDeparture() {
                AgendaItem expired = expiredAgenda(AgendaSubject.BUDGET);

                assertErrorCode(
                        () -> expired.openNextRound(4, D_MINUS_1),
                        AgendaErrorCode.AGENDA_NOT_REOPENABLE);
            }
        }
    }
}
