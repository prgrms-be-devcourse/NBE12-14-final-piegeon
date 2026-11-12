package world.chakchak.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import world.chakchak.TestcontainersConfig;

/**
 * 실제 서버를 임의 포트로 띄우고 HTTP로 호출해서 인증 흐름 전체를 확인한다.
 * 필터, 세션 저장(MySQL), 쿠키, 에러 응답 형식이 함께 검증된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("가입하면 로그인 상태가 되고, 로그아웃하면 내 정보 조회가 401이 된다")
    void signupThenLogout() throws Exception {
        String email = uniqueEmail();

        HttpResponse<String> signup = postJson("/api/v1/auth/signup", signupBody(email), null);
        assertThat(signup.statusCode()).isEqualTo(201);
        assertThat(signup.body()).contains(email);

        String setCookie = signup.headers().firstValue("Set-Cookie").orElse("");
        assertThat(setCookie).contains("SESSION=").contains("HttpOnly").contains("SameSite=Lax");
        String sessionCookie = sessionCookieOf(signup);

        HttpResponse<String> me = get("/api/v1/users/me", sessionCookie);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(email);

        HttpResponse<String> logout = postJson("/api/v1/auth/logout", "", sessionCookie);
        assertThat(logout.statusCode()).isEqualTo(204);

        HttpResponse<String> meAfterLogout = get("/api/v1/users/me", sessionCookie);
        assertThat(meAfterLogout.statusCode()).isEqualTo(401);
        assertThat(meAfterLogout.body()).contains("AUTH_UNAUTHENTICATED");
    }

    @Test
    @DisplayName("가입한 이메일과 비밀번호로 로그인하면 내 정보를 조회할 수 있다")
    void login() throws Exception {
        String email = uniqueEmail();
        postJson("/api/v1/auth/signup", signupBody(email), null);

        HttpResponse<String> login = postJson("/api/v1/auth/login", loginBody(email, PASSWORD), null);
        assertThat(login.statusCode()).isEqualTo(200);

        HttpResponse<String> me = get("/api/v1/users/me", sessionCookieOf(login));
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(email);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401과 AUTH_INVALID_CREDENTIALS를 응답한다")
    void loginWithWrongPassword() throws Exception {
        String email = uniqueEmail();
        postJson("/api/v1/auth/signup", signupBody(email), null);

        HttpResponse<String> login = postJson("/api/v1/auth/login", loginBody(email, "wrong-password"), null);

        assertThat(login.statusCode()).isEqualTo(401);
        assertThat(login.body()).contains("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("로그인 없이 내 정보를 조회하면 401과 AUTH_UNAUTHENTICATED를 응답한다")
    void meWithoutLogin() throws Exception {
        HttpResponse<String> me = get("/api/v1/users/me", null);

        assertThat(me.statusCode()).isEqualTo(401);
        assertThat(me.body()).contains("AUTH_UNAUTHENTICATED");
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 400과 필드별 사유를 응답한다")
    void signupWithInvalidEmail() throws Exception {
        HttpResponse<String> signup = postJson("/api/v1/auth/signup", signupBody("not-an-email"), null);

        assertThat(signup.statusCode()).isEqualTo(400);
        assertThat(signup.body()).contains("COMMON_INVALID_INPUT").contains("\"field\":\"email\"");
    }

    @Test
    @DisplayName("이미 가입된 이메일로 가입하면 409와 USER_EMAIL_DUPLICATED를 응답한다")
    void signupWithDuplicatedEmail() throws Exception {
        String email = uniqueEmail();
        postJson("/api/v1/auth/signup", signupBody(email), null);

        HttpResponse<String> second = postJson("/api/v1/auth/signup", signupBody(email), null);

        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("USER_EMAIL_DUPLICATED");
    }

    private HttpResponse<String> postJson(String path, String body, String sessionCookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionCookie != null) {
            builder.header("Cookie", sessionCookie);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String sessionCookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        if (sessionCookie != null) {
            builder.header("Cookie", sessionCookie);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** 응답의 Set-Cookie에서 "SESSION=값" 부분만 꺼낸다. 브라우저가 다음 요청에 붙이는 형태와 같다. */
    private String sessionCookieOf(HttpResponse<String> response) {
        String setCookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.split(";", 2)[0];
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.com";
    }

    private String signupBody(String email) {
        return """
                {"email": "%s", "name": "테스터", "password": "%s"}
                """.formatted(email, PASSWORD);
    }

    private String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
