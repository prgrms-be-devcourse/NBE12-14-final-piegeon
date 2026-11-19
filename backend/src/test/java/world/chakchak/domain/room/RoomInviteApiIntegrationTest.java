package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

@IntegrationTest
@Import(RoomInviteApiIntegrationTest.TestClockConfiguration.class)
class RoomInviteApiIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MutableClock clock;
    @Autowired private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void startsPreviewRequestsInNewLimitWindow() {
        clock.advance(Duration.ofMinutes(1));
    }

    @Nested
    @TestClassOrder(ClassOrderer.OrderAnnotation.class)
    class Preview {

        @Nested
        @Order(1)
        class Success {

            @Test
            @DisplayName("초대 미리보기는 공개 정보와 사용자별 합류 가능 여부를 제공한다")
            void returnsPreviewForAnonymousAndLoggedInVisitors() throws java.lang.Exception {
                Room room = saveRoom("부산 연말 여행", RoomStatus.PLANNING);
                join(room, saveUser("서연"));
                HttpResponse<String> anonymousPreview = getPreview(room.getInviteCode(), null);

                assertThat(anonymousPreview.statusCode()).isEqualTo(200);
                JsonNode anonymousBody = objectMapper.readTree(anonymousPreview.body());
                assertThat(anonymousBody.path("success").booleanValue()).isTrue();
                assertThat(anonymousBody.path("code").stringValue())
                        .isEqualTo("INVITE_PREVIEW_FETCHED");
                assertThat(anonymousBody.has("message")).isFalse();

                JsonNode anonymousData = anonymousBody.path("data");
                assertThat(anonymousData.path("roomId").longValue()).isEqualTo(room.getId());
                assertThat(anonymousData.path("name").stringValue()).isEqualTo("부산 연말 여행");
                assertThat(anonymousData.path("destination").stringValue()).isEqualTo("부산");
                assertThat(anonymousData.path("startDate").stringValue())
                        .isEqualTo(room.getStartDate().toString());
                assertThat(anonymousData.path("endDate").stringValue())
                        .isEqualTo(room.getEndDate().toString());
                assertThat(anonymousData.path("status").stringValue()).isEqualTo("PLANNING");
                assertThat(anonymousData.path("memberCount").intValue()).isEqualTo(1);
                assertThat(anonymousData.path("joinable").booleanValue()).isFalse();
                assertThat(anonymousData.path("notJoinableReason").stringValue())
                        .isEqualTo("LOGIN_REQUIRED");
                assertThat(anonymousData.path("surveyRequired").booleanValue()).isTrue();
                assertThat(anonymousData.has("inviteCode")).isFalse();
                assertThat(anonymousData.has("members")).isFalse();

                Room historyRoom = saveRoom("초대할 여행", RoomStatus.SURVEYING);
                LoginSession activeSession = login("활동 멤버");
                join(historyRoom, userRepository.findByEmail(activeSession.email()).orElseThrow());

                LoginSession departedSession = login("탈퇴 멤버");
                RoomMember departedMember =
                        join(
                                historyRoom,
                                userRepository.findByEmail(departedSession.email()).orElseThrow());
                jdbcTemplate.update(
                        "update plan_room_member set left_at = ? where id = ?",
                        LocalDateTime.now(clock),
                        departedMember.getId());
                LoginSession newVisitorSession = login("새 방문자");

                HttpResponse<String> activePreview =
                        getPreview(historyRoom.getInviteCode(), activeSession.cookie());
                HttpResponse<String> departedPreview =
                        getPreview(historyRoom.getInviteCode(), departedSession.cookie());
                HttpResponse<String> newVisitorPreview =
                        getPreview(historyRoom.getInviteCode(), newVisitorSession.cookie());

                JsonNode activeData = objectMapper.readTree(activePreview.body()).path("data");
                JsonNode departedData = objectMapper.readTree(departedPreview.body()).path("data");
                JsonNode newVisitorData =
                        objectMapper.readTree(newVisitorPreview.body()).path("data");
                assertThat(activeData.path("joinable").booleanValue()).isFalse();
                assertThat(activeData.path("notJoinableReason").stringValue())
                        .isEqualTo("ALREADY_MEMBER");
                assertThat(departedData.path("joinable").booleanValue()).isFalse();
                assertThat(departedData.path("notJoinableReason").stringValue())
                        .isEqualTo("REJOIN_NOT_ALLOWED");
                assertThat(newVisitorData.path("joinable").booleanValue()).isTrue();
                assertThat(newVisitorData.path("notJoinableReason").isNull()).isTrue();
                assertThat(newVisitorData.path("memberCount").intValue()).isEqualTo(1);

                Room finalizedRoom = saveRoom("확정된 방", RoomStatus.FINALIZED);
                Room expiredRoom = saveRoom("만료된 방", RoomStatus.EXPIRED);
                HttpResponse<String> finalizedPreview =
                        getPreview(finalizedRoom.getInviteCode(), null);
                HttpResponse<String> expiredPreview = getPreview(expiredRoom.getInviteCode(), null);
                assertThat(
                                objectMapper
                                        .readTree(finalizedPreview.body())
                                        .path("data")
                                        .path("surveyRequired")
                                        .booleanValue())
                        .isFalse();
                assertThat(
                                objectMapper
                                        .readTree(expiredPreview.body())
                                        .path("data")
                                        .path("surveyRequired")
                                        .booleanValue())
                        .isTrue();
            }
        }

        @Nested
        @Order(2)
        class Exception {

            @Test
            @DisplayName("존재하지 않는 코드와 분당 호출 한도 초과를 구분한다")
            void rejectsUnknownAndOverLimitCodes() throws java.lang.Exception {
                HttpResponse<String> unknownCode = getPreview("MISSING1", null);
                assertThat(unknownCode.statusCode()).isEqualTo(404);
                assertThat(objectMapper.readTree(unknownCode.body()).path("code").stringValue())
                        .isEqualTo("INVITE_CODE_NOT_FOUND");

                Room validRoom = saveRoom("제한할 여행", RoomStatus.PLANNING);
                int acceptedRequests = 1;
                while (acceptedRequests < 20) {
                    boolean invalidCode = acceptedRequests % 2 == 1;
                    HttpResponse<String> response =
                            getPreview(invalidCode ? "NO-CODE1" : validRoom.getInviteCode(), null);
                    assertThat(response.statusCode()).isEqualTo(invalidCode ? 404 : 200);
                    acceptedRequests++;
                }

                HttpResponse<String> overLimit = getPreview("NO-CODE1", null);
                assertThat(overLimit.statusCode()).isEqualTo(429);
                assertThat(objectMapper.readTree(overLimit.body()).path("code").stringValue())
                        .isEqualTo("TOO_MANY_REQUESTS");
            }
        }
    }

    private LoginSession login(String name) throws java.lang.Exception {
        String email = uniqueEmail();
        post(
                "/api/auth/signup",
                "{\"email\":\""
                        + email
                        + "\",\"name\":\""
                        + name
                        + "\",\"password\":\""
                        + PASSWORD
                        + "\"}");
        HttpResponse<String> response =
                post(
                        "/api/auth/login",
                        "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
        String cookie =
                response.headers().allValues("Set-Cookie").stream()
                        .filter(value -> value.startsWith("SESSION="))
                        .map(value -> value.split(";", 2)[0])
                        .findFirst()
                        .orElseThrow();
        return new LoginSession(email, cookie);
    }

    private HttpResponse<String> post(String path, String body) throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getPreview(String inviteCode, String cookie)
            throws java.lang.Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri("/api/rooms/invites/" + inviteCode)).GET();
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private Room saveRoom(String name, RoomStatus status) {
        String inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        LocalDate startDate = LocalDate.now(clock).plusDays(10);
        return roomRepository.saveAndFlush(
                Room.create(
                        name, "부산", startDate, startDate.plusDays(2), inviteCode, status, clock));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private User saveUser(String name) {
        return userRepository.saveAndFlush(User.create(uniqueEmail(), name, "hashed-password"));
    }

    private RoomMember join(Room room, User user) {
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }

    private record LoginSession(String email, String cookie) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-11-19T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    static class MutableClock extends Clock {

        private final AtomicReference<Instant> currentInstant;
        private final ZoneId zone;

        MutableClock(Instant currentInstant, ZoneId zone) {
            this.currentInstant = new AtomicReference<>(currentInstant);
            this.zone = zone;
        }

        void advance(Duration duration) {
            currentInstant.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant(), zone);
        }

        @Override
        public Instant instant() {
            return currentInstant.get();
        }
    }
}
