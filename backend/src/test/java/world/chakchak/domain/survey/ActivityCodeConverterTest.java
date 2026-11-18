package world.chakchak.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.ActivityCodeConverter;

class ActivityCodeConverterTest {

    private final ActivityCodeConverter converter = new ActivityCodeConverter();

    @Nested
    class ToDatabase {

        @Test
        @DisplayName("ActivityCode 하나를 Enum 이름으로 변환한다")
        void convertsSingleValue() {
            assertThat(converter.convertToDatabaseColumn(List.of(ActivityCode.FOOD)))
                    .isEqualTo("FOOD");
        }

        @Test
        @DisplayName("복수 ActivityCode를 중복 없이 쉼표로 구분해 변환한다")
        void convertsMultipleValuesWithoutDuplicates() {
            assertThat(
                            converter.convertToDatabaseColumn(
                                    List.of(
                                            ActivityCode.FOOD,
                                            ActivityCode.NATURE,
                                            ActivityCode.FOOD)))
                    .isEqualTo("FOOD,NATURE");
        }

        @Test
        @DisplayName("null 리스트는 DB null로 변환한다")
        void convertsNull() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        @DisplayName("빈 리스트는 빈 문자열로 변환한다")
        void convertsEmptyList() {
            assertThat(converter.convertToDatabaseColumn(List.of())).isEmpty();
        }
    }

    @Nested
    class FromDatabase {

        @Test
        @DisplayName("쉼표로 구분된 ActivityCode를 Enum 리스트로 복원한다")
        void restoresMultipleValues() {
            assertThat(converter.convertToEntityAttribute("FOOD,NATURE"))
                    .containsExactly(ActivityCode.FOOD, ActivityCode.NATURE);
        }

        @Test
        @DisplayName("DB null은 null 리스트로 복원한다")
        void restoresNull() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("빈 문자열은 빈 리스트로 복원한다")
        void restoresEmptyString() {
            assertThat(converter.convertToEntityAttribute(" ")).isEmpty();
        }

        @Test
        @DisplayName("정의되지 않은 ActivityCode 문자열은 예외를 발생시킨다")
        void rejectsUnknownValue() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("UNKNOWN"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
