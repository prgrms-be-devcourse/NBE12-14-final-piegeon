package world.chakchak.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import world.chakchak.IntegrationTest;

/** 호출 횟수 제한을 켜고 실제 HTTP로 한도를 넘겨 본다. 다른 통합 테스트는 제한을 꺼 둔다(application-test.yml). */
@IntegrationTest
@TestPropertySource(properties = "app.rate-limit.enabled=true")
class RateLimitIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RateLimiter rateLimiter;

    private final HttpClient client = HttpClient.newHttpClient();

    // 테스트끼리 같은 IP(127.0.0.1)라 횟수가 이어지지 않게 비운다
    @BeforeEach
    void resetCounts() {
        rateLimiter.clear();
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("방 생성은 사용자마다 따로 세서, 한 사람이 한도를 넘어도 다른 사람은 만들 수 있다")
            void countsRoomCreationPerUser() throws Exception {
                String alice = login();
                String bob = login();
                for (int i = 0; i < 10; i++) {
                    assertThat(createRoom(alice).statusCode()).isEqualTo(201);
                }

                assertThat(createRoom(alice).statusCode()).isEqualTo(429);
                assertThat(createRoom(bob).statusCode()).isEqualTo(201);
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("같은 IP에서 1분에 로그인을 60번 넘게 하면 429 TOO_MANY_REQUESTS와 Retry-After를 받는다")
            void rejectsLoginOverLimit() throws Exception {
                String email = signup();
                for (int i = 0; i < 60; i++) {
                    assertThat(loginAs(email).statusCode()).isEqualTo(200);
                }

                // 분당 60번은 1초마다 하나씩 다시 차서, 60번 보내는 사이 몇 개가 더 찼을 수 있다.
                // 그래서 61번째 한 번이 아니라, 몇 번 더 보내는 안에 막히는지 본다
                HttpResponse<String> response = loginAs(email);
                for (int i = 0; i < 30 && response.statusCode() == 200; i++) {
                    response = loginAs(email);
                }

                assertThat(response.statusCode()).isEqualTo(429);
                assertThat(response.body())
                        .contains("\"success\":false")
                        .contains("\"code\":\"TOO_MANY_REQUESTS\"");
                assertThat(response.headers().firstValue("Retry-After")).hasValue("1");
            }

            @Test
            @DisplayName("같은 IP에서 1분에 초대 미리보기를 21번 하면 429를 받는다")
            void rejectsInvitePreviewOverLimit() throws Exception {
                for (int i = 0; i < 20; i++) {
                    assertThat(invitePreview().statusCode()).isEqualTo(404);
                }

                assertThat(invitePreview().statusCode()).isEqualTo(429);
            }
        }
    }

    private String signup() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        post(
                "/api/auth/signup",
                "{\"email\":\"" + email + "\",\"name\":\"여행자\",\"password\":\"" + PASSWORD + "\"}",
                null);
        return email;
    }

    private HttpResponse<String> loginAs(String email) throws Exception {
        return post(
                "/api/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}",
                null);
    }

    // 가입하고 로그인해서 세션 쿠키를 돌려준다
    private String login() throws Exception {
        return loginAs(signup()).headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    private HttpResponse<String> createRoom(String cookie) throws Exception {
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        return post(
                "/api/rooms",
                "{\"name\":\"여행\",\"destination\":\"서울\",\"startDate\":\""
                        + start
                        + "\",\"endDate\":\""
                        + start.plusDays(2)
                        + "\"}",
                cookie);
    }

    // 없는 초대 코드라 404지만, 횟수는 센다
    private HttpResponse<String> invitePreview() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://localhost:" + port + "/api/rooms/invites/NOPE1234"))
                        .GET()
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String cookie) throws Exception {
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
