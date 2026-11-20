package world.chakchak.domain.place.dto;

import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 장소 상세에 보여 줄 장소 종류. (P-10)
 *
 * <p>설문 "하고 싶은 것"(ActivityCode)과 같은 5개 값이다. DB에는 Google이 준 종류(place_type, 예: "market")만 저장하고, 응답할
 * 때마다 여기서 바꾼다. 표에 없는 종류는 분류하지 않고 null로 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum PlaceCategory {
    FOOD("맛집"),
    NATURE("자연·힐링"),
    ACTIVITY("액티비티·체험"),
    SHOPPING("쇼핑·투어"),
    CULTURE("명소·문화탐방");

    /** 화면에 보여 줄 이름 */
    private final String label;

    // Google 장소 종류 -> 카테고리. "_restaurant", "_store"로 끝나는 종류는 아래 fromGoogleType에서 따로 처리한다
    private static final Map<String, PlaceCategory> BY_GOOGLE_TYPE =
            Map.ofEntries(
                    Map.entry("restaurant", FOOD),
                    Map.entry("cafe", FOOD),
                    Map.entry("coffee_shop", FOOD),
                    Map.entry("bakery", FOOD),
                    Map.entry("dessert_shop", FOOD),
                    Map.entry("ice_cream_shop", FOOD),
                    Map.entry("bar", FOOD),
                    Map.entry("pub", FOOD),
                    Map.entry("food_court", FOOD),
                    Map.entry("park", NATURE),
                    Map.entry("national_park", NATURE),
                    Map.entry("hiking_area", NATURE),
                    Map.entry("beach", NATURE),
                    Map.entry("garden", NATURE),
                    Map.entry("botanical_garden", NATURE),
                    Map.entry("campground", NATURE),
                    Map.entry("spa", NATURE),
                    Map.entry("amusement_park", ACTIVITY),
                    Map.entry("water_park", ACTIVITY),
                    Map.entry("zoo", ACTIVITY),
                    Map.entry("bowling_alley", ACTIVITY),
                    Map.entry("ski_resort", ACTIVITY),
                    Map.entry("golf_course", ACTIVITY),
                    Map.entry("karaoke", ACTIVITY),
                    Map.entry("market", SHOPPING),
                    Map.entry("shopping_mall", SHOPPING),
                    Map.entry("department_store", SHOPPING),
                    Map.entry("tourist_attraction", CULTURE),
                    Map.entry("museum", CULTURE),
                    Map.entry("art_gallery", CULTURE),
                    Map.entry("historical_landmark", CULTURE),
                    Map.entry("cultural_landmark", CULTURE),
                    Map.entry("monument", CULTURE),
                    Map.entry("aquarium", CULTURE),
                    Map.entry("observation_deck", CULTURE),
                    Map.entry("performing_arts_theater", CULTURE),
                    Map.entry("place_of_worship", CULTURE));

    /**
     * Google이 준 장소 종류를 카테고리로 바꾼다.
     *
     * @param googleType Google 장소 종류 (예: "korean_restaurant"). 없을 수 있다
     * @return 해당하는 카테고리. 표에 없거나 종류가 없으면 null
     */
    public static PlaceCategory fromGoogleType(String googleType) {
        if (googleType == null) {
            return null;
        }
        // korean_restaurant, sushi_restaurant처럼 음식점 종류는 이름 끝이 같다
        if (googleType.endsWith("_restaurant")) {
            return FOOD;
        }
        // book_store, clothing_store처럼 가게 종류도 이름 끝이 같다
        if (googleType.endsWith("_store")) {
            return SHOPPING;
        }
        return BY_GOOGLE_TYPE.get(googleType);
    }
}
