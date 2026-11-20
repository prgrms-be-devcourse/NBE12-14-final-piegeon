package world.chakchak.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.entity.FinalPlanChoice;
import world.chakchak.domain.agenda.errorcode.AgendaErrorCode;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.user.entity.User;
import world.chakchak.global.error.BusinessException;

class AgendaOptionTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
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
        return AgendaItem.create(ROOM, subject, 1, "안건 제목", 3, NOW, NOW.plusDays(4));
    }

    private static RoomMember member(String name) {
        return RoomMember.create(ROOM, User.create(name + "@example.com", name, "hash"), CLOCK);
    }

    private static List<String> values(List<AgendaOption> options) {
        return options.stream().map(AgendaOption::getValue).toList();
    }

    private static List<Integer> indexes(List<AgendaOption> options) {
        return options.stream().map(AgendaOption::getDisplayIndex).toList();
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
            @DisplayName("설문에서 나온 예산 선지는 금액을 값으로, 만원 단위 글자를 라벨로 쓰고 추가한 사람이 없다")
            void createsSurveyBudgetOption() {
                AgendaItem agenda = agenda(AgendaSubject.BUDGET);

                AgendaOption option = AgendaOption.budget(agenda, 1_200_000L, 2, null);

                assertThat(option.getAgendaItem()).isSameAs(agenda);
                assertThat(option.getValue()).isEqualTo("1200000");
                assertThat(option.getLabel()).isEqualTo("120만원");
                assertThat(option.getDisplayIndex()).isEqualTo(2);
                assertThat(option.getProposedBy()).isNull();
            }

            @Test
            @DisplayName("멤버가 추가한 예산 선지는 추가한 멤버를 기록한다")
            void createsProposedBudgetOption() {
                RoomMember minsu = member("minsu");

                AgendaOption option =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), 1_500_000L, 3, minsu);

                assertThat(option.getProposedBy()).isSameAs(minsu);
            }

            @Test
            @DisplayName("다음 회차로 옮긴 선지는 값, 라벨, 추가한 멤버가 같고 새 안건과 순서를 가진다")
            void copiesToNextRound() {
                RoomMember minsu = member("minsu");
                AgendaOption original =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), 1_200_000L, 3, minsu);
                AgendaItem next = agenda(AgendaSubject.BUDGET);

                AgendaOption copied = original.copyTo(next, 0);

                assertThat(copied.getAgendaItem()).isSameAs(next);
                assertThat(copied.getValue()).isEqualTo("1200000");
                assertThat(copied.getLabel()).isEqualTo("120만원");
                assertThat(copied.getDisplayIndex()).isZero();
                assertThat(copied.getProposedBy()).isSameAs(minsu);
            }

            @Test
            @DisplayName("설문 금액들은 만원 단위로 반올림하고 같은 금액을 하나로 합쳐 낮은 순의 예산 선지가 된다")
            void buildsBudgetOptionsInAscendingOrder() {
                List<AgendaOption> options =
                        AgendaOption.budgetOptions(
                                agenda(AgendaSubject.BUDGET),
                                List.of(1_000_000L, 604_000L, 596_000L),
                                List.of());

                assertThat(values(options)).containsExactly("600000", "1000000");
                assertThat(indexes(options)).containsExactly(0, 1);
                assertThat(options).allMatch(o -> o.getProposedBy() == null);
            }

            @Test
            @DisplayName("직전 회차에 멤버가 추가한 예산 선지는 추가한 멤버를 유지한 채 금액 순서에 끼워 넣는다")
            void carriesProposedOptionsWithProposer() {
                RoomMember minsu = member("minsu");
                AgendaOption proposed =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), 800_000L, 2, minsu);

                List<AgendaOption> options =
                        AgendaOption.budgetOptions(
                                agenda(AgendaSubject.BUDGET),
                                List.of(600_000L, 1_000_000L),
                                List.of(proposed));

                assertThat(values(options)).containsExactly("600000", "800000", "1000000");
                assertThat(indexes(options)).containsExactly(0, 1, 2);
                assertThat(options.get(1).getProposedBy()).isSameAs(minsu);
            }

            @Test
            @DisplayName("직전 회차 추가 선지가 설문 금액과 같으면 설문 선지 하나만 남는다")
            void keepsSurveyOptionOnDuplicateAmount() {
                AgendaOption proposed =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), 600_000L, 1, member("a"));

                List<AgendaOption> options =
                        AgendaOption.budgetOptions(
                                agenda(AgendaSubject.BUDGET), List.of(600_000L), List.of(proposed));

                assertThat(options)
                        .singleElement()
                        .satisfies(o -> {
                            assertThat(o.getValue()).isEqualTo("600000");
                            assertThat(o.getProposedBy()).isNull();
                        });
            }

            @Test
            @DisplayName("숙소 타입과 이동 강도는 나온 값을 중복 없이 선언 순서대로 선지로 만든다")
            void buildsEnumOptionsInDeclarationOrder() {
                List<AgendaOption> stays =
                        AgendaOption.stayOptions(
                                agenda(AgendaSubject.STAY),
                                List.of(StayType.SOCIAL, StayType.HOTEL, StayType.SOCIAL));
                List<AgendaOption> paces =
                        AgendaOption.paceOptions(
                                agenda(AgendaSubject.PACE),
                                List.of(Pace.HURRY, Pace.RELAXED, Pace.HURRY));

                assertThat(values(stays)).containsExactly("HOTEL", "SOCIAL");
                assertThat(stays.getFirst().getLabel()).isEqualTo(StayType.HOTEL.getLabel());
                assertThat(indexes(stays)).containsExactly(0, 1);
                assertThat(values(paces)).containsExactly("RELAXED", "HURRY");
            }

            @Test
            @DisplayName("답이 없으면 직전 회차 추가 선지가 있어도 빈 목록이다")
            void returnsEmptyWithoutAnswers() {
                AgendaOption proposed =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), 800_000L, 0, member("a"));

                assertThat(
                                AgendaOption.budgetOptions(
                                        agenda(AgendaSubject.BUDGET),
                                        Arrays.asList(null, null),
                                        List.of(proposed)))
                        .isEmpty();
                assertThat(AgendaOption.stayOptions(agenda(AgendaSubject.STAY), List.of()))
                        .isEmpty();
            }

            @ParameterizedTest(name = "{0}원 → {1} / {2}")
            @CsvSource(
                    delimiter = '|',
                    value = {
                        "1234567|1230000|123만원",
                        "1234999|1230000|123만원",
                        "1235000|1240000|124만원",
                        "1226000|1230000|123만원",
                        "1000000|1000000|100만원",
                        "15000|20000|2만원",
                        "14999|10000|1만원"
                    })
            @DisplayName("예산 선지는 금액을 만원 단위로 반올림해 값과 라벨에 쓴다")
            void roundsBudgetToMan(long amount, String value, String label) {
                AgendaOption option =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), amount, 0, null);

                assertThat(option.getValue()).isEqualTo(value);
                assertThat(option.getLabel()).isEqualTo(label);
            }

            @ParameterizedTest(name = "{0}원 → {1}원")
            @CsvSource({"1, 10000", "4999, 10000", "5000, 10000"})
            @DisplayName("반올림한 예산이 1만원보다 작으면 1만원으로 맞춘다")
            void raisesToMinimumMan(long amount, long expected) {
                assertThat(AgendaOption.roundBudget(amount)).isEqualTo(expected);
            }

            @ParameterizedTest(name = "{0}원 → {1}원")
            @CsvSource({"99994999, 99990000", "99995000, 99990000", "99999999, 99990000"})
            @DisplayName("반올림한 예산이 9,999만원을 넘으면 9,999만원으로 맞춘다")
            void capsToMaximumMan(long amount, long expected) {
                assertThat(AgendaOption.roundBudget(amount)).isEqualTo(expected);
            }

            @Test
            @DisplayName("1,000만원 이상은 천 단위 쉼표를 붙여 라벨을 만든다")
            void formatsLargeBudgetLabel() {
                AgendaOption option =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), 12_340_000L, 0, null);

                assertThat(option.getLabel()).isEqualTo("1,234만원");
            }

            @Test
            @DisplayName("숙소 타입 선지는 enum 이름을 값으로, enum 라벨을 라벨로 쓴다")
            void createsStayOption() {
                AgendaOption option =
                        AgendaOption.stay(agenda(AgendaSubject.STAY), StayType.PERSONAL, 1);

                assertThat(option.getValue()).isEqualTo("PERSONAL");
                assertThat(option.getLabel()).isEqualTo(StayType.PERSONAL.getLabel());
                assertThat(option.getProposedBy()).isNull();
            }

            @Test
            @DisplayName("이동 강도 선지는 enum 이름을 값으로, enum 라벨을 라벨로 쓴다")
            void createsPaceOption() {
                AgendaOption option = AgendaOption.pace(agenda(AgendaSubject.PACE), Pace.HURRY, 0);

                assertThat(option.getValue()).isEqualTo("HURRY");
                assertThat(option.getLabel()).isEqualTo(Pace.HURRY.getLabel());
            }

            @Test
            @DisplayName("최종 확정 선지는 동의가 먼저, 반대가 다음 순서다")
            void createsFinalPlanOptions() {
                AgendaItem agenda = agenda(AgendaSubject.FINAL_PLAN);

                AgendaOption agree = AgendaOption.finalPlan(agenda, FinalPlanChoice.AGREE);
                AgendaOption reject = AgendaOption.finalPlan(agenda, FinalPlanChoice.REJECT);

                assertThat(agree.getValue()).isEqualTo("AGREE");
                assertThat(agree.getLabel()).isEqualTo("동의");
                assertThat(reject.getValue()).isEqualTo("REJECT");
                assertThat(reject.getLabel()).isEqualTo("반대");
                assertThat(agree.getDisplayIndex()).isLessThan(reject.getDisplayIndex());
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("예산 안건이 아니면 예산 선지를 추가할 수 없다")
            void rejectsBudgetOnOtherSubject() {
                assertErrorCode(
                        () ->
                                AgendaOption.budget(
                                        agenda(AgendaSubject.STAY), 1_000_000L, 0, member("a")),
                        AgendaErrorCode.OPTION_NOT_ADDABLE);
            }

            @Test
            @DisplayName("주제가 다른 안건으로는 선지를 옮길 수 없다")
            void rejectsCopyToOtherSubject() {
                AgendaOption budget =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), 1_000_000L, 0, null);

                assertThatThrownBy(() -> budget.copyTo(agenda(AgendaSubject.STAY), 0))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("예산 안건이 아니면 설문 금액들로 예산 선지 목록을 만들 수 없다")
            void rejectsBudgetOptionsOnOtherSubject() {
                assertErrorCode(
                        () ->
                                AgendaOption.budgetOptions(
                                        agenda(AgendaSubject.STAY), List.of(1_000_000L), List.of()),
                        AgendaErrorCode.OPTION_NOT_ADDABLE);
            }

            @Test
            @DisplayName("숙소 안건이 아니면 숙소 선지 목록을 만들 수 없다")
            void rejectsStayOptionsOnOtherSubject() {
                assertThatThrownBy(() ->
                                AgendaOption.stayOptions(
                                        agenda(AgendaSubject.PACE), List.of(StayType.HOTEL)))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @ParameterizedTest
            @CsvSource({"0", "-1", "100000000"})
            @DisplayName("예산 범위(1원~99,999,999원)를 벗어난 선지는 만들 수 없다")
            void rejectsBudgetOutOfRange(long amount) {
                assertThatThrownBy(() ->
                                AgendaOption.budget(agenda(AgendaSubject.BUDGET), amount, 0, null))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("주제가 다른 안건에는 숙소·이동 강도·최종 확정 선지를 만들 수 없다")
            void rejectsMismatchedSubject() {
                AgendaItem budget = agenda(AgendaSubject.BUDGET);

                assertThatThrownBy(() -> AgendaOption.stay(budget, StayType.HOTEL, 0))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> AgendaOption.pace(budget, Pace.NORMAL, 0))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> AgendaOption.finalPlan(budget, FinalPlanChoice.AGREE))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("화면 순서가 음수인 선지는 만들 수 없다")
            void rejectsNegativeDisplayIndex() {
                assertThatThrownBy(() ->
                                AgendaOption.stay(agenda(AgendaSubject.STAY), StayType.HOTEL, -1))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("선지는 자신을 만든 안건에만 속한다")
            void belongsToOwnAgenda() {
                AgendaItem agenda = agenda(AgendaSubject.STAY);
                AgendaOption option = AgendaOption.stay(agenda, StayType.HOTEL, 0);

                assertThat(option.belongsTo(agenda)).isTrue();
                assertThat(option.belongsTo(agenda(AgendaSubject.STAY))).isFalse();
            }

            @Test
            @DisplayName("직접 추가한 멤버만 그 선지의 추가자로 판단한다")
            void checksProposer() {
                RoomMember minsu = member("minsu");
                AgendaOption option =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), 1_000_000L, 0, minsu);

                assertThat(option.isProposedBy(minsu)).isTrue();
                assertThat(option.isProposedBy(member("jieun"))).isFalse();
            }

            @Test
            @DisplayName("설문에서 나온 선지는 누구의 선지도 아니다")
            void surveyOptionHasNoProposer() {
                AgendaOption option =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), 1_000_000L, 0, null);

                assertThat(option.isProposedBy(member("minsu"))).isFalse();
            }

            @Test
            @DisplayName("예산 선지는 저장한 금액을 숫자로 돌려준다")
            void returnsBudgetAmount() {
                AgendaOption option =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), 800_000L, 0, null);

                assertThat(option.budgetAmount()).isEqualTo(800_000L);
            }

            @Test
            @DisplayName("최종 확정의 반대 선지만 반대로 판단한다")
            void checksReject() {
                AgendaItem agenda = agenda(AgendaSubject.FINAL_PLAN);

                assertThat(AgendaOption.finalPlan(agenda, FinalPlanChoice.REJECT).isReject())
                        .isTrue();
                assertThat(AgendaOption.finalPlan(agenda, FinalPlanChoice.AGREE).isReject())
                        .isFalse();
                assertThat(
                                AgendaOption.stay(agenda(AgendaSubject.STAY), StayType.HOTEL, 0)
                                        .isReject())
                        .isFalse();
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("예산 선지가 아니면 금액을 꺼낼 수 없다")
            void rejectsBudgetAmountOfOtherSubject() {
                AgendaOption option = AgendaOption.pace(agenda(AgendaSubject.PACE), Pace.NORMAL, 0);

                assertThatThrownBy(option::budgetAmount).isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Nested
    class Delete {

        @Nested
        class Success {

            @Test
            @DisplayName("직접 추가한 멤버는 삭제 권한 검사를 통과한다")
            void allowsProposer() {
                RoomMember minsu = member("minsu");
                AgendaOption option =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), 1_000_000L, 0, minsu);

                option.requireProposedBy(minsu);
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("다른 멤버가 추가한 선지는 지울 수 없다")
            void rejectsOtherMember() {
                AgendaOption option =
                        AgendaOption.budget(
                                agenda(AgendaSubject.BUDGET), 1_000_000L, 0, member("minsu"));

                assertErrorCode(
                        () -> option.requireProposedBy(member("jieun")),
                        AgendaErrorCode.NOT_OPTION_OWNER);
            }

            @Test
            @DisplayName("설문에서 나온 선지는 아무도 지울 수 없다")
            void rejectsSurveyOption() {
                AgendaOption option =
                        AgendaOption.budget(agenda(AgendaSubject.BUDGET), 1_000_000L, 0, null);

                assertErrorCode(
                        () -> option.requireProposedBy(member("minsu")),
                        AgendaErrorCode.NOT_OPTION_OWNER);
            }
        }
    }
}
