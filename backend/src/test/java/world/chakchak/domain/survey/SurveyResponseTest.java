package world.chakchak.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.errorcode.SurveyErrorCode;
import world.chakchak.global.error.BusinessException;

class SurveyResponseTest {

    private final Clock clock = Clock.systemUTC();

    @Nested
    class CreateForSurveying {

        @Test
        @DisplayName("예산의 최소 및 최대 경계값을 허용한다")
        void acceptsBudgetBoundaries() {
            assertThat(createForSurveying(1L, List.of(ActivityCode.FOOD)).getBudget())
                    .isEqualTo(1L);
            assertThat(createForSurveying(99_999_999L, List.of(ActivityCode.FOOD)).getBudget())
                    .isEqualTo(99_999_999L);
        }

        @Test
        @DisplayName("선호 활동을 한 개 또는 세 개 선택할 수 있다")
        void acceptsActivityCountBoundaries() {
            assertThat(createForSurveying(1L, List.of(ActivityCode.FOOD)).getActPrefs())
                    .containsExactly(ActivityCode.FOOD);
            assertThat(
                            createForSurveying(
                                            1L,
                                            List.of(
                                                    ActivityCode.FOOD,
                                                    ActivityCode.NATURE,
                                                    ActivityCode.ACTIVITY))
                                    .getActPrefs())
                    .containsExactly(ActivityCode.FOOD, ActivityCode.NATURE, ActivityCode.ACTIVITY);
        }

        @Test
        @DisplayName("중복 선호 활동은 한 가지로 계산하고 한 개 이상 세 개 이하만 허용한다")
        void countsDistinctActivityCodes() {
            assertThat(
                            createForSurveying(
                                            1L,
                                            List.of(
                                                    ActivityCode.FOOD,
                                                    ActivityCode.FOOD,
                                                    ActivityCode.NATURE,
                                                    ActivityCode.NATURE))
                                    .getActPrefs())
                    .containsExactly(ActivityCode.FOOD, ActivityCode.NATURE);

            assertBusinessError(
                    () -> createForSurveying(1L, List.of()),
                    SurveyErrorCode.ACTIVITY_COUNT_INVALID);
            assertBusinessError(
                    () ->
                            createForSurveying(
                                    1L,
                                    List.of(
                                            ActivityCode.FOOD,
                                            ActivityCode.NATURE,
                                            ActivityCode.ACTIVITY,
                                            ActivityCode.SHOPPING)),
                    SurveyErrorCode.ACTIVITY_COUNT_INVALID);
        }

        @Test
        @DisplayName("예산은 1원 미만이나 99,999,999원 초과를 허용하지 않는다")
        void rejectsBudgetOutsideRange() {
            assertBusinessError(
                    () -> createForSurveying(0L, List.of(ActivityCode.FOOD)),
                    SurveyErrorCode.BUDGET_INVALID);
            assertBusinessError(
                    () -> createForSurveying(100_000_000L, List.of(ActivityCode.FOOD)),
                    SurveyErrorCode.BUDGET_INVALID);
        }

        @Test
        @DisplayName("SURVEYING 응답은 예산과 숙박 유형과 여행 페이스를 필수로 요구한다")
        void requiresSurveyingFields() {
            assertBusinessError(
                    () ->
                            SurveyResponse.createForSurveying(
                                    null,
                                    null,
                                    StayType.HOTEL,
                                    Pace.NORMAL,
                                    List.of(ActivityCode.FOOD),
                                    "장거리 걷기",
                                    clock),
                    SurveyErrorCode.BUDGET_REQUIRED);
            assertBusinessError(
                    () ->
                            SurveyResponse.createForSurveying(
                                    null,
                                    1L,
                                    null,
                                    Pace.NORMAL,
                                    List.of(ActivityCode.FOOD),
                                    "장거리 걷기",
                                    clock),
                    SurveyErrorCode.STAY_TYPE_REQUIRED);
            assertBusinessError(
                    () ->
                            SurveyResponse.createForSurveying(
                                    null,
                                    1L,
                                    StayType.HOTEL,
                                    null,
                                    List.of(ActivityCode.FOOD),
                                    "장거리 걷기",
                                    clock),
                    SurveyErrorCode.PACE_REQUIRED);
        }

        @Test
        @DisplayName("기피 요소는 선택이며 100자를 초과할 수 없다")
        void validatesAvoidNote() {
            assertThat(
                            SurveyResponse.createForSurveying(
                                            null,
                                            1L,
                                            StayType.HOTEL,
                                            Pace.NORMAL,
                                            List.of(ActivityCode.FOOD),
                                            null,
                                            clock)
                                    .getAvoidNote())
                    .isNull();
            assertThat(
                            SurveyResponse.createForSurveying(
                                            null,
                                            1L,
                                            StayType.HOTEL,
                                            Pace.NORMAL,
                                            List.of(ActivityCode.FOOD),
                                            "가".repeat(100),
                                            clock)
                                    .getAvoidNote())
                    .hasSize(100);
        }
    }

    @Nested
    class CreateForPlanning {

        @Test
        @DisplayName("PLANNING 응답은 예산을 null로 두고 선호 활동을 검증한다")
        void keepsBudgetNull() {
            SurveyResponse response =
                    SurveyResponse.createForPlanning(
                            null, List.of(ActivityCode.FOOD), "장거리 걷기", clock);

            assertThat(response.getBudget()).isNull();
            assertThat(response.getActPrefs()).containsExactly(ActivityCode.FOOD);
        }
    }

    private SurveyResponse createForSurveying(Long budget, List<ActivityCode> actPrefs) {
        return SurveyResponse.createForSurveying(
                null, budget, StayType.HOTEL, Pace.NORMAL, actPrefs, "장거리 걷기", clock);
    }

    private void assertBusinessError(ThrowingCallable action, SurveyErrorCode expectedErrorCode) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(expectedErrorCode);
    }
}
