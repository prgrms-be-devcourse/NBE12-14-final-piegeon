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
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.agenda.repository.AgendaOptionRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/**
 * V-09 자유 안건 열기와, 자유 안건의 투표(V-03)·다음 회차(V-04)를 실제 서버에 HTTP로 보내 DB에 반영되는지 확인한다.
 *
 * <p>서버는 실제 시각으로 동작하므로 출발일을 오늘 + 30일로 두고, 투표 자격이 생기도록 멤버를 안건보다 먼저 저장한다.
 */
@IntegrationTest
class AgendaCustomIntegrationTest {

    private static final String PASSWORD = "password123!";
    private static final String RENTAL = "렌터카 빌릴까?";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private AgendaOptionRepository agendaOptionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Long> roomIds = new ArrayList<>();

    private String minsuCookie;
    private String jieunCookie;
    private User minsu;
    private User jieun;

    @BeforeEach
    void setUp() throws Exception {
        String minsuEmail = UUID.randomUUID() + "@example.com";
        minsuCookie = login(minsuEmail);
        minsu = userRepository.findByEmail(minsuEmail).orElseThrow();
        String jieunEmail = UUID.randomUUID() + "@example.com";
        jieunCookie = login(jieunEmail);
        jieun = userRepository.findByEmail(jieunEmail).orElseThrow();
    }

    // FK 순서대로 지운다. 안건과 결정된 선지가 서로를 참조하므로 그 연결을 먼저 끊는다
    @AfterEach
    void tearDown() {
        for (Long roomId : roomIds) {
            String agendasOfRoom = "select id from agenda_item where plan_room_id = ?";
            jdbcTemplate.update(
                    "delete from vote where agenda_id in (" + agendasOfRoom + ")", roomId);
            jdbcTemplate.update(
                    "delete from comment where agenda_id in (" + agendasOfRoom + ")", roomId);
            jdbcTemplate.update(
                    "update agenda_item set resolved_option_id = null, resolved_at = null,"
                            + " status = 'EXPIRED' where plan_room_id = ?",
                    roomId);
            jdbcTemplate.update(
                    "delete from agenda_option where agenda_id in (" + agendasOfRoom + ")", roomId);
            jdbcTemplate.update("delete from agenda_item where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from change_log where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room where id = ?", roomId);
        }
    }

