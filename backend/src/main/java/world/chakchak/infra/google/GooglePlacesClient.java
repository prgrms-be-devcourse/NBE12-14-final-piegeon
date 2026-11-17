package world.chakchak.infra.google;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import world.chakchak.domain.place.dto.GooglePlace;
import world.chakchak.domain.place.dto.PlaceSearchResult;
import world.chakchak.domain.place.errorcode.PlaceErrorCode;
import world.chakchak.domain.place.service.PlaceSearchClient;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

/**
 * Google Places API (New)를 HTTP로 부르는 PlaceSearchClient 구현체.
 *
 * <p>API 키는 브라우저에 노출되지 않도록 서버에서만 붙인다. 응답에서 받을 필드는 FieldMask 헤더로 정한다. Google은 요청한 필드만큼 요금을 매기므로, 필요한
 * 필드만 적는다(명세 P-01 "필요한 필드만 요청").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GooglePlacesClient implements PlaceSearchClient {

    static final String SEARCH_TEXT_PATH = "/v1/places:searchText";
    static final String API_KEY_HEADER = "X-Goog-Api-Key";
    static final String FIELD_MASK_HEADER = "X-Goog-FieldMask";
    static final String LANGUAGE_CODE = "ko";

    /** 검색 결과에서 받을 필드. GooglePlace 필드를 늘리면 여기에도 추가한다. */
    static final String SEARCH_FIELD_MASK =
            "places.id,places.displayName,places.formattedAddress,places.primaryType,nextPageToken";

    static final String PLACE_DETAILS_PATH = "/v1/places/{placeId}";

    /** 상세 조회에서 받을 필드. 검색과 달리 "places." 접두사가 없다. */
    static final String DETAILS_FIELD_MASK = "id,displayName,formattedAddress,primaryType";

    private final RestClient googlePlacesRestClient;

    private final GooglePlacesProperties properties;

    @Override
    public PlaceSearchResult search(String query, String pageToken) {
        SearchTextResponse response;
        try {
            response =
                    googlePlacesRestClient
                            .post()
                            .uri(SEARCH_TEXT_PATH)
                            .header(API_KEY_HEADER, properties.apiKey())
                            .header(FIELD_MASK_HEADER, SEARCH_FIELD_MASK)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new SearchTextRequest(query, pageToken, LANGUAGE_CODE))
                            .retrieve()
                            .body(SearchTextResponse.class);
        } catch (RestClientResponseException e) {
            // 응답은 받았는데 실패한 경우 (4xx, 5xx)
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            if (status.isSameCodeAs(HttpStatus.BAD_REQUEST) && pageToken != null) {
                log.info("Google Places pageToken 거절: body={}", body);
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            throw toPlacesApiError(e);
        } catch (RestClientException e) {
            // 응답을 못 받았거나(타임아웃, 연결 실패) 응답을 읽지 못한 경우
            log.warn("Google Places 호출 실패: {}", e.getMessage());
            throw new BusinessException(PlaceErrorCode.PLACES_API_ERROR);
        }

        if (response == null || response.places() == null) {
            return new PlaceSearchResult(List.of(), null);
        }
        List<GooglePlace> places =
                response.places().stream().map(PlaceItem::toGooglePlace).toList();
        return new PlaceSearchResult(places, response.nextPageToken());
    }

    @Override
    public Optional<GooglePlace> findById(String googlePlaceId) {
        PlaceItem item;
        try {
            item =
                    googlePlacesRestClient
                            .get()
                            .uri(
                                    PLACE_DETAILS_PATH + "?languageCode={languageCode}",
                                    googlePlaceId,
                                    LANGUAGE_CODE)
                            .header(API_KEY_HEADER, properties.apiKey())
                            .header(FIELD_MASK_HEADER, DETAILS_FIELD_MASK)
                            .retrieve()
                            .body(PlaceItem.class);
        } catch (RestClientResponseException e) {
            // 404: 없어진 장소, 400: 형식이 틀린 장소 ID. 둘 다 "Google에 없는 장소"로 본다.
            HttpStatusCode status = e.getStatusCode();
            if (status.isSameCodeAs(HttpStatus.NOT_FOUND)
                    || status.isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                log.info(
                        "Google Places 장소 없음: id={} status={} body={}",
                        googlePlaceId,
                        status.value(),
                        e.getResponseBodyAsString());
                return Optional.empty();
            }
            throw toPlacesApiError(e);
        } catch (RestClientException e) {
            log.warn("Google Places 호출 실패: {}", e.getMessage());
            throw new BusinessException(PlaceErrorCode.PLACES_API_ERROR);
        }
        return Optional.ofNullable(item).map(PlaceItem::toGooglePlace);
    }

    /** 응답을 받았지만 실패한 경우의 공통 처리. 로그를 남기고 PLACES_API_ERROR로 바꾼다. */
    private BusinessException toPlacesApiError(RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        if (status.is4xxClientError() && !status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            log.error(
                    "Google Places 요청 오류: status={} body={}",
                    status.value(),
                    e.getResponseBodyAsString());
        } else {
            log.warn("Google Places 일시 오류: status={}", status.value());
        }
        return new BusinessException(PlaceErrorCode.PLACES_API_ERROR);
    }

    /** Google searchText 요청. pageToken이 null이면 JSON에서 아예 뺀다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SearchTextRequest(String textQuery, String pageToken, String languageCode) {}

    /** Google searchText 응답. 필요한 필드만 받는다. */
    private record SearchTextResponse(List<PlaceItem> places, String nextPageToken) {}

    /** Google 응답의 장소 하나 */
    private record PlaceItem(
            String id, DisplayName displayName, String formattedAddress, String primaryType) {
        GooglePlace toGooglePlace() {
            String name = displayName == null ? null : displayName.text();
            return new GooglePlace(id, name, formattedAddress, primaryType);
        }
    }

    /** Google은 이름을 {"text" : "광장시장", "languageCode" : "ko"} 형태로 준다. text만 쓴다. */
    private record DisplayName(String text) {}
}
