package world.chakchak.infra.google;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
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

    // TODO PLACE_DETAILS_PATH = "/v1/places/{placeId}" (P-02 findById에서 사용)
    // TODO DETAILS_FIELD_MASK - 상세 조회용 필드. 검색과 달리 "places." 접두사 없이 적는다 (예: "id,displayName")

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
            if (status.is4xxClientError() && !status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                // 키, 권한 문제나 우리 요청 버그. 사람이 바로 봐야 한다.
                log.error("Google Places 요청 오류: status={} body={}", status.value(), body);
            } else {
                // 429(한도 초과)와 5xx는 잠깐 기다리면 풀린다.
                log.warn("Google Places 일시 오류 status={}", status.value());
            }
            throw new BusinessException(PlaceErrorCode.PLACES_API_ERROR);
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

    // TODO findById(String googlePlaceId) 구현 (P-02)
    //  - GET PLACE_DETAILS_PATH, 헤더는 search와 같고 FieldMask만 DETAILS_FIELD_MASK
    //  - Google이 404를 주면 Optional.empty() (HttpClientErrorException.NotFound를 잡는다)
    //  - 그 밖의 실패는 search와 같이 PLACES_API_ERROR

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
