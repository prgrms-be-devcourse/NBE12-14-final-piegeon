package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import world.chakchak.domain.place.dto.PlaceCategory;

/** Google 장소 종류를 상세 화면 카테고리로 바꾸는 규칙. (P-10) */
class PlaceCategoryTest {

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("표에 있는 Google 종류는 정해진 카테고리로 바뀐다")
            void mapsKnownTypes() {
                assertThat(PlaceCategory.fromGoogleType("cafe")).isEqualTo(PlaceCategory.FOOD);
                assertThat(PlaceCategory.fromGoogleType("park")).isEqualTo(PlaceCategory.NATURE);
                assertThat(PlaceCategory.fromGoogleType("amusement_park"))
                        .isEqualTo(PlaceCategory.ACTIVITY);
                assertThat(PlaceCategory.fromGoogleType("market"))
                        .isEqualTo(PlaceCategory.SHOPPING);
                assertThat(PlaceCategory.fromGoogleType("tourist_attraction"))
                        .isEqualTo(PlaceCategory.CULTURE);
            }

            @Test
            @DisplayName("_restaurant로 끝나면 맛집, _store로 끝나면 쇼핑·투어다")
            void mapsBySuffix() {
                assertThat(PlaceCategory.fromGoogleType("korean_restaurant"))
                        .isEqualTo(PlaceCategory.FOOD);
                assertThat(PlaceCategory.fromGoogleType("clothing_store"))
                        .isEqualTo(PlaceCategory.SHOPPING);
            }

            @Test
            @DisplayName("표에 없거나 종류가 없으면 null이다")
            void unknownTypeIsNull() {
                assertThat(PlaceCategory.fromGoogleType("parking")).isNull();
                assertThat(PlaceCategory.fromGoogleType(null)).isNull();
            }

            @Test
            @DisplayName("카테고리 이름은 설문 '하고 싶은 것' 선택지와 같다")
            void labelsMatchSurvey() {
                assertThat(PlaceCategory.FOOD.getLabel()).isEqualTo("맛집");
                assertThat(PlaceCategory.NATURE.getLabel()).isEqualTo("자연·힐링");
                assertThat(PlaceCategory.ACTIVITY.getLabel()).isEqualTo("액티비티·체험");
                assertThat(PlaceCategory.SHOPPING.getLabel()).isEqualTo("쇼핑·투어");
                assertThat(PlaceCategory.CULTURE.getLabel()).isEqualTo("명소·문화탐방");
            }
        }
    }
}