    /** 민수와 지은이 멤버인 방 */
    private Room roomIn(RoomStatus status) {
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

    private HttpResponse<String> openCustom(Room room, String body) throws Exception {
        return send("POST", "/api/rooms/" + room.getId() + "/agendas", body, minsuCookie);
    }

    private static String customBody(String title, String... options) {
        return "{\"title\":\""
                + title
                + "\",\"options\":["
                + String.join(",", List.of(options).stream().map(o -> "\"" + o + "\"").toList())
                + "]}";
    }

    private AgendaItem latest(Room room, String title) {
        return agendaItemRepository
                .findTopByRoomIdAndSubjectAndTitleOrderByRoundDesc(
                        room.getId(), AgendaSubject.CUSTOM, title)
                .orElseThrow();
    }

    private List<AgendaOption> options(AgendaItem agenda) {
        return agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(agenda.getId());
    }

    private HttpResponse<String> vote(
            Room room, AgendaItem agenda, AgendaOption option, String cookie) throws Exception {
        return send(
                "PUT",
                "/api/rooms/" + room.getId() + "/agendas/" + agenda.getId() + "/vote",
                "{\"optionId\":" + option.getId() + "}",
                cookie);
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("일정을 짜는 중인 방에 자유 안건을 열면 201로 응답하고 제목과 선지가 공백 없이 적은 순서대로 저장된다")
            void opensCustomAgenda() throws Exception {
                Room room = roomIn(RoomStatus.PLANNING);

                HttpResponse<String> response =
                        openCustom(room, customBody("  " + RENTAL + " ", " 빌린다", "안 빌린다 "));

                assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
                assertThat(response.body())
                        .contains("\"code\":\"AGENDA_CREATED\"")
                        .contains("\"subject\":\"CUSTOM\"");
                AgendaItem agenda = latest(room, RENTAL);
                assertThat(agenda.getRound()).isEqualTo(1);
                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.VOTING);
                assertThat(agenda.getTotalVoter()).isEqualTo(2);
                assertThat(options(agenda))
                        .extracting(AgendaOption::getLabel)
                        .containsExactly("빌린다", "안 빌린다");
                assertThat(
                                jdbcTemplate.queryForList(
                                        "select summary from change_log where plan_room_id = ?",
                                        String.class,
                                        room.getId()))
                        .containsExactly("'" + RENTAL + "' 1회차 투표가 열렸어요");
            }

            @Test
            @DisplayName("일정을 짜는 중에 자유 안건에 모두 같은 선지를 고르면 결정되고 방은 그대로 PLANNING이다")
            void resolvesCustomAgendaInPlanning() throws Exception {
                Room room = roomIn(RoomStatus.PLANNING);
                openCustom(room, customBody(RENTAL, "빌린다", "안 빌린다"));
                AgendaItem agenda = latest(room, RENTAL);
                AgendaOption rent = options(agenda).getFirst();

                assertThat(vote(room, agenda, rent, minsuCookie).statusCode()).isEqualTo(200);
                HttpResponse<String> last = vote(room, agenda, rent, jieunCookie);

                assertThat(last.statusCode()).as(last.body()).isEqualTo(200);
                AgendaItem resolved = agendaItemRepository.findById(agenda.getId()).orElseThrow();
                assertThat(resolved.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(resolved.getResolvedOptionId()).isEqualTo(rent.getId());
                assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                        .isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("미결로 끝난 자유 안건은 일정을 짜는 중에도 다음 회차를 열 수 있고 선지를 그대로 옮기며 다른 제목의 회차와 섞이지 않는다")
            void opensCustomNextRound() throws Exception {
                Room room = roomIn(RoomStatus.PLANNING);
                openCustom(room, customBody(RENTAL, "빌린다", "안 빌린다"));
                openCustom(room, customBody("조식 포함할까?", "포함", "미포함"));
                AgendaItem previous = latest(room, RENTAL);
                jdbcTemplate.update(
                        "update agenda_item set status = 'EXPIRED' where id = ?", previous.getId());

                HttpResponse<String> response =
                        send(
                                "POST",
                                "/api/rooms/"
                                        + room.getId()
                                        + "/agendas/"
                                        + previous.getId()
                                        + "/next-round",
                                "",
                                minsuCookie);

                assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
                AgendaItem next = latest(room, RENTAL);
                assertThat(next.getRound()).isEqualTo(2);
                assertThat(options(next))
                        .extracting(AgendaOption::getLabel)
                        .containsExactly("빌린다", "안 빌린다");
                assertThat(latest(room, "조식 포함할까?").getRound()).isEqualTo(1);
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("같은 방에 같은 제목의 자유 안건이 있으면 409 AGENDA_TITLE_DUPLICATED와 그 안건 id로 응답한다")
            void rejectsDuplicateTitle() throws Exception {
                Room room = roomIn(RoomStatus.SURVEYING);
                openCustom(room, customBody(RENTAL, "빌린다", "안 빌린다"));
                Long existingId = latest(room, RENTAL).getId();

                HttpResponse<String> response =
                        openCustom(room, customBody(RENTAL + " ", "예", "아니오"));

                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(response.body())
                        .contains("\"code\":\"AGENDA_TITLE_DUPLICATED\"")
                        .contains("\"agendaId\":" + existingId);
            }

            @Test
            @DisplayName("공백을 지우면 같은 선지가 있으면 400 INVALID_INPUT으로 응답한다")
            void rejectsDuplicateOptions() throws Exception {
                Room room = roomIn(RoomStatus.SURVEYING);

                HttpResponse<String> response =
                        openCustom(room, customBody(RENTAL, "빌린다", " 빌린다 "));

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("INVALID_INPUT");
            }

            @Test
            @DisplayName("선지가 2개보다 적으면 400 INVALID_INPUT으로 응답한다")
            void rejectsTooFewOptions() throws Exception {
                Room room = roomIn(RoomStatus.SURVEYING);

                HttpResponse<String> response = openCustom(room, customBody(RENTAL, "빌린다"));

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("INVALID_INPUT");
            }

            @Test
            @DisplayName("최종 확정 투표 중인 방에서는 자유 안건을 열 수 없어 409 INVALID_STATUS로 응답한다")
            void rejectsWhileReviewing() throws Exception {
                Room room = roomIn(RoomStatus.REVIEWING);

                HttpResponse<String> response =
                        openCustom(room, customBody(RENTAL, "빌린다", "안 빌린다"));

                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(response.body()).contains("INVALID_STATUS");
            }

            @Test
            @DisplayName("일정을 짜는 중에는 설문 안건에 투표할 수 없어 409 INVALID_STATUS로 응답한다")
            void rejectsSurveyVoteInPlanning() throws Exception {
                Room room = roomIn(RoomStatus.PLANNING);
                LocalDateTime now = LocalDateTime.now(clock);
                AgendaItem budget =
                        agendaItemRepository.save(
                                AgendaItem.create(
                                        room,
                                        AgendaSubject.BUDGET,
                                        1,
                                        "예산을 정해주세요",
                                        2,
                                        now,
                                        now.plusDays(3)));
                AgendaOption option =
                        agendaOptionRepository.save(AgendaOption.budget(budget, 600_000L, 0, null));

                HttpResponse<String> response = vote(room, budget, option, minsuCookie);

                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(response.body()).contains("INVALID_STATUS");
            }

            @Test
            @DisplayName("일정을 짜는 중에는 미결 설문 안건의 다음 회차를 열 수 없어 409 INVALID_STATUS로 응답한다")
            void rejectsSurveyNextRoundInPlanning() throws Exception {
                Room room = roomIn(RoomStatus.PLANNING);
                LocalDateTime now = LocalDateTime.now(clock);
                AgendaItem budget =
                        AgendaItem.create(
                                room,
                                AgendaSubject.BUDGET,
                                1,
                                "예산을 정해주세요",
                                2,
                                now,
                                now.plusDays(3));
                budget.expire();
                agendaItemRepository.save(budget);

                HttpResponse<String> response =
                        send(
                                "POST",
                                "/api/rooms/"
                                        + room.getId()
                                        + "/agendas/"
                                        + budget.getId()
                                        + "/next-round",
                                "",
                                minsuCookie);

                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(response.body()).contains("INVALID_STATUS");
            }
        }
    }

    private String login(String email) throws Exception {
        send(
                "POST",
                "/api/auth/signup",
                "{\"email\":\"" + email + "\",\"name\":\"여행자\",\"password\":\"" + PASSWORD + "\"}",
                null);
        HttpResponse<String> login =
                send(
                        "POST",
                        "/api/auth/login",
                        "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}",
                        null);
        return login.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    private HttpResponse<String> send(String method, String path, String body, String cookie)
            throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
