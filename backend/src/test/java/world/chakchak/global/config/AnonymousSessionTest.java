package world.chakchak.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import world.chakchak.IntegrationTest;

/**
 * 로그인 없이 보호된 API를 호출해 401을 받아도 세션이 만들어지지 않는지 확인한다.
 *
 * <p>Spring Security는 Accept가 application/json이면 원래 요청을 세션에 저장하지 않지만, Accept가 없거나 *&#47;*이면 저장한다.
 * (curl·봇, fetch 기본값, 주소창 직접 입력) 그래서 JSON이 아닌 경우를 골라 확인한다.
 */
@IntegrationTest
class AnonymousSessionTest {

    @LocalServerPort private int port;

    @Autowired private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();

    @ParameterizedTest(name = "Accept: {0}")
    @ValueSource(strings = {"", "*/*", "text/html,application/xhtml+xml,*/*;q=0.8"})
    @DisplayName("로그인 없이 보호된 API를 호출하면 401이고 세션 쿠키와 세션 행이 생기지 않는다")
    void anonymousRequestDoesNotCreateSession(String accept) throws Exception {
        int before = sessionCount();

        HttpResponse<String> response = get("/api/users/me", accept);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().allValues("Set-Cookie"))
                .noneMatch(value -> value.startsWith("SESSION="));
        assertThat(sessionCount()).isEqualTo(before);
    }

    private HttpResponse<String> get(String path, String accept) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (!accept.isEmpty()) {
            request.header("Accept", accept);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private int sessionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
    }
}
