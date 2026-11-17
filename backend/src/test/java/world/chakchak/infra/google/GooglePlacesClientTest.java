package world.chakchak.infra.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import world.chakchak.domain.place.dto.GooglePlace;
import world.chakchak.domain.place.dto.PlaceSearchResult;
import world.chakchak.domain.place.errorcode.PlaceErrorCode;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

/**
 * GooglePlacesClient 단위 테스트. Spring을 띄우지 않고, MockRestServiceServer가 Google 대신 응답한다. 실제 Google을 부르지
 * 않으므로 키도 인터넷도 필요 없다.
 */
class GooglePlacesClientTest {

    private static final String BASE_URL = "https://places.googleapis.com";

    private MockRestServiceServer server;
    private GooglePlacesClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesProperties properties =
                new GooglePlacesProperties("test-key", BASE_URL, Duration.ofSeconds(3));
        client = new GooglePlacesClient(builder.build(), properties);
    }

    @Test
    @DisplayName("장소 검색은 API 키와 받을 필드 목록을 헤더에 담아 Google에 보낸다")
    void searchSendsApiKeyAndFieldMask() {
        server.expect(requestTo(BASE_URL + GooglePlacesClient.SEARCH_TEXT_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(GooglePlacesClient.API_KEY_HEADER, "test-key"))
                .andExpect(
                        header(
                                GooglePlacesClient.FIELD_MASK_HEADER,
                                GooglePlacesClient.SEARCH_FIELD_MASK))
                .andRespond(withSuccess("{\"places\": []}", MediaType.APPLICATION_JSON));

        client.search("광장시장", null);

        server.verify();
    }

    // TODO (findById 구현 후) Google이 404를 주면 빈 Optional을 돌려준다 → withResourceNotFound()
    @Test
    @DisplayName("Google 응답의 장소는 GooglePlace로 바뀌고 다음 페이지 토큰도 함께 돌려준다")
    void searchConvertsGooglePlaces() {
        String googleResponse =
                """
                {
                  "places": [
                    {
                      "id": "place-1",
                      "displayName": {"text": "광장시장", "languageCode": "ko"},
                      "formattedAddress": "서울특별시 종로구 창경궁로 88",
                      "primaryType": "market"
                    },
                    {
                      "id": "place-2",
                      "displayName": {"text": "남대문시장", "languageCode": "ko"},
                      "formattedAddress": "서울특별시 중구 남대문시장4길 21",
                      "primaryType": "market"
                    }
                  ],
                  "nextPageToken": "next-token"
                }
                """;
        server.expect(requestTo(BASE_URL + GooglePlacesClient.SEARCH_TEXT_PATH))
                .andRespond(withSuccess(googleResponse, MediaType.APPLICATION_JSON));

        PlaceSearchResult result = client.search("시장", null);

        assertThat(result.places())
                .containsExactly(
                        new GooglePlace("place-1", "광장시장", "서울특별시 종로구 창경궁로 88", "market"),
                        new GooglePlace("place-2", "남대문시장", "서울특별시 중구 남대문시장4길 21", "market"));
        assertThat(result.nextPageToken()).isEqualTo("next-token");
    }

    @Test
    @DisplayName("Google이 검색 결과 없이 빈 응답을 주면 빈 목록을 돌려준다")
    void searchReturnsEmptyListWhenNoResult() {
        server.expect(requestTo(BASE_URL + GooglePlacesClient.SEARCH_TEXT_PATH))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        PlaceSearchResult result = client.search("없는장소", null);

        assertThat(result.places()).isEmpty();
        assertThat(result.nextPageToken()).isNull();
    }

    @Test
    @DisplayName("Google이 서버 오류를 주면 PLACES_API_ERROR를 던진다")
    void searchThrowsPlacesApiErrorWhenGoogleFails() {
        server.expect(requestTo(BASE_URL + GooglePlacesClient.SEARCH_TEXT_PATH))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.search("광장시장", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.PLACES_API_ERROR);
    }

    @Test
    @DisplayName("첫 페이지 검색은 검색어와 언어 코드만 본문에 담고 pageToken은 보내지 않는다")
    void searchSendsBodyWithoutPageToken() {
        server.expect(requestTo(BASE_URL + GooglePlacesClient.SEARCH_TEXT_PATH))
                .andExpect(content().json("{\"textQuery\": \"광장시장\", \"languageCode\": \"ko\"}"))
                .andExpect(jsonPath("$.pageToken").doesNotExist())
                .andRespond(withSuccess("{\"places\": []}", MediaType.APPLICATION_JSON));

        client.search("광장시장", null);

        server.verify();
    }

    @Test
    @DisplayName("다음 페이지 검색은 받은 pageToken을 본문에 담아 보낸다")
    void searchSendsPageToken() {
        server.expect(requestTo(BASE_URL + GooglePlacesClient.SEARCH_TEXT_PATH))
                .andExpect(jsonPath("$.textQuery").value("광장시장"))
                .andExpect(jsonPath("$.pageToken").value("next-token"))
                .andRespond(withSuccess("{\"places\": []}", MediaType.APPLICATION_JSON));

        client.search("광장시장", "next-token");

        server.verify();
    }

    @Test
    @DisplayName("pageToken을 보냈는데 Google이 400을 주면 INVALID_INPUT을 던진다")
    void searchThrowsInvalidInputWhenPageTokenRejected() {
        server.expect(requestTo(BASE_URL + GooglePlacesClient.SEARCH_TEXT_PATH))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> client.search("광장시장", "wrong-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("Google이 403을 주면 PLACES_API_ERROR를 던진다")
    void searchThrowsPlacesApiErrorWhenForbidden() {
        server.expect(requestTo(BASE_URL + GooglePlacesClient.SEARCH_TEXT_PATH))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.search("광장시장", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.PLACES_API_ERROR);
    }
}
