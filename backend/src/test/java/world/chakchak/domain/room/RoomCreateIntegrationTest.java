package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.repository.UserRepository;

@IntegrationTest
class RoomCreateIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("로그인한 사용자가 여행방을 만들면 생성 응답과 초대 코드가 반환된다")
            void createsRoom() throws java.lang.Exception {
                String cookie = login(uniqueEmail());
                LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
                String body = requestBody("  여름 여행  ", "  부산  ", start, start.plusDays(30));

                HttpResponse<String> response = post("/api/rooms", body, cookie);

                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.body())
                        .contains("\"success\":true")
                        .contains("\"code\":\"ROOM_CREATED\"")
                        .contains("여행방을 만들었습니다.")
                        .contains("\"name\":\"여름 여행\"")
                        .contains("\"destination\":\"부산\"")
                        .contains("\"status\":\"SURVEYING\"")
                        .contains("\"surveyClosedAt\":null")
                        .contains("\"finalizedAt\":null")
                        .contains("\"readOnly\":false")
                        .contains("\"surveySubmitted\":false");
                assertThat(response.body()).containsPattern("\"inviteCode\":\"[a-f0-9]{8}\"");

                Room room = roomFromResponse(response.body());
                assertThat(room.getName()).isEqualTo("여름 여행");
                assertThat(room.getDestination()).isEqualTo("부산");
                assertThat(
                                Duration.between(
                                                room.getCreatedAt().plusDays(3),
                                                room.getOpinionDeadlineAt())
                                        .abs()
                                        .getSeconds())
                        .isLessThanOrEqualTo(1);

                List<RoomMember> members =
                        roomMemberRepository.findAllByRoom_IdAndLeftAtIsNull(room.getId());
                assertThat(members).hasSize(1);
                assertThat(members.getFirst().getJoinedAt())
                        .isEqualTo(members.getFirst().getLastSeenAt());

                List<Integer> dayIndexes =
                        jdbcTemplate.queryForList(
                                "select day_index from schedule_day where plan_room_id = ? order by day_index",
                                Integer.class,
                                room.getId());
                assertThat(dayIndexes)
                        .containsExactlyElementsOf(
                                java.util.stream.IntStream.rangeClosed(1, 31).boxed().toList());
                List<Long> versions =
                        jdbcTemplate.queryForList(
                                "select version from schedule_day where plan_room_id = ?",
                                Long.class,
                                room.getId());
                assertThat(versions).hasSize(31).containsOnly(0L);
            }

            @Test
            @DisplayName("같은 날짜의 여행방을 만들 수 있다")
            void createsSameDayTrip() throws java.lang.Exception {
                String cookie = login(uniqueEmail());
                LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);

                HttpResponse<String> response =
                        post("/api/rooms", requestBody("당일 여행", "부산", start, start), cookie);

                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.body()).contains("\"code\":\"ROOM_CREATED\"");
                Room room = roomFromResponse(response.body());
                assertThat(
                                jdbcTemplate.queryForList(
                                        "select day_index from schedule_day where plan_room_id = ?",
                                        Integer.class,
                                        room.getId()))
                        .containsExactly(1);
                assertThat(
                                jdbcTemplate.queryForList(
                                        "select version from schedule_day where plan_room_id = ?",
                                        Long.class,
                                        room.getId()))
                        .containsExactly(0L);
            }

            @Test
            @DisplayName("이름과 목적지가 각각 50자이면 여행방을 만들 수 있다")
            void acceptsMaximumLengthNameAndDestination() throws java.lang.Exception {
                String cookie = login(uniqueEmail());
                LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
                String name = "가".repeat(50);
                String destination = "나".repeat(50);

                HttpResponse<String> response =
                        post("/api/rooms", requestBody(name, destination, start, start), cookie);

                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.body()).contains(name).contains(destination);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("로그인하지 않은 사용자는 여행방을 만들 수 없다")
            void requiresLogin() throws java.lang.Exception {
                LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);

                HttpResponse<String> response =
                        post("/api/rooms", requestBody("여행", "서울", start, start), null);

                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.body()).contains("UNAUTHORIZED");
            }

            @Test
            @DisplayName("출발일 자정이 24시간 이내이면 DEPARTURE_TOO_SOON을 응답한다")
            void rejectsDepartureTooSoon() throws java.lang.Exception {
                String cookie = login(uniqueEmail());
                LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);

                HttpResponse<String> response =
                        post("/api/rooms", requestBody("여행", "서울", start, start), cookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("DEPARTURE_TOO_SOON");
            }

            @Test
            @DisplayName("출발일이 오늘부터 1년 이상 뒤면 DEPARTURE_TOO_FAR를 응답한다")
            void rejectsDepartureTooFar() throws java.lang.Exception {
                String cookie = login(uniqueEmail());
                LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusYears(1);

                HttpResponse<String> response =
                        post("/api/rooms", requestBody("여행", "서울", start, start), cookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("DEPARTURE_TOO_FAR");
            }

            @Test
            @DisplayName("여행 기간이 31일을 초과하면 INVALID_DATE_RANGE를 응답한다")
            void rejectsTripLongerThan31Days() throws java.lang.Exception {
                String cookie = login(uniqueEmail());
                LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);

                HttpResponse<String> response =
                        post(
                                "/api/rooms",
                                requestBody("여행", "서울", start, start.plusDays(31)),
                                cookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("INVALID_DATE_RANGE");
            }

            @Test
            @DisplayName("종료일이 시작일보다 빠르면 INVALID_DATE_RANGE를 응답한다")
            void rejectsEndDateBeforeStartDate() throws java.lang.Exception {
                String cookie = login(uniqueEmail());
                LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);

                HttpResponse<String> response =
                        post(
                                "/api/rooms",
                                requestBody("여행", "서울", start, start.minusDays(1)),
                                cookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("INVALID_DATE_RANGE");
            }

            @Test
            @DisplayName("이름과 목적지가 비어 있거나 50자를 넘으면 생성하지 않는다")
            void rejectsInvalidNameAndDestination() throws java.lang.Exception {
                String cookie = login(uniqueEmail());
                LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
                long roomCountBefore = roomRepository.count();

                HttpResponse<String> emptyName =
                        post("/api/rooms", requestBody("  ", "서울", start, start), cookie);
                HttpResponse<String> longName =
                        post("/api/rooms", requestBody("가".repeat(51), "서울", start, start), cookie);
                HttpResponse<String> emptyDestination =
                        post("/api/rooms", requestBody("여행", "  ", start, start), cookie);
                HttpResponse<String> longDestination =
                        post("/api/rooms", requestBody("여행", "가".repeat(51), start, start), cookie);

                assertThat(emptyName.statusCode()).isEqualTo(400);
                assertThat(emptyName.body()).contains("INVALID_INPUT").contains("name");
                assertThat(longName.statusCode()).isEqualTo(400);
                assertThat(longName.body()).contains("INVALID_INPUT").contains("name");
                assertThat(emptyDestination.statusCode()).isEqualTo(400);
                assertThat(emptyDestination.body())
                        .contains("INVALID_INPUT")
                        .contains("destination");
                assertThat(longDestination.statusCode()).isEqualTo(400);
                assertThat(longDestination.body())
                        .contains("INVALID_INPUT")
                        .contains("destination");
                assertThat(roomRepository.count()).isEqualTo(roomCountBefore);
            }

            @Test
            @DisplayName("멤버 생성에 실패하면 방과 일정도 저장하지 않는다")
            void rollsBackRoomWhenCreatorCannotBeSaved() throws java.lang.Exception {
                String email = uniqueEmail();
                String cookie = login(email);
                LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
                long roomCountBefore = roomRepository.count();
                long memberCountBefore = roomMemberRepository.count();
                Long scheduleDayCountBefore =
                        jdbcTemplate.queryForObject(
                                "select count(*) from schedule_day", Long.class);
                userRepository.delete(userRepository.findByEmail(email).orElseThrow());

                HttpResponse<String> response =
                        post("/api/rooms", requestBody("여행", "서울", start, start), cookie);

                assertThat(response.statusCode()).isEqualTo(404);
                assertThat(response.body()).contains("USER_NOT_FOUND");
                assertThat(roomRepository.count()).isEqualTo(roomCountBefore);
                assertThat(roomMemberRepository.count()).isEqualTo(memberCountBefore);
                assertThat(
                                jdbcTemplate.queryForObject(
                                        "select count(*) from schedule_day", Long.class))
                        .isEqualTo(scheduleDayCountBefore);
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

    private String requestBody(String name, String destination, LocalDate start, LocalDate end) {
        return "{\"name\":\""
                + name
                + "\",\"destination\":\""
                + destination
                + "\",\"startDate\":\""
                + start
                + "\",\"endDate\":\""
                + end
                + "\"}";
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private Room roomFromResponse(String body) {
        Matcher matcher = Pattern.compile("\\\"roomId\\\":(\\d+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        return roomRepository.findById(Long.parseLong(matcher.group(1))).orElseThrow();
    }
}
