package world.chakchak.domain.place.service;

import java.util.Optional;
import world.chakchak.domain.place.dto.GooglePlace;
import world.chakchak.domain.place.dto.PlaceSearchResult;

/**
 * 외부 장소 검색 서비스(Google Places)를 부르는 창구.
 *
 * <p>place service는 이 인터페이스만 알고, 실제 HTTP 호출은 infra.google.GooglePlacesClient가 구현한다. 그래서 service
 * 테스트에서는 이 인터페이스를 Mockito로 바꿔 끼우면 Google을 부르지 않고 테스트할 수 있다.
 */
public interface PlaceSearchClient {

    /**
     * 검색어로 장소를 찾는다. (P-01)
     *
     * @param query 검색어 (2~50자 검증은 요청 DTO에서 한다)
     * @param pageToken 다음 페이지 토큰. 첫 페이지면 null
     */
    PlaceSearchResult search(String query, String pageToken);

    /**
     * Google 장소 ID로 장소 하나를 조회한다. (P-02)
     *
     * @return Google에 없는 장소면 Optional.empty(). PLACE_NOT_FOUND로 바꾸는 일은 service가 한다.
     */
    Optional<GooglePlace> findById(String googlePlaceId);

    /**
     * 등록 때 저장한 사진 이름으로 Google에서 사진 주소를 받는다. (P-11)
     *
     * <p>받은 주소는 잠깐만 쓸 수 있어서 저장하지 않는다.
     *
     * @param photoName "places/{장소 ID}/photos/{사진 ID}"
     * @return Google에서 사진이 사라졌으면 Optional.empty(). PLACE_PHOTO_NOT_FOUND로 바꾸는 일은 service가 한다.
     */
    Optional<String> findPhotoUri(String photoName);
}
