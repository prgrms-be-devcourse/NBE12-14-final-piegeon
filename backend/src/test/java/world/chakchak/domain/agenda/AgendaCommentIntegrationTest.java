package world.chakchak.domain.agenda;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.comment.repository.CommentRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/** V-05·V-06 안건 코멘트를 실제 서버에 HTTP로 보내 요청 검증, 방 단계 확인, 저장과 목록 순서를 확인한다. */
@IntegrationTest
class AgendaCommentIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Long> roomIds = new ArrayList<>();

    private String minsuCookie;
    private String jieunCookie;
    private User minsu;
    private User jieun;

    @BeforeEach
    void setUp() throws java.lang.Exception {
        String minsuEmail = UUID.randomUUID() + "@example.com";
        String jieunEmail = UUID.randomUUID() + "@example.com";
        minsuCookie = login(minsuEmail);
        jieunCookie = login(jieunEmail);
        minsu = userRepository.findByEmail(minsuEmail).orElseThrow();
        jieun = userRepository.findByEmail(jieunEmail).orElseThrow();
    }

    // FK 순서대로 지운다. 이 테스트는 안건을 결정하지 않으므로 결정 선지 연결을 끊을 필요가 없다
    @AfterEach
    void tearDown() {
        for (Long roomId : roomIds) {
            String agendasOfRoom = "select id from agenda_item where plan_room_id = ?";
            jdbcTemplate.update(
                    "delete from comment where agenda_id in (" + agendasOfRoom + ")", roomId);
            jdbcTemplate.update("delete from agenda_item where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room where id = ?", roomId);
        }
    }

    private Room roomWithMembers(RoomStatus status) {
        LocalDate start = LocalDate.now(clock).plusDays(30);
        Room room =
                roomRepository.save(
                        Room.create(
                                "여름 여행",
                                "부산",
                                start,
                                start.plusDays(2),
                                UUID.randomUUID().toString().substring(0, 8),
                                status,
                                clock));
        roomIds.add(room.getId());
        roomMemberRepository.save(RoomMember.create(room, minsu, clock));
        roomMemberRepository.save(RoomMember.create(room, jieun, clock));
        return room;
    }

    private AgendaItem openAgenda(Room room) {
        LocalDateTime now = LocalDateTime.now(clock);
        return agendaItemRepository.save(
                AgendaItem.create(room, AgendaSubject.BUDGET, 1, "예산", 2, now, now.plusDays(3)));
    }

    private String commentsPath(Room room, AgendaItem agenda) {
        return "/api/rooms/" + room.getId() + "/agendas/" + agenda.getId() + "/comments";
    }

    private static String commentBody(String body) {
        return "{\"body\":\"" + body + "\"}";
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("코멘트를 남기면 201 COMMENT_CREATED로 응답하고 목록에서 오래된 순으로 보인다")
            void createsAndListsInOrder() throws java.lang.Exception {
                Room room = roomWithMembers(RoomStatus.SURVEYING);
                AgendaItem agenda = openAgenda(room);

                HttpResponse<String> first =
                        post(commentsPath(room, agenda), commentBody("150만 원은 부담돼요"), minsuCookie);
                HttpResponse<String> second =
                        post(commentsPath(room, agenda), commentBody("저는 괜찮아요"), jieunCookie);
                HttpResponse<String> list = get(commentsPath(room, agenda), minsuCookie);

                assertThat(first.statusCode()).isEqualTo(201);
                assertThat(first.body())
                        .contains("\"code\":\"COMMENT_CREATED\"")
                        .contains("\"body\":\"150만 원은 부담돼요\"")
                        .contains("\"name\":\"여행자\"");
                assertThat(second.statusCode()).isEqualTo(201);
                assertThat(list.statusCode()).isEqualTo(200);
                assertThat(list.body()).contains("\"code\":\"COMMENTS_FETCHED\"");
                assertThat(list.body().indexOf("150만 원은 부담돼요"))
                        .isLessThan(list.body().indexOf("저는 괜찮아요"));
                assertThat(commentRepository.findAllByAgendaItemIdOrderByIdAsc(agenda.getId()))
                        .hasSize(2);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("본문이 비었거나 500자를 넘으면 400 INVALID_INPUT으로 응답하고 저장하지 않는다")
            void rejectsInvalidBody() throws java.lang.Exception {
                Room room = roomWithMembers(RoomStatus.PLANNING);
                AgendaItem agenda = openAgenda(room);

                HttpResponse<String> blank =
                        post(commentsPath(room, agenda), commentBody("  "), minsuCookie);
                HttpResponse<String> tooLong =
                        post(commentsPath(room, agenda), commentBody("가".repeat(501)), minsuCookie);

                assertThat(blank.statusCode()).isEqualTo(400);
                assertThat(blank.body()).contains("\"code\":\"INVALID_INPUT\"");
                assertThat(tooLong.statusCode()).isEqualTo(400);
                assertThat(tooLong.body()).contains("\"code\":\"INVALID_INPUT\"");
                assertThat(commentRepository.findAllByAgendaItemIdOrderByIdAsc(agenda.getId()))
                        .isEmpty();
            }

            @Test
            @DisplayName("확정된 방에서는 409 INVALID_STATUS로 응답하고 저장하지 않는다")
            void rejectsFinalizedRoom() throws java.lang.Exception {
                Room room = roomWithMembers(RoomStatus.FINALIZED);
                AgendaItem agenda = openAgenda(room);

                HttpResponse<String> response =
                        post(commentsPath(room, agenda), commentBody("늦었나요"), minsuCookie);

                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(response.body()).contains("\"code\":\"INVALID_STATUS\"");
                assertThat(commentRepository.findAllByAgendaItemIdOrderByIdAsc(agenda.getId()))
                        .isEmpty();
            }
        }
    }

    private String login(String email) throws java.lang.Exception {
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

    private HttpResponse<String> get(String path, String cookie) throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(uri(path)).header("Cookie", cookie).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String cookie)
            throws java.lang.Exception {
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
}
