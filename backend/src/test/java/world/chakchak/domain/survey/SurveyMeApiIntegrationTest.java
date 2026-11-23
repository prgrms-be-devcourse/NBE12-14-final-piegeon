package world.chakchak.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import world.chakchak.IntegrationTest;

@IntegrationTest
class SurveyMeApiIntegrationTest {

    private static final String PASSWORD = "password123!";
    private static final String SURVEY_BODY =
            "{\"budget\":10000,\"stayType\":\"HOTEL\",\"pace\":\"NORMAL\","
                    + "\"actPrefs\":[\"FOOD\",\"NATURE\"],\"avoidNote\":\"장거리 걷기\"}";

    @LocalServerPort private int port;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void returnsMySurveyWithEnumLabels() throws Exception {
        Fixture fixture = fixture();
        post(path(fixture.roomId()), SURVEY_BODY, fixture.cookie());

        HttpResponse<String> response = get(path(fixture.roomId()) + "/me", fixture.cookie());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("code").asText()).isEqualTo("SURVEY_FETCHED");
        JsonNode data = body.path("data");
        assertThat(data.path("surveyId").asLong()).isPositive();
        assertThat(data.path("budget").asLong()).isEqualTo(10000);
        assertThat(data.path("stayType").path("value").asText()).isEqualTo("HOTEL");
        assertThat(data.path("stayType").path("label").asText()).isEqualTo("전문 숙박시설(호텔·리조트)");
        assertThat(data.path("pace").path("label").asText()).isEqualTo("적당히(하루 4~7개)");
        assertThat(data.path("actPrefs").get(0).path("value").asText()).isEqualTo("FOOD");
        assertThat(data.path("actPrefs").get(0).path("label").asText()).isEqualTo("맛집");
        assertThat(data.path("avoidNote").asText()).isEqualTo("장거리 걷기");
        assertThat(data.path("submittedAt").asText()).isNotBlank();
    }

    @Test
    void returnsDefinedAccessErrors() throws Exception {
        Fixture fixture = fixture();

        assertError(get(path(fixture.roomId()) + "/me", null), 401, "UNAUTHORIZED");
        assertError(
                get("/api/rooms/not-a-number/survey/me", fixture.cookie()), 400, "INVALID_INPUT");
        assertError(
                get(path(fixture.roomId() + 999999) + "/me", fixture.cookie()),
                404,
                "ROOM_NOT_FOUND");
        assertError(
                get(path(fixture.roomId()) + "/me", authenticatedCookie("other")),
                403,
                "FORBIDDEN");
        assertError(get(path(fixture.roomId()) + "/me", fixture.cookie()), 404, "SURVEY_NOT_FOUND");
    }

    private Fixture fixture() throws Exception {
        String cookie = authenticatedCookie("owner");
        LocalDate start = LocalDate.now().plusDays(14);
        String body =
                "{\"name\":\"설문 조회 테스트\",\"destination\":\"서울\",\"startDate\":\""
                        + start
                        + "\",\"endDate\":\""
                        + start.plusDays(2)
                        + "\"}";
        HttpResponse<String> created = post("/api/rooms", body, cookie);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode data = objectMapper.readTree(created.body()).path("data");
        long roomId = data.has("roomId") ? data.path("roomId").asLong() : data.path("id").asLong();
        return new Fixture(roomId, cookie);
    }

    private String authenticatedCookie(String suffix) throws Exception {
        String email = "survey-me-" + suffix + "-" + UUID.randomUUID() + "@test.com";
        String body =
                "{\"email\":\"" + email + "\",\"name\":\"테스터\",\"password\":\"" + PASSWORD + "\"}";
        post("/api/auth/signup", body, null);
        return post("/api/auth/login", body, null).headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String cookie) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.body()).contains(code);
    }

    private static String path(long roomId) {
        return "/api/rooms/" + roomId + "/survey";
    }

    private record Fixture(long roomId, String cookie) {}
}
