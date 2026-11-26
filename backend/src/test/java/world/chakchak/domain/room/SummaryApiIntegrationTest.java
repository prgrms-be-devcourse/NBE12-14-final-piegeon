package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

@IntegrationTest
class SummaryApiIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private ChangeLogRepository changeLogRepository;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
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
            @DisplayName("변경을 종류별로 요약하고 본인 변경과 조회 시각 갱신을 제외한다")
            void summarizesChangesWithoutAdvancingLastSeen() throws Exception {
                String email = uniqueEmail();
                String cookie = login(email);
                User currentUser = userRepository.findByEmail(email).orElseThrow();
                Room room = saveRoom();
                RoomMember current = join(room, currentUser);
                RoomMember other = join(room, saveUser());
                LocalDateTime since =
                        LocalDateTime.now(clock).minusHours(2).truncatedTo(ChronoUnit.SECONDS);
                jdbcTemplate.update(
                        "update plan_room_member set last_seen_at = ? where id = ?",
                        since,
                        current.getId());
                for (int index = 1; index <= 6; index++) {
                    saveChange(room, other, EventKind.PLACE_REGISTERED, "장소 " + index);
                }
                saveChange(room, current, EventKind.AGENDA_OPENED, "내 변경");
                saveChange(room, other, EventKind.AGENDA_RESOLVED, "숙소는 호텔로 결정됐어요");

                HttpResponse<String> response =
                        get("/api/rooms/" + room.getId() + "/summary", cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                JsonNode data = objectMapper.readTree(response.body()).path("data");
                assertThat(LocalDateTime.parse(data.path("since").asText())).isEqualTo(since);
                assertThat(data.path("totalChanges").asLong()).isEqualTo(7);
                JsonNode groups = data.path("groups");
                assertThat(groups.size()).isEqualTo(2);
                assertThat(groups.get(0).path("kind").asText()).isEqualTo("PLACE_REGISTERED");
                assertThat(groups.get(0).path("count").asLong()).isEqualTo(6);
                assertThat(groups.get(0).path("lines").size()).isEqualTo(5);
                assertThat(groups.get(1).path("kind").asText()).isEqualTo("AGENDA_RESOLVED");
                assertThat(data.path("needsMyAction").path("surveyNotSubmitted").asBoolean())
                        .isTrue();
                assertThat(data.path("needsMyAction").path("unvotedAgendas").isEmpty()).isTrue();
                assertThat(
                                roomMemberRepository
                                        .findById(current.getId())
                                        .orElseThrow()
                                        .getLastSeenAt())
                        .isEqualTo(since);
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("로그인하지 않으면 요약을 조회할 수 없다")
            void requiresLogin() throws Exception {
                HttpResponse<String> response = get("/api/rooms/1/summary", null);
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.body()).contains("UNAUTHORIZED");
            }

            @Test
            @DisplayName("숫자가 아닌 방 식별자는 INVALID_INPUT으로 응답한다")
            void rejectsNonNumericRoomId() throws Exception {
                HttpResponse<String> response =
                        get("/api/rooms/not-a-number/summary", login(uniqueEmail()));
                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("INVALID_INPUT");
            }

            @Test
            @DisplayName("없는 방은 ROOM_NOT_FOUND로 응답한다")
            void returnsRoomNotFound() throws Exception {
                HttpResponse<String> response =
                        get("/api/rooms/9223372036854775807/summary", login(uniqueEmail()));
                assertThat(response.statusCode()).isEqualTo(404);
                assertThat(response.body()).contains("ROOM_NOT_FOUND");
            }

            @Test
            @DisplayName("비멤버와 탈퇴 멤버는 FORBIDDEN으로 응답한다")
            void forbidsNonMembersAndDepartedMembers() throws Exception {
                String email = uniqueEmail();
                String cookie = login(email);
                Room room = saveRoom();
                User user = userRepository.findByEmail(email).orElseThrow();
                RoomMember departed = join(room, user);
                jdbcTemplate.update(
                        "update plan_room_member set left_at = ? where id = ?",
                        LocalDateTime.now(clock),
                        departed.getId());
                Room otherRoom = saveRoom();
                join(otherRoom, saveUser());

                for (Room target : List.of(room, otherRoom)) {
                    HttpResponse<String> response =
                            get("/api/rooms/" + target.getId() + "/summary", cookie);
                    assertThat(response.statusCode()).isEqualTo(403);
                    assertThat(response.body()).contains("FORBIDDEN");
                }
            }
        }
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String cookie) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String login(String email) throws Exception {
        String body =
                "{\"email\":\"" + email + "\",\"name\":\"테스터\",\"password\":\"" + PASSWORD + "\"}";
        post("/api/auth/signup", body, null);
        HttpResponse<String> response = post("/api/auth/login", body, null);
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    private Room saveRoom() {
        LocalDate start = LocalDate.now(clock).plusDays(10);
        return roomRepository.saveAndFlush(
                Room.create(
                        "요약 테스트",
                        "부산",
                        start,
                        start.plusDays(2),
                        UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                        RoomStatus.PLANNING,
                        clock));
    }

    private RoomMember join(Room room, User user) {
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }

    private User saveUser() {
        return userRepository.saveAndFlush(User.create(uniqueEmail(), "동행자", "hashed-password"));
    }

    private void saveChange(Room room, RoomMember actor, EventKind kind, String summary) {
        boolean placeRegistered = kind == EventKind.PLACE_REGISTERED;
        changeLogRepository.save(
                ChangeLog.create(
                        room,
                        actor,
                        placeRegistered ? EntityType.PLACE_CANDIDATE : EntityType.AGENDA,
                        // 지워진 장소의 등록 기록은 요약에서 빠지므로, 실제 후보를 저장하고 그 id로 남긴다
                        placeRegistered ? savePlace(room, actor, summary).getId() : 1L,
                        kind,
                        ActionStatus.CREATE,
                        summary));
    }

    private PlaceCandidate savePlace(Room room, RoomMember suggestedBy, String name) {
        return placeCandidateRepository.saveAndFlush(
                PlaceCandidate.create(
                        room,
                        "place-" + UUID.randomUUID(),
                        name,
                        "market",
                        "부산",
                        "https://maps.google.com/?cid=1",
                        null,
                        suggestedBy));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
