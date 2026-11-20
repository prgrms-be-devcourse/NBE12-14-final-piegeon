package world.chakchak.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import world.chakchak.IntegrationTest;

@IntegrationTest
class SurveyOptionsApiIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("로그인한 사용자는 enum 선택지와 입력 한도를 조회한다")
    void fetchOptionsWhenLoggedIn() throws Exception {
        String email = "survey-options-" + UUID.randomUUID() + "@test.com";
        post("/api/auth/signup", signupBody(email), null);
        HttpResponse<String> login = post("/api/auth/login", loginBody(email), null);

        HttpResponse<String> response = get(sessionCookieOf(login));

        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).contains("\"success\":true", "\"code\":\"SURVEY_OPTIONS_FETCHED\"");
        assertThat(body)
                .contains(
                        "\"stayTypes\":[{\"value\":\"HOTEL\",\"label\":\"전문 숙박시설(호텔·리조트)\"},{\"value\":\"PERSONAL\",\"label\":\"독채형(에어비앤비·레지던스)\"},{\"value\":\"SOCIAL\",\"label\":\"교류형(호스텔·게스트하우스)\"},{\"value\":\"CUSTOM\",\"label\":\"특수(전통·이색 숙소)\"}]",
                        "\"paces\":[{\"value\":\"RELAXED\",\"label\":\"여유롭게(하루 1~3개)\"},{\"value\":\"NORMAL\",\"label\":\"적당히(하루 4~7개)\"},{\"value\":\"HURRY\",\"label\":\"바쁘게(하루 8~10개)\"}]",
                        "\"actPrefs\":[{\"value\":\"FOOD\",\"label\":\"맛집\"},{\"value\":\"NATURE\",\"label\":\"자연·힐링\"},{\"value\":\"ACTIVITY\",\"label\":\"액티비티·체험\"},{\"value\":\"SHOPPING\",\"label\":\"쇼핑·투어\"},{\"value\":\"CULTURE\",\"label\":\"명소·문화탐방\"}]",
                        "\"limits\":{\"budgetMin\":1,\"budgetMax\":99999999,\"actPrefsMin\":1,\"actPrefsMax\":3,\"avoidNoteMaxLength\":100}");
    }

    @Test
    @DisplayName("로그인하지 않으면 선택지 조회에 401과 UNAUTHORIZED를 응답한다")
    void fetchOptionsWithoutLogin() throws Exception {
        HttpResponse<String> response = get(null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHORIZED");
    }

    private HttpResponse<String> get(String cookie) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/survey-options"))
                        .GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
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

    private static String signupBody(String email) {
        return "{\"email\":\"" + email + "\",\"name\":\"테스터\",\"password\":\"" + PASSWORD + "\"}";
    }

    private static String loginBody(String email) {
        return signupBody(email);
    }

    private static String sessionCookieOf(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }
}
