package world.chakchak.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.entity.FinalPlanChoice;
import world.chakchak.domain.agenda.entity.Vote;
import world.chakchak.domain.agenda.errorcode.AgendaErrorCode;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.user.entity.User;
import world.chakchak.global.error.BusinessException;

class AgendaItemTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    private static final LocalDate START_DATE = LocalDate.of(2027, 7, 1);
    // 출발 전날 00:00
    private static final LocalDateTime D_MINUS_1 = LocalDateTime.of(2027, 6, 30, 0, 0);
    private static final Long OPTION_ID = 10L;

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
        return AgendaItem.create(room(), subject, 1, "안건 제목", totalVoter, NOW, NOW.plusDays(4));
    }

    private static AgendaItem expiredAgenda(AgendaSubject subject) {
        AgendaItem agenda = votingAgenda(subject, 3);
        agenda.expire();
        return agenda;
    }

    // 안건 주제에 맞는 저장된 선지. resolve는 저장된(id가 있는) 선지만 받아 id를 직접 넣는다
    private static AgendaOption optionOf(AgendaItem agenda) {
        AgendaOption option = unsavedOptionOf(agenda);
        ReflectionTestUtils.setField(option, "id", OPTION_ID);
        return option;
    }

    private static AgendaOption unsavedOptionOf(AgendaItem agenda) {
        return switch (agenda.getSubject()) {
            case BUDGET -> AgendaOption.budget(agenda, 1_000_000L, 0, null);
            case STAY -> AgendaOption.stay(agenda, StayType.HOTEL, 0);
            case PACE -> AgendaOption.pace(agenda, Pace.RELAXED, 0);
            case FINAL_PLAN -> AgendaOption.finalPlan(agenda, FinalPlanChoice.AGREE);
        };
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
                                room(),
                                AgendaSubject.BUDGET,
                                1,
                                "예산을 정해주세요",
                                4,
                                NOW,
                                NOW.plusDays(4));

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
                                NOW,
                                LocalDateTime.of(2027, 6, 30, 9, 0));

                assertThat(agenda.getDeadlineAt()).isEqualTo(D_MINUS_1);
            }

            @Test
            @DisplayName("마감 후보가 출발 전날 00시보다 이르면 그대로 둔다")
            void keepsDeadlineBeforeDayBeforeDeparture() {
                LocalDateTime candidate = LocalDateTime.of(2027, 6, 29, 23, 0);

                AgendaItem agenda =
                        AgendaItem.create(room(), AgendaSubject.BUDGET, 1, "제목", 4, NOW, candidate);

                assertThat(agenda.getDeadlineAt()).isEqualTo(candidate);
            }

            @Test
            @DisplayName("출발 전날 00시 직전에는 안건을 만들 수 있고 마감은 출발 전날 00시로 줄어든다")
            void createsJustBeforeDayBeforeDeparture() {
                LocalDateTime now = D_MINUS_1.minusMinutes(1);

                AgendaItem agenda =
                        AgendaItem.create(
                                room(), AgendaSubject.BUDGET, 1, "제목", 4, now, now.plusDays(4));

                assertThat(agenda.getDeadlineAt()).isEqualTo(D_MINUS_1);
                assertThat(agenda.isOpenAt(now)).isTrue();
            }

            @Test
            @DisplayName("만장일치 안건은 마감을 지금 시각으로 넘겨 만들고 바로 결정할 수 있다")
            void createsUnanimousAgenda() {
                AgendaItem agenda =
                        AgendaItem.create(room(), AgendaSubject.STAY, 1, "제목", 4, NOW, NOW);
                agenda.resolve(optionOf(agenda), NOW);

                assertThat(agenda.getDeadlineAt()).isEqualTo(NOW);
                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
            }

            @Test
            @DisplayName("최종 확정 안건은 정해진 제목으로 열리고 마감은 지금부터 7일 뒤다")
            void opensFinalReview() {
                AgendaItem agenda = AgendaItem.openFinalReview(room(), 2, 4, NOW);

                assertThat(agenda.getSubject()).isEqualTo(AgendaSubject.FINAL_PLAN);
                assertThat(agenda.getRound()).isEqualTo(2);
                assertThat(agenda.getTitle()).isEqualTo("이 계획으로 확정할까요?");
                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.VOTING);
                assertThat(agenda.getTotalVoter()).isEqualTo(4);
                assertThat(agenda.getDeadlineAt()).isEqualTo(NOW.plusDays(7));
            }

            @Test
            @DisplayName("출발까지 7일이 안 남았으면 최종 확정 마감을 출발 전날 00시로 줄인다")
            void capsFinalReviewDeadline() {
                AgendaItem agenda =
                        AgendaItem.openFinalReview(room(), 1, 4, D_MINUS_1.minusDays(3));

                assertThat(agenda.getDeadlineAt()).isEqualTo(D_MINUS_1);
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("출발 전날 00시가 되면 최종 확정 투표를 열 수 없다")
            void rejectsFinalReviewAtDayBeforeDeparture() {
                assertErrorCode(
                        () -> AgendaItem.openFinalReview(room(), 1, 4, D_MINUS_1),
                        AgendaErrorCode.AGENDA_DEADLINE_PASSED);
            }

            // 출발 4일 전에 만든 방은 설문 마감이 정확히 D-1이라, 이때 만든 안건은 만들자마자 투표할 수 없게 된다
            @Test
            @DisplayName("출발 전날 00시가 되면 안건을 만들 수 없다")
            void rejectsAtDayBeforeDeparture() {
                assertErrorCode(
                        () ->
                                AgendaItem.create(
                                        room(),
                                        AgendaSubject.BUDGET,
                                        1,
                                        "제목",
                                        4,
                                        D_MINUS_1,
                                        D_MINUS_1.plusDays(4)),
                        AgendaErrorCode.AGENDA_DEADLINE_PASSED);
            }

            @Test
            @DisplayName("출발 전날 00시가 지나도 안건을 만들 수 없다")
            void rejectsAfterDayBeforeDeparture() {
                LocalDateTime now = D_MINUS_1.plusHours(5);

                assertErrorCode(
                        () -> AgendaItem.create(room(), AgendaSubject.BUDGET, 1, "제목", 4, now, now),
                        AgendaErrorCode.AGENDA_DEADLINE_PASSED);
            }

            @Test
            @DisplayName("투표자가 0명인 안건은 만들 수 없다")
            void rejectsZeroVoter() {
                assertThatThrownBy(() ->
                                AgendaItem.create(
                                        room(),
                                        AgendaSubject.BUDGET,
                                        1,
                                        "제목",
                                        0,
                                        NOW,
                                        NOW.plusDays(4)))
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
                agenda.resolve(optionOf(agenda), NOW);

                assertThat(agenda.isOpenAt(NOW)).isFalse();
            }
        }
    }

    @Nested
    class Eligibility {

        // 안건이 NOW에 저장됐다고 보고, 멤버의 합류 시각만 바꿔 본다
        private AgendaItem savedAgenda() {
            AgendaItem agenda = votingAgenda(AgendaSubject.BUDGET, 3);
            ReflectionTestUtils.setField(agenda, "createdAt", NOW);
            return agenda;
        }

        private RoomMember memberJoinedAt(LocalDateTime joinedAt) {
            return RoomMember.create(
                    room(),
                    User.create("minsu@example.com", "민수", "hash"),
                    Clock.fixed(joinedAt.atZone(SEOUL).toInstant(), SEOUL));
        }

        @Nested
        class Success {

            @Test
            @DisplayName("안건이 열리기 전에 합류한 멤버는 투표 자격이 있다")
            void eligibleWhenJoinedBefore() {
                assertThat(savedAgenda().isEligible(memberJoinedAt(NOW.minusDays(1)))).isTrue();
            }

            @Test
            @DisplayName("안건이 열린 시각과 같은 시각에 합류한 멤버도 투표 자격이 있다")
            void eligibleWhenJoinedAtSameTime() {
                assertThat(savedAgenda().isEligible(memberJoinedAt(NOW))).isTrue();
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("안건이 열린 뒤에 합류한 멤버는 투표 자격이 없다")
            void notEligibleWhenJoinedAfter() {
                assertThat(savedAgenda().isEligible(memberJoinedAt(NOW.plusSeconds(1)))).isFalse();
            }

            @Test
            @DisplayName("저장되지 않아 생성 시각이 없는 안건은 투표 자격을 판단할 수 없다")
            void rejectsUnsavedAgenda() {
                AgendaItem agenda = votingAgenda(AgendaSubject.BUDGET, 3);

                assertThatThrownBy(() -> agenda.isEligible(memberJoinedAt(NOW)))
                        .isInstanceOf(IllegalStateException.class);
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

                agenda.resolve(optionOf(agenda), NOW);

                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(agenda.getResolvedOptionId()).isEqualTo(OPTION_ID);
                assertThat(agenda.getResolvedAt()).isEqualTo(NOW);
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("다른 안건의 선택지로는 결정할 수 없다")
            void rejectsOptionOfOtherAgenda() {
                AgendaItem agenda = votingAgenda(AgendaSubject.PACE, 3);
                AgendaOption otherOption = optionOf(votingAgenda(AgendaSubject.PACE, 3));

                assertThatThrownBy(() -> agenda.resolve(otherOption, NOW))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.VOTING);
            }

            @Test
            @DisplayName("저장되지 않은 선택지로는 결정할 수 없다")
            void rejectsUnsavedOption() {
                AgendaItem agenda = votingAgenda(AgendaSubject.PACE, 3);

                assertThatThrownBy(() -> agenda.resolve(unsavedOptionOf(agenda), NOW))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.VOTING);
            }

            @Test
            @DisplayName("이미 결정된 안건은 다시 결정할 수 없다")
            void rejectsResolvedAgenda() {
                AgendaItem agenda = votingAgenda(AgendaSubject.PACE, 3);
                agenda.resolve(optionOf(agenda), NOW);

                assertErrorCode(
                        () -> agenda.resolve(optionOf(agenda), NOW), AgendaErrorCode.AGENDA_CLOSED);
            }

            @Test
            @DisplayName("미결 마감된 안건은 결정할 수 없다")
            void rejectsExpiredAgenda() {
                AgendaItem agenda = expiredAgenda(AgendaSubject.PACE);

                assertErrorCode(
                        () -> agenda.resolve(optionOf(agenda), NOW), AgendaErrorCode.AGENDA_CLOSED);
            }
        }
    }

    @Nested
    class Decide {

        private RoomMember member() {
            return RoomMember.create(
                    room(),
                    User.create("member@example.com", "멤버", "hash"),
                    Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
        }

        // 표는 선지 id로 같은지 비교하므로 저장된 것처럼 id를 넣는다
        private AgendaOption budgetOption(AgendaItem agenda, long amount, long id) {
            AgendaOption option = AgendaOption.budget(agenda, amount, (int) id, null);
            ReflectionTestUtils.setField(option, "id", id);
            return option;
        }

        private AgendaOption finalOption(AgendaItem agenda, FinalPlanChoice choice, long id) {
            AgendaOption option = AgendaOption.finalPlan(agenda, choice);
            ReflectionTestUtils.setField(option, "id", id);
            return option;
        }

        private Vote vote(AgendaItem agenda, AgendaOption option) {
            return Vote.create(agenda, option, member(), NOW);
        }

        @Nested
        class Success {

            @Test
            @DisplayName("자격자 전원이 같은 선지에 투표하면 그 선지로 결정할 수 있다")
            void decidesWhenAllVotedSameOption() {
                AgendaItem agenda = votingAgenda(AgendaSubject.BUDGET, 3);
                AgendaOption low = budgetOption(agenda, 600_000L, 1L);

                Optional<AgendaOption> decided =
                        agenda.decidableOption(
                                List.of(vote(agenda, low), vote(agenda, low), vote(agenda, low)));

                assertThat(decided).containsSame(low);
            }

            @Test
            @DisplayName("최종 확정에 반대 표가 하나라도 있으면 미투표자가 있어도 반대로 결정할 수 있다")
            void decidesRejectImmediately() {
                AgendaItem agenda = votingAgenda(AgendaSubject.FINAL_PLAN, 4);
                AgendaOption agree = finalOption(agenda, FinalPlanChoice.AGREE, 1L);
                AgendaOption reject = finalOption(agenda, FinalPlanChoice.REJECT, 2L);

                Optional<AgendaOption> decided =
                        agenda.decidableOption(List.of(vote(agenda, agree), vote(agenda, reject)));

                assertThat(decided).containsSame(reject);
            }

            @Test
            @DisplayName("최종 확정에 자격자 전원이 동의하면 동의로 결정할 수 있다")
            void decidesAgreeWhenAllAgreed() {
                AgendaItem agenda = votingAgenda(AgendaSubject.FINAL_PLAN, 2);
                AgendaOption agree = finalOption(agenda, FinalPlanChoice.AGREE, 1L);

                Optional<AgendaOption> decided =
                        agenda.decidableOption(List.of(vote(agenda, agree), vote(agenda, agree)));

                assertThat(decided).containsSame(agree);
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("표 수가 자격자 수보다 적으면 모두 같은 선지여도 결정하지 않는다")
            void waitsForRemainingVoters() {
                AgendaItem agenda = votingAgenda(AgendaSubject.BUDGET, 3);
                AgendaOption low = budgetOption(agenda, 600_000L, 1L);

                Optional<AgendaOption> decided =
                        agenda.decidableOption(List.of(vote(agenda, low), vote(agenda, low)));

                assertThat(decided).isEmpty();
            }

            @Test
            @DisplayName("자격자 전원이 투표했어도 표가 갈리면 결정하지 않는다")
            void doesNotDecideWhenSplit() {
                AgendaItem agenda = votingAgenda(AgendaSubject.BUDGET, 2);
                AgendaOption low = budgetOption(agenda, 600_000L, 1L);
                AgendaOption high = budgetOption(agenda, 1_000_000L, 2L);

                Optional<AgendaOption> decided =
                        agenda.decidableOption(List.of(vote(agenda, low), vote(agenda, high)));

                assertThat(decided).isEmpty();
            }

            @Test
            @DisplayName("최종 확정에 동의 표만 있고 미투표자가 있으면 결정하지 않는다")
            void waitsForRemainingFinalVoters() {
                AgendaItem agenda = votingAgenda(AgendaSubject.FINAL_PLAN, 3);
                AgendaOption agree = finalOption(agenda, FinalPlanChoice.AGREE, 1L);

                Optional<AgendaOption> decided =
                        agenda.decidableOption(List.of(vote(agenda, agree), vote(agenda, agree)));

                assertThat(decided).isEmpty();
            }

            @Test
            @DisplayName("투표 중이 아닌 안건은 표가 모두 같아도 결정하지 않는다")
            void doesNotDecideClosedAgenda() {
                AgendaItem agenda = votingAgenda(AgendaSubject.BUDGET, 1);
                AgendaOption low = budgetOption(agenda, 600_000L, 1L);
                List<Vote> votes = List.of(vote(agenda, low));
                agenda.expire();

                assertThat(agenda.decidableOption(votes)).isEmpty();
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
        class Failure {

            @Test
            @DisplayName("이미 결정된 안건은 미결로 마감할 수 없다")
            void rejectsResolvedAgenda() {
                AgendaItem agenda = votingAgenda(AgendaSubject.BUDGET, 3);
                agenda.resolve(optionOf(agenda), NOW);

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
        class Failure {

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
    class FirstRound {

        @Nested
        class Success {

            @Test
            @DisplayName("설문 마감 때 1회차 안건은 주제별 제목으로 열리고 마감은 설문 마감부터 4일 뒤다")
            void opensFirstRound() {
                AgendaItem agenda = AgendaItem.openFirstRound(room(), AgendaSubject.STAY, 3, NOW);

                assertThat(agenda.getSubject()).isEqualTo(AgendaSubject.STAY);
                assertThat(agenda.getRound()).isEqualTo(1);
                assertThat(agenda.getTitle()).isEqualTo("숙소 타입을 정해주세요");
                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.VOTING);
                assertThat(agenda.getTotalVoter()).isEqualTo(3);
                assertThat(agenda.getDeadlineAt()).isEqualTo(NOW.plusDays(4));
            }

            @Test
            @DisplayName("출발까지 4일이 안 남았으면 1회차 마감을 출발 전날 00시로 줄인다")
            void capsFirstRoundDeadline() {
                AgendaItem agenda =
                        AgendaItem.openFirstRound(
                                room(), AgendaSubject.BUDGET, 3, D_MINUS_1.minusDays(2));

                assertThat(agenda.getDeadlineAt()).isEqualTo(D_MINUS_1);
            }

            @Test
            @DisplayName("투표 없이 결정하면 마감이 지금으로 바뀌고 RESOLVED가 된다")
            void resolvesWithoutVote() {
                AgendaItem agenda = AgendaItem.openFirstRound(room(), AgendaSubject.PACE, 3, NOW);

                agenda.resolveWithoutVote(optionOf(agenda), NOW);

                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(agenda.getDeadlineAt()).isEqualTo(NOW);
                assertThat(agenda.getResolvedOptionId()).isEqualTo(OPTION_ID);
            }

            @Test
            @DisplayName("출발 전날 00시 전에만 안건을 열 수 있다")
            void canOpenOnlyBeforeDayBeforeDeparture() {
                assertThat(AgendaItem.canOpenAt(room(), D_MINUS_1.minusSeconds(1))).isTrue();
                assertThat(AgendaItem.canOpenAt(room(), D_MINUS_1)).isFalse();
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("최종 확정 안건은 설문 마감 때 1회차로 열 수 없다")
            void rejectsFinalPlan() {
                assertThatThrownBy(() ->
                                AgendaItem.openFirstRound(room(), AgendaSubject.FINAL_PLAN, 3, NOW))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("이미 끝난 안건은 투표 없이 결정할 수 없다")
            void rejectsResolvingClosedAgenda() {
                AgendaItem agenda = expiredAgenda(AgendaSubject.STAY);

                assertErrorCode(
                        () -> agenda.resolveWithoutVote(optionOf(agenda), NOW),
                        AgendaErrorCode.AGENDA_CLOSED);
            }
        }
    }

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
        class Failure {

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

                // create와 같은 규칙이라 같은 에러 코드를 쓴다
                assertErrorCode(
                        () -> expired.openNextRound(4, D_MINUS_1),
                        AgendaErrorCode.AGENDA_DEADLINE_PASSED);
            }
        }
    }
}
