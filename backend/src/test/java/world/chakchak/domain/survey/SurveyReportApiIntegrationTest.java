package world.chakchak.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

@IntegrationTest
class SurveyReportApiIntegrationTest {

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

    private record LoggedInMember(String cookie, RoomMember member) {}

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName(
                    "설문이 마감되면 마감 시각까지 낸 설문의 실명 분포와 만장일치, 늦게 낸 설문까지 센 선호 행동과 피해야 할 것을 보여주고 나간 멤버는 뺀다")
            void reportsClosedSurvey() throws Exception {
                Room room = saveRoom();
                LoggedInMember minsu = joinAs(room, "민수");
                LoggedInMember jieun = joinAs(room, "지은");
                RoomMember departed = join(room, saveUser("나간멤버"));
                surveyResponseRepository.saveAndFlush(
                        SurveyResponse.createForSurveying(
                                departed,
                                5_000_000L,
                                StayType.SOCIAL,
                                Pace.HURRY,
                                List.of(ActivityCode.SHOPPING),
                                "나간 사람 메모",
                                clock));
                jdbcTemplate.update(
                        "update plan_room_member set left_at = ? where id = ?",
                        LocalDateTime.now(clock),
                        departed.getId());

                submit(
                        room,
                        minsu,
                        "{\"budget\":600000,\"stayType\":\"HOTEL\",\"pace\":\"RELAXED\","
                                + "\"actPrefs\":[\"FOOD\",\"NATURE\"],"
                                + "\"avoidNote\":\"해산물 알레르기가 있어요\"}");
                // 남은 두 사람이 모두 내서 설문이 마감된다. 예산이 갈려 방은 SURVEYING에 남아 늦은 제출을 받을 수 있다
                submit(
                        room,
                        jieun,
                        "{\"budget\":1000000,\"stayType\":\"HOTEL\",\"pace\":\"RELAXED\","
                                + "\"actPrefs\":[\"FOOD\"],\"avoidNote\":\"없음\"}");
                LoggedInMember late = joinAs(room, "늦은멤버");
                // 조율 중이라 늦게 내도 세 항목을 함께 낸다. 마감 뒤 답이라 아래 분포와 만장일치에는 들어가지 않는다
                submit(
                        room,
                        late,
                        "{\"budget\":2000000,\"stayType\":\"SOCIAL\",\"pace\":\"HURRY\","
                                + "\"actPrefs\":[\"CULTURE\"],\"avoidNote\":\"높은 곳은 싫어요\"}");

                HttpResponse<String> response = get(reportPath(room.getId()), minsu.cookie());

                assertThat(response.statusCode()).isEqualTo(200);
                JsonNode body = objectMapper.readTree(response.body());
                assertThat(body.path("code").stringValue()).isEqualTo("SURVEY_REPORT_FETCHED");
                JsonNode data = body.path("data");
                LocalDateTime closedAt =
                        roomRepository.findById(room.getId()).orElseThrow().getSurveyClosedAt();
                assertThat(LocalDateTime.parse(data.path("closedAt").stringValue()))
                        .isEqualTo(closedAt);
                assertThat(data.path("respondentCount").longValue()).isEqualTo(2);

                JsonNode items = data.path("items");
                assertThat(items.size()).isEqualTo(3);
                assertItem(
                        items.get(0),
                        AgendaSubject.BUDGET,
                        false,
                        answer(minsu.member(), "민수", "600000"),
                        answer(jieun.member(), "지은", "1000000"));
                assertItem(
                        items.get(1),
                        AgendaSubject.STAY,
                        true,
                        answer(minsu.member(), "민수", "HOTEL"),
                        answer(jieun.member(), "지은", "HOTEL"));
                assertItem(
                        items.get(2),
                        AgendaSubject.PACE,
                        true,
                        answer(minsu.member(), "민수", "RELAXED"),
                        answer(jieun.member(), "지은", "RELAXED"));

                assertThat(lines(data.path("actPrefs"), "value", "label", "count"))
                        .containsExactly("FOOD|맛집|2", "NATURE|자연·힐링|1", "CULTURE|명소·문화탐방|1");
                assertThat(lines(data.path("avoidNotes"), "memberId", "name", "value"))
                        .containsExactly(
                                answer(minsu.member(), "민수", "해산물 알레르기가 있어요"),
                                answer(jieun.member(), "지은", "없음"),
                                answer(late.member(), "늦은멤버", "높은 곳은 싫어요"));
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("로그인하지 않은 사용자는 리포트 조회에 401을 받는다")
            void requiresLogin() throws Exception {
                HttpResponse<String> response = get(reportPath(1), null);

                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.body()).contains("UNAUTHORIZED");
            }

