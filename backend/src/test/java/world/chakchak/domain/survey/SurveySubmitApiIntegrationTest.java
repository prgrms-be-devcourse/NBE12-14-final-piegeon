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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import world.chakchak.IntegrationTest;

@IntegrationTest
class SurveySubmitApiIntegrationTest {

    private static final String PASSWORD = "password123!";
    private static final String SURVEY_BODY =
            "{\"budget\":10000,\"stayType\":\"HOTEL\",\"pace\":\"NORMAL\","
                    + "\"actPrefs\":[\"FOOD\",\"NATURE\"],\"avoidNote\":\"장거리 걷기\"}";

    @LocalServerPort private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("로그인하지 않은 사용자는 설문을 제출할 수 없다")
    void submitWithoutLogin() throws Exception {
        Fixture fixture = fixture();

        HttpResponse<String> response = post(surveyPath(fixture.roomId()), SURVEY_BODY, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHORIZED");
    }

    @Test
    @DisplayName("설문 제출을 저장하고 제출자 재요청은 ALREADY_SUBMITTED로 거절한다")
    void submitsSurveyAndRejectsResubmission() throws Exception {
        Fixture fixture = fixture();

        HttpResponse<String> submitted =
                post(surveyPath(fixture.roomId()), SURVEY_BODY, fixture.cookie());

        assertThat(submitted.statusCode()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(submitted.body());
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("code").asText()).isEqualTo("SURVEY_SUBMITTED");
        assertThat(body.path("data").path("surveyId").asLong()).isPositive();
        assertThat(body.path("data").path("submittedAt").asText()).isNotBlank();
        assertThat(body.path("data").path("surveyClosed").asBoolean()).isTrue();

        HttpResponse<String> resubmitted =
                post(surveyPath(fixture.roomId()), SURVEY_BODY, fixture.cookie());

        assertThat(resubmitted.statusCode()).isEqualTo(409);
        assertThat(resubmitted.body()).contains("ALREADY_SUBMITTED");
    }

    @Test
    @DisplayName("활동 멤버가 아니면 FORBIDDEN을 응답한다")
    void rejectsNonMember() throws Exception {
        Fixture fixture = fixture();
        String otherCookie = authenticatedCookie("other");

        HttpResponse<String> response =
                post(surveyPath(fixture.roomId()), SURVEY_BODY, otherCookie);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("FORBIDDEN");
    }

    @Test
    @DisplayName("잘못된 방과 잘못된 입력을 각각 명확한 오류로 응답한다")
    void rejectsMissingRoomAndInvalidPayload() throws Exception {
        Fixture fixture = fixture();

        HttpResponse<String> missingRoom =
                post(surveyPath(fixture.roomId() + 999_999), SURVEY_BODY, fixture.cookie());
        assertThat(missingRoom.statusCode()).isEqualTo(404);
        assertThat(missingRoom.body()).contains("ROOM_NOT_FOUND");

        HttpResponse<String> nonNumericRoomId =
                post("/api/rooms/not-a-number/survey", SURVEY_BODY, fixture.cookie());
        assertThat(nonNumericRoomId.statusCode()).isEqualTo(400);
        assertThat(nonNumericRoomId.body()).contains("INVALID_INPUT");

        HttpResponse<String> missingRequiredField =
                post(
                        surveyPath(fixture.roomId()),
                        "{\"budget\":10000,\"stayType\":\"HOTEL\","
                                + "\"actPrefs\":[\"FOOD\"],\"avoidNote\":\"메모\"}",
                        fixture.cookie());
        assertThat(missingRequiredField.statusCode()).isEqualTo(400);
        assertThat(missingRequiredField.body()).contains("INVALID_INPUT");

        HttpResponse<String> tooManyActivities =
                post(
                        surveyPath(fixture.roomId()),
                        "{\"budget\":10000,\"stayType\":\"HOTEL\",\"pace\":\"NORMAL\","
                                + "\"actPrefs\":[\"FOOD\",\"NATURE\",\"ACTIVITY\",\"CULTURE\"],"
                                + "\"avoidNote\":\"메모\"}",
                        fixture.cookie());
        assertThat(tooManyActivities.statusCode()).isEqualTo(400);
        assertThat(tooManyActivities.body()).contains("INVALID_ACTIVITY_COUNT");

        HttpResponse<String> invalidEnum =
                post(
                        surveyPath(fixture.roomId()),
                        "{\"budget\":10000,\"stayType\":\"CAMPING\",\"pace\":\"NORMAL\","
                                + "\"actPrefs\":[\"FOOD\"],\"avoidNote\":\"메모\"}",
                        fixture.cookie());
        assertThat(invalidEnum.statusCode()).isEqualTo(400);
        assertThat(invalidEnum.body()).contains("INVALID_INPUT");

        Fixture optionalNoteFixture = fixture();
        HttpResponse<String> missingAvoidNote =
                post(
                        surveyPath(optionalNoteFixture.roomId()),
                        "{\"budget\":10000,\"stayType\":\"HOTEL\",\"pace\":\"NORMAL\","
                                + "\"actPrefs\":[\"FOOD\"]}",
                        optionalNoteFixture.cookie());
        assertThat(missingAvoidNote.statusCode()).isEqualTo(400);
        assertThat(missingAvoidNote.body()).contains("INVALID_INPUT");
    }

    private Fixture fixture() throws Exception {
        String email = "survey-submit-" + UUID.randomUUID() + "@test.com";
        String cookie = authenticatedCookie(email);
        LocalDate startDate = LocalDate.now().plusDays(14);
        String roomBody =
                "{\"name\":\"설문 테스트\",\"destination\":\"서울\",\"startDate\":\""
                        + startDate
                        + "\",\"endDate\":\""
                        + startDate.plusDays(2)
                        + "\"}";
        HttpResponse<String> created = post("/api/rooms", roomBody, cookie);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode data = objectMapper.readTree(created.body()).path("data");
        long roomId = data.has("roomId") ? data.path("roomId").asLong() : data.path("id").asLong();
        assertThat(roomId).isPositive();
        return new Fixture(roomId, cookie);
    }

    private String authenticatedCookie(String suffix) throws Exception {
        String email =
                suffix.contains("@")
                        ? suffix
                        : "survey-submit-" + suffix + "-" + UUID.randomUUID() + "@test.com";
        String signupBody =
                "{\"email\":\"" + email + "\",\"name\":\"테스터\",\"password\":\"" + PASSWORD + "\"}";
        post("/api/auth/signup", signupBody, null);
        HttpResponse<String> login = post("/api/auth/login", signupBody, null);
        return login.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    private HttpResponse<String> post(String path, String body, String cookie) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String surveyPath(long roomId) {
        return "/api/rooms/" + roomId + "/survey";
    }

    private record Fixture(long roomId, String cookie) {}
}
