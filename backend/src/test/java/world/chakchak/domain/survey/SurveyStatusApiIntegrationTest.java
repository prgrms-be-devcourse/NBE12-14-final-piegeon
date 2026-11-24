package world.chakchak.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

@IntegrationTest
class SurveyStatusApiIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private SurveyResponseRepository surveyResponseRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;
    @Autowired private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("마감 전후 제출 여부와 제출자의 실제 제출 시각을 조회한다")
            void returnsStatusBeforeAndAfterSurveyCloses() throws Exception {
                String email = uniqueEmail();
                String cookie = login(email);
                User currentUser = userRepository.findByEmail(email).orElseThrow();
                Room room = saveRoom("설문 제출 현황", RoomStatus.SURVEYING);
                RoomMember currentMember = join(room, currentUser);
                RoomMember submittedMember = join(room, saveUser("제출자"));
                RoomMember unsubmittedMember = join(room, saveUser("미제출자"));
                submitSurvey(submittedMember);

                LocalDateTime submittedAt =
                        surveyResponseRepository
                                .findByRoomMember_Id(submittedMember.getId())
                                .orElseThrow()
                                .getSubmittedAt();

                HttpResponse<String> beforeClose = get(statusPath(room.getId()), cookie);

                assertThat(beforeClose.statusCode()).isEqualTo(200);
                JsonNode beforeBody = objectMapper.readTree(beforeClose.body());
                assertThat(beforeBody.path("success").booleanValue()).isTrue();
                assertThat(beforeBody.path("code").stringValue())
                        .isEqualTo("SURVEY_STATUS_FETCHED");
                JsonNode beforeData = beforeBody.path("data");
                assertThat(beforeData.path("closed").booleanValue()).isFalse();
                assertThat(beforeData.path("opinionDeadlineAt").isString()).isTrue();
                assertThat(beforeData.path("activeMemberCount").longValue()).isEqualTo(3);
                assertThat(beforeData.path("submittedCount").longValue()).isEqualTo(1);
                assertMemberStatus(beforeData, currentMember, false, null);
                assertMemberStatus(beforeData, submittedMember, true, submittedAt);
                assertMemberStatus(beforeData, unsubmittedMember, false, null);

                LocalDateTime closedAt = LocalDateTime.now(clock);
                jdbcTemplate.update(
                        "update plan_room set survey_closed_at = ?, status = ? where id = ?",
                        closedAt,
                        RoomStatus.PLANNING.name(),
                        room.getId());

                HttpResponse<String> afterClose = get(statusPath(room.getId()), cookie);

                assertThat(afterClose.statusCode()).isEqualTo(200);
                JsonNode afterBody = objectMapper.readTree(afterClose.body());
                assertThat(afterBody.path("success").booleanValue()).isTrue();
                assertThat(afterBody.path("code").stringValue()).isEqualTo("SURVEY_STATUS_FETCHED");
                JsonNode afterData = afterBody.path("data");
                assertThat(afterData.path("closed").booleanValue()).isTrue();
                assertThat(afterData.path("activeMemberCount").longValue()).isEqualTo(3);
                assertThat(afterData.path("submittedCount").longValue()).isEqualTo(1);
                assertMemberStatus(afterData, currentMember, false, null);
                assertMemberStatus(afterData, submittedMember, true, submittedAt);
                assertMemberStatus(afterData, unsubmittedMember, false, null);
            }

            @Test
            @DisplayName("방에서 나간 멤버는 제출 현황에서 제외되고 현황을 조회할 수 없다")
            void excludesDepartedMemberAndForbidsTheirRequest() throws Exception {
                String activeEmail = uniqueEmail();
                String activeCookie = login(activeEmail);
                User activeUser = userRepository.findByEmail(activeEmail).orElseThrow();
                String departedEmail = uniqueEmail();
                String departedCookie = login(departedEmail);
                User departedUser = userRepository.findByEmail(departedEmail).orElseThrow();
                Room room = saveRoom("퇴장 멤버 제외", RoomStatus.SURVEYING);
                RoomMember activeMember = join(room, activeUser);
                RoomMember departedMember = join(room, departedUser);
                submitSurvey(departedMember);
                jdbcTemplate.update(
                        "update plan_room_member set left_at = ? where id = ?",
                        LocalDateTime.now(clock),
                        departedMember.getId());

                HttpResponse<String> activeResponse = get(statusPath(room.getId()), activeCookie);

                assertThat(activeResponse.statusCode()).isEqualTo(200);
                JsonNode data = objectMapper.readTree(activeResponse.body()).path("data");
                assertThat(data.path("activeMemberCount").longValue()).isEqualTo(1);
                assertThat(data.path("submittedCount").longValue()).isZero();
                assertThat(data.path("members").size()).isEqualTo(1);
                assertThat(data.path("members").get(0).path("memberId").longValue())
                        .isEqualTo(activeMember.getId());

                HttpResponse<String> departedResponse =
                        get(statusPath(room.getId()), departedCookie);

                assertThat(departedResponse.statusCode()).isEqualTo(403);
                assertThat(departedResponse.body()).contains("FORBIDDEN");
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("로그인하지 않은 사용자는 제출 현황 조회에 401을 받는다")
            void requiresLogin() throws Exception {
                HttpResponse<String> response = get(statusPath(1), null);

                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.body()).contains("UNAUTHORIZED");
            }

            @Test
            @DisplayName("숫자가 아닌 방 ID는 INVALID_INPUT으로 거절한다")
            void rejectsNonNumericRoomId() throws Exception {
                String cookie = login(uniqueEmail());

                HttpResponse<String> response =
                        get("/api/rooms/not-a-number/surveys/status", cookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("INVALID_INPUT");
            }

            @Test
            @DisplayName("존재하지 않는 방은 ROOM_NOT_FOUND로 응답한다")
            void returnsRoomNotFound() throws Exception {
                String cookie = login(uniqueEmail());

                HttpResponse<String> response =
                        get("/api/rooms/9223372036854775807/surveys/status", cookie);

                assertThat(response.statusCode()).isEqualTo(404);
                assertThat(response.body()).contains("ROOM_NOT_FOUND");
            }

            @Test
            @DisplayName("방 멤버가 아닌 사용자는 FORBIDDEN으로 응답한다")
            void forbidsNonMember() throws Exception {
                String cookie = login(uniqueEmail());
                Room room = saveRoom("다른 사용자의 방", RoomStatus.SURVEYING);
                join(room, saveUser("방 주인"));

                HttpResponse<String> response = get(statusPath(room.getId()), cookie);

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(response.body()).contains("FORBIDDEN");
            }
        }
    }

    private void assertMemberStatus(
            JsonNode data, RoomMember member, boolean submitted, LocalDateTime submittedAt) {
        JsonNode memberStatus =
                streamMembers(data).stream()
                        .filter(candidate ->
                                candidate.path("memberId").longValue() == member.getId())
                        .findFirst()
                        .orElseThrow();
        assertThat(memberStatus.path("submitted").booleanValue()).isEqualTo(submitted);
        if (submittedAt == null) {
            assertThat(memberStatus.path("submittedAt").isNull()).isTrue();
        } else {
            // JSON은 소수점 끝의 0을 지우고(.12345) toString()은 남겨서(.123450) 문자열로 비교하면 가끔 다르다
            assertThat(LocalDateTime.parse(memberStatus.path("submittedAt").stringValue()))
                    .isEqualTo(submittedAt);
        }
    }

    private List<JsonNode> streamMembers(JsonNode data) {
        java.util.ArrayList<JsonNode> members = new java.util.ArrayList<>();
        data.path("members").forEach(members::add);
        return members;
    }

    private String login(String email) throws Exception {
        String signupBody =
                "{\"email\":\"" + email + "\",\"name\":\"여행자\",\"password\":\"" + PASSWORD + "\"}";
        post("/api/auth/signup", signupBody, null);
        HttpResponse<String> login = post("/api/auth/login", signupBody, null);
        return login.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String cookie) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private Room saveRoom(String name, RoomStatus status) {
        LocalDate startDate = LocalDate.now(clock).plusDays(10);
        String inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return roomRepository.saveAndFlush(
                Room.create(
                        name, "부산", startDate, startDate.plusDays(2), inviteCode, status, clock));
    }

    private User saveUser(String name) {
        return userRepository.saveAndFlush(User.create(uniqueEmail(), name, "hashed-password"));
    }

    private RoomMember join(Room room, User user) {
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }

    private void submitSurvey(RoomMember member) {
        surveyResponseRepository.saveAndFlush(
                SurveyResponse.createForPlanning(
                        member, List.of(ActivityCode.CULTURE), "없음", clock));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private static String statusPath(long roomId) {
        return "/api/rooms/" + roomId + "/surveys/status";
    }
}
