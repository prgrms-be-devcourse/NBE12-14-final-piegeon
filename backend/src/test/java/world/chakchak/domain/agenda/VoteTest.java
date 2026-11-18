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
import org.springframework.test.util.ReflectionTestUtils;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.entity.FinalPlanChoice;
import world.chakchak.domain.agenda.entity.Vote;
import world.chakchak.domain.agenda.errorcode.AgendaErrorCode;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.user.entity.User;
import world.chakchak.global.error.BusinessException;

class VoteTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    private static final LocalDateTime DEADLINE = NOW.plusDays(4);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    private static final Room ROOM =
            Room.create(
                    "여름 여행",
                    "부산",
                    LocalDate.of(2027, 7, 1),
                    LocalDate.of(2027, 7, 3),
                    "ABCD1234",
                    RoomStatus.SURVEYING,
                    CLOCK);

    private static AgendaItem agenda(AgendaSubject subject) {
        return AgendaItem.create(ROOM, subject, 1, "안건 제목", 3, NOW, DEADLINE);
    }

    private static RoomMember member() {
        return RoomMember.create(ROOM, User.create("minsu@example.com", "민수", "hash"), CLOCK);
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
            @DisplayName("투표 중인 안건의 선지에 표를 내면 안건, 선지, 멤버를 기록한다")
            void createsVote() {
                AgendaItem agenda = agenda(AgendaSubject.PACE);
                AgendaOption option = AgendaOption.pace(agenda, Pace.RELAXED, 0);
                RoomMember member = member();

                Vote vote = Vote.create(agenda, option, member, NOW);

                assertThat(vote.getAgendaItem()).isSameAs(agenda);
                assertThat(vote.getAgendaOption()).isSameAs(option);
                assertThat(vote.getMember()).isSameAs(member);
            }

            @Test
            @DisplayName("마감 직전까지는 표를 낼 수 있다")
            void createsJustBeforeDeadline() {
                AgendaItem agenda = agenda(AgendaSubject.PACE);
                AgendaOption option = AgendaOption.pace(agenda, Pace.RELAXED, 0);

                Vote vote = Vote.create(agenda, option, member(), DEADLINE.minusSeconds(1));

                assertThat(vote.getAgendaOption()).isSameAs(option);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("마감 시각이 되면 표를 낼 수 없다")
            void rejectsAtDeadline() {
                AgendaItem agenda = agenda(AgendaSubject.PACE);
                AgendaOption option = AgendaOption.pace(agenda, Pace.RELAXED, 0);

                assertErrorCode(
                        () -> Vote.create(agenda, option, member(), DEADLINE),
                        AgendaErrorCode.AGENDA_CLOSED);
            }

            @Test
            @DisplayName("미결 마감(EXPIRED)된 안건에는 표를 낼 수 없다")
            void rejectsExpiredAgenda() {
                AgendaItem agenda = agenda(AgendaSubject.PACE);
                AgendaOption option = AgendaOption.pace(agenda, Pace.RELAXED, 0);
                agenda.expire();

                assertErrorCode(
                        () -> Vote.create(agenda, option, member(), NOW),
                        AgendaErrorCode.AGENDA_CLOSED);
            }

            // resolve는 저장된(id가 있는) 선지만 받아 id를 직접 넣는다
            @Test
            @DisplayName("이미 결정된 안건에는 표를 낼 수 없다")
            void rejectsResolvedAgenda() {
                AgendaItem agenda = agenda(AgendaSubject.PACE);
                AgendaOption option = AgendaOption.pace(agenda, Pace.RELAXED, 0);
                ReflectionTestUtils.setField(option, "id", 10L);
                agenda.resolve(option, NOW);

                assertErrorCode(
                        () -> Vote.create(agenda, option, member(), NOW),
                        AgendaErrorCode.AGENDA_CLOSED);
            }

            @Test
            @DisplayName("다른 안건의 선지에는 표를 낼 수 없다")
            void rejectsOptionOfOtherAgenda() {
                AgendaItem agenda = agenda(AgendaSubject.PACE);
                AgendaOption otherOption =
                        AgendaOption.pace(agenda(AgendaSubject.PACE), Pace.RELAXED, 0);

                assertErrorCode(
                        () -> Vote.create(agenda, otherOption, member(), NOW),
                        AgendaErrorCode.OPTION_NOT_IN_AGENDA);
            }
        }
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("표를 바꾸면 새 선지를 가리킨다")
            void changesOption() {
                AgendaItem agenda = agenda(AgendaSubject.PACE);
                AgendaOption relaxed = AgendaOption.pace(agenda, Pace.RELAXED, 0);
                AgendaOption hurry = AgendaOption.pace(agenda, Pace.HURRY, 1);
                Vote vote = Vote.create(agenda, relaxed, member(), NOW);

                vote.change(hurry, NOW);

                assertThat(vote.getAgendaOption()).isSameAs(hurry);
            }

            @Test
            @DisplayName("같은 선지로 바꾸면 아무것도 바뀌지 않는다")
            void keepsSameOption() {
                AgendaItem agenda = agenda(AgendaSubject.PACE);
                AgendaOption relaxed = AgendaOption.pace(agenda, Pace.RELAXED, 0);
                Vote vote = Vote.create(agenda, relaxed, member(), NOW);

                vote.change(relaxed, NOW);

                assertThat(vote.getAgendaOption()).isSameAs(relaxed);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("마감이 지나면 표를 바꿀 수 없다")
            void rejectsAfterDeadline() {
                AgendaItem agenda = agenda(AgendaSubject.PACE);
                AgendaOption relaxed = AgendaOption.pace(agenda, Pace.RELAXED, 0);
                AgendaOption hurry = AgendaOption.pace(agenda, Pace.HURRY, 1);
                Vote vote = Vote.create(agenda, relaxed, member(), NOW);

                assertErrorCode(() -> vote.change(hurry, DEADLINE), AgendaErrorCode.AGENDA_CLOSED);
                assertThat(vote.getAgendaOption()).isSameAs(relaxed);
            }

            @Test
            @DisplayName("다른 안건의 선지로는 표를 바꿀 수 없다")
            void rejectsOptionOfOtherAgenda() {
                AgendaItem agenda = agenda(AgendaSubject.PACE);
                AgendaOption relaxed = AgendaOption.pace(agenda, Pace.RELAXED, 0);
                AgendaOption otherOption =
                        AgendaOption.pace(agenda(AgendaSubject.PACE), Pace.HURRY, 1);
                Vote vote = Vote.create(agenda, relaxed, member(), NOW);

                assertErrorCode(
                        () -> vote.change(otherOption, NOW), AgendaErrorCode.OPTION_NOT_IN_AGENDA);
                assertThat(vote.getAgendaOption()).isSameAs(relaxed);
            }
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("표가 가리키는 선지만 그 표의 선지로 판단한다")
            void checksOption() {
                AgendaItem agenda = agenda(AgendaSubject.PACE);
                AgendaOption relaxed = AgendaOption.pace(agenda, Pace.RELAXED, 0);
                AgendaOption hurry = AgendaOption.pace(agenda, Pace.HURRY, 1);
                Vote vote = Vote.create(agenda, relaxed, member(), NOW);

                assertThat(vote.isFor(relaxed)).isTrue();
                assertThat(vote.isFor(hurry)).isFalse();
            }

            // 저장 후에는 프록시와 실제 객체가 다른 인스턴스일 수 있어 id로 비교한다
            @Test
            @DisplayName("다른 인스턴스여도 id가 같으면 같은 선지로 판단한다")
            void checksOptionById() {
                AgendaItem agenda = agenda(AgendaSubject.PACE);
                AgendaOption relaxed = AgendaOption.pace(agenda, Pace.RELAXED, 0);
                AgendaOption sameRow = AgendaOption.pace(agenda, Pace.RELAXED, 0);
                ReflectionTestUtils.setField(relaxed, "id", 10L);
                ReflectionTestUtils.setField(sameRow, "id", 10L);
                Vote vote = Vote.create(agenda, relaxed, member(), NOW);

                assertThat(vote.isFor(sameRow)).isTrue();
            }

            @Test
            @DisplayName("최종 확정 안건의 반대 선지에 낸 표만 반대 표다")
            void checksReject() {
                AgendaItem agenda = agenda(AgendaSubject.FINAL_PLAN);
                AgendaOption agree = AgendaOption.finalPlan(agenda, FinalPlanChoice.AGREE);
                AgendaOption reject = AgendaOption.finalPlan(agenda, FinalPlanChoice.REJECT);

                assertThat(Vote.create(agenda, reject, member(), NOW).isReject()).isTrue();
                assertThat(Vote.create(agenda, agree, member(), NOW).isReject()).isFalse();
            }
        }
    }
}