            @Test
            @DisplayName("숫자가 아닌 방 ID는 INVALID_INPUT으로 거절한다")
            void rejectsNonNumericRoomId() throws Exception {
                String cookie = login(uniqueEmail(), "여행자");

                HttpResponse<String> response =
                        get("/api/rooms/not-a-number/survey/report", cookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("INVALID_INPUT");
            }

            @Test
            @DisplayName("존재하지 않는 방은 ROOM_NOT_FOUND로 응답한다")
            void returnsRoomNotFound() throws Exception {
                String cookie = login(uniqueEmail(), "여행자");

                HttpResponse<String> response = get(reportPath(Long.MAX_VALUE), cookie);

                assertThat(response.statusCode()).isEqualTo(404);
                assertThat(response.body()).contains("ROOM_NOT_FOUND");
            }

            @Test
            @DisplayName("방 멤버가 아닌 사용자는 FORBIDDEN으로 응답한다")
            void forbidsNonMember() throws Exception {
                String cookie = login(uniqueEmail(), "여행자");
                Room room = saveRoom();
                join(room, saveUser("방 주인"));

                HttpResponse<String> response = get(reportPath(room.getId()), cookie);

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(response.body()).contains("FORBIDDEN");
            }

            @Test
            @DisplayName("설문이 아직 마감되지 않았으면 SURVEY_NOT_CLOSED로 응답한다")
            void rejectsBeforeSurveyCloses() throws Exception {
                Room room = saveRoom();
                LoggedInMember minsu = joinAs(room, "민수");

                HttpResponse<String> response = get(reportPath(room.getId()), minsu.cookie());

                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(response.body()).contains("SURVEY_NOT_CLOSED");
            }
        }
    }

    private static void assertItem(
            JsonNode item, AgendaSubject subject, boolean unanimous, String... distribution) {
        assertThat(item.path("subject").stringValue()).isEqualTo(subject.name());
        assertThat(item.path("unanimous").booleanValue()).isEqualTo(unanimous);
        assertThat(lines(item.path("distribution"), "memberId", "name", "value"))
                .containsExactly(distribution);
    }

    private static String answer(RoomMember member, String name, String value) {
        return member.getId() + "|" + name + "|" + value;
    }

    // 배열의 각 원소를 "필드1|필드2|필드3" 한 줄로 바꿔 순서까지 비교한다
    private static List<String> lines(JsonNode array, String... fields) {
        List<String> lines = new ArrayList<>();
        array.forEach(element -> {
            List<String> values = new ArrayList<>();
            for (String field : fields) {
                values.add(element.path(field).asString());
            }
            lines.add(String.join("|", values));
        });
        return lines;
    }

    private void submit(Room room, LoggedInMember member, String body) throws Exception {
        HttpResponse<String> response =
                post("/api/rooms/" + room.getId() + "/survey", body, member.cookie());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    }

    private LoggedInMember joinAs(Room room, String name) throws Exception {
        String email = uniqueEmail();
        String cookie = login(email, name);
        User user = userRepository.findByEmail(email).orElseThrow();
        return new LoggedInMember(cookie, join(room, user));
    }

    private String login(String email, String name) throws Exception {
        String body =
                "{\"email\":\""
                        + email
                        + "\",\"name\":\""
                        + name
                        + "\",\"password\":\""
                        + PASSWORD
                        + "\"}";
        post("/api/auth/signup", body, null);
        HttpResponse<String> login = post("/api/auth/login", body, null);
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

    private Room saveRoom() {
        LocalDate startDate = LocalDate.now(clock).plusDays(10);
        String inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return roomRepository.saveAndFlush(
                Room.create(
                        "설문 리포트",
                        "부산",
                        startDate,
                        startDate.plusDays(2),
                        inviteCode,
                        RoomStatus.SURVEYING,
                        clock));
    }

    private User saveUser(String name) {
        return userRepository.saveAndFlush(User.create(uniqueEmail(), name, "hashed-password"));
    }

    private RoomMember join(Room room, User user) {
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private static String reportPath(long roomId) {
        return "/api/rooms/" + roomId + "/survey/report";
    }
}
