package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.place.dto.GooglePlace;
import world.chakchak.domain.place.dto.PlaceSearchResult;
import world.chakchak.domain.place.service.PlaceSearchClient;

/** P-01 장소 검색 API를 HTTP로 호출한다. Google은 PlaceSearchClient를 가짜로 바꿔 부르지 않는다. */
@IntegrationTest
class PlaceSearchApiIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private PlaceSearchClient placeSearchClient;

    private final HttpClient client = HttpClient.newHttpClient();

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("방 멤버가 검색하면 200과 PLACES_SEARCHED, 장소 목록과 다음 페이지 토큰이 반환된다")
            void searchesPlaces() throws java.lang.Exception {
                String cookie = login();
                long roomId = createPlanningRoom(cookie);
                given(placeSearchClient.search("서울 시장", null))
                        .willReturn(
                                new PlaceSearchResult(
                                        List.of(
                                                new GooglePlace(
                                                        "place-1", "광장시장", "서울 종로구", "market", null,
                                                        null)),
                                        "next-token"));

                HttpResponse<String> response = search(roomId, "시장", cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .contains("\"success\":true")
                        .contains("\"code\":\"PLACES_SEARCHED\"")
                        .contains("\"googlePlaceId\":\"place-1\"")
                        .contains("\"name\":\"광장시장\"")
                        .contains("\"alreadyRegistered\":false")
                        .contains("\"nextPageToken\":\"next-token\"");
            }
        }

        @Nested
        class Exception {

            @ParameterizedTest
            @ValueSource(ints = {1, 51})
            @DisplayName("검색어가 2~50자가 아니면 400 INVALID_INPUT이고 Google을 부르지 않는다")
            void rejectsInvalidQueryLength(int length) throws java.lang.Exception {
                String cookie = login();
                long roomId = createPlanningRoom(cookie);

                HttpResponse<String> response = search(roomId, "가".repeat(length), cookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("\"code\":\"INVALID_INPUT\"");
                verify(placeSearchClient, never()).search(anyString(), any());
            }

            @Test
            @DisplayName("방 멤버가 아니면 403 FORBIDDEN이고 Google을 부르지 않는다")
            void rejectsNonMember() throws java.lang.Exception {
                long roomId = createPlanningRoom(login());

                HttpResponse<String> response = search(roomId, "시장", login());

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"");
                verify(placeSearchClient, never()).search(anyString(), any());
            }
        }
    }

    private HttpResponse<String> search(long roomId, String query, String cookie)
            throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://localhost:"
                                                + port
                                                + "/api/rooms/"
                                                + roomId
                                                + "/places/search?query="
                                                + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                        .header("Cookie", cookie)
                        .GET()
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // 설문 단계 API가 아직 없어서, 만든 방(SURVEYING)을 DB에서 바로 PLANNING으로 넘긴다
    private long createPlanningRoom(String cookie) throws java.lang.Exception {
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        HttpResponse<String> response =
                post(
                        "/api/rooms",
                        "{\"name\":\"여행\",\"destination\":\"서울\",\"startDate\":\""
                                + start
                                + "\",\"endDate\":\""
                                + start.plusDays(2)
                                + "\"}",
                        cookie);
        Matcher matcher = Pattern.compile("\"roomId\":(\\d+)").matcher(response.body());
        assertThat(matcher.find()).isTrue();
        long roomId = Long.parseLong(matcher.group(1));
        jdbcTemplate.update("UPDATE plan_room SET status = 'PLANNING' WHERE id = ?", roomId);
        return roomId;
    }

    private String login() throws java.lang.Exception {
        String email = UUID.randomUUID() + "@example.com";
        post(
                "/api/auth/signup",
                "{\"email\":\"" + email + "\",\"name\":\"여행자\",\"password\":\"" + PASSWORD + "\"}",
                null);
        HttpResponse<String> login =
                post(
                        "/api/auth/login",
                        "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}",
                        null);
        return login.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    private HttpResponse<String> post(String path, String body, String cookie)
            throws java.lang.Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
