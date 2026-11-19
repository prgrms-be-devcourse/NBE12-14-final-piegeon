package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
class RoomReadApiIntegrationTest {

    private static final String PASSWORD = "password123!";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

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
            @DisplayName("로그인 사용자의 활동 중인 방을 집계해 출발일 순으로 반환한다")
            void returnsActiveRoomsInStableOrder() throws java.lang.Exception {
                String email = uniqueEmail();
                String cookie = login(email);
                User currentUser = userRepository.findByEmail(email).orElseThrow();
                LocalDate today = LocalDate.now(clock);

                Room pastFinalized =
                        saveRoom("지난 확정 여행", today.minusDays(10), RoomStatus.FINALIZED);
                RoomMember pastMember = join(pastFinalized, currentUser);
                submitSurvey(pastMember);

                Room tieFirst = saveRoom("같은 출발일 첫 여행", today.plusDays(5), RoomStatus.SURVEYING);
                RoomMember tieFirstMember = join(tieFirst, currentUser);
                join(tieFirst, saveUser("동행자"));
                LocalDateTime lastSeenBefore = tieFirstMember.getLastSeenAt();

                Room tieSecond = saveRoom("같은 출발일 두 번째 여행", today.plusDays(5), RoomStatus.PLANNING);
                RoomMember tieSecondMember = join(tieSecond, currentUser);
                submitSurvey(tieSecondMember);

                Room leftRoom = saveRoom("탈퇴한 여행", today.plusDays(1), RoomStatus.SURVEYING);
                RoomMember leftRoomMember = join(leftRoom, currentUser);
                jdbcTemplate.update(
                        "update plan_room_member set left_at = ? where id = ?",
                        LocalDateTime.now(clock),
                        leftRoomMember.getId());

                Room otherUsersRoom =
                        saveRoom("다른 사용자의 여행", today.minusDays(20), RoomStatus.SURVEYING);
                join(otherUsersRoom, saveUser("다른 사용자"));

                HttpResponse<String> response = get("/api/rooms", cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                JsonNode body = objectMapper.readTree(response.body());
                assertThat(body.path("success").booleanValue()).isTrue();
                assertThat(body.path("code").stringValue()).isEqualTo("ROOMS_FETCHED");
                assertThat(body.has("message")).isFalse();

                JsonNode rooms = body.path("data").path("rooms");
                List<Long> roomIds = new ArrayList<>();
                for (JsonNode room : rooms) {
                    roomIds.add(room.path("roomId").longValue());
                }
                assertThat(roomIds)
                        .containsExactly(
                                pastFinalized.getId(), tieFirst.getId(), tieSecond.getId());

                assertThat(rooms.get(0).path("status").stringValue()).isEqualTo("FINALIZED");
                assertThat(rooms.get(0).path("name").stringValue()).isEqualTo("지난 확정 여행");
                assertThat(rooms.get(0).path("destination").stringValue()).isEqualTo("부산");
                assertThat(rooms.get(0).path("startDate").stringValue())
                        .isEqualTo(today.minusDays(10).toString());
                assertThat(rooms.get(0).path("endDate").stringValue())
                        .isEqualTo(today.minusDays(8).toString());
                assertThat(rooms.get(0).path("memberCount").intValue()).isEqualTo(1);
                assertThat(rooms.get(0).path("mySurveySubmitted").booleanValue()).isTrue();
                assertThat(rooms.get(0).path("opinionDeadlineAt").isString()).isTrue();
                assertThat(rooms.get(1).path("memberCount").intValue()).isEqualTo(2);
                assertThat(rooms.get(1).path("mySurveySubmitted").booleanValue()).isFalse();
                assertThat(rooms.get(2).path("mySurveySubmitted").booleanValue()).isTrue();
                assertThat(rooms.get(2).path("opinionDeadlineAt").isString()).isTrue();

                RoomMember unchangedMember =
                        roomMemberRepository
                                .findByRoom_IdAndUser_Id(tieFirst.getId(), currentUser.getId())
                                .orElseThrow();
                assertThat(unchangedMember.getLastSeenAt()).isEqualTo(lastSeenBefore);
            }

            @Test
            @DisplayName("활동 중인 방이 없으면 빈 목록을 반환한다")
            void returnsEmptyRoomsWhenUserHasNoActiveMemberships() throws java.lang.Exception {
                String cookie = login(uniqueEmail());

                HttpResponse<String> response = get("/api/rooms", cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                JsonNode rooms = objectMapper.readTree(response.body()).path("data").path("rooms");
                assertThat(rooms.isArray()).isTrue();
                assertThat(rooms.isEmpty()).isTrue();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("로그인하지 않은 사용자는 내 방 목록을 조회할 수 없다")
            void requiresLogin() throws java.lang.Exception {
                HttpResponse<String> response = get("/api/rooms", null);

                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.body()).contains("UNAUTHORIZED");
            }
        }
    }

    private String login(String email) throws Exception {
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

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String cookie) throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private Room saveRoom(String name, LocalDate startDate, RoomStatus status) {
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

    private void submitSurvey(RoomMember roomMember) {
        surveyResponseRepository.saveAndFlush(
                SurveyResponse.createForPlanning(
                        roomMember, List.of(ActivityCode.CULTURE), "없음", clock));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
