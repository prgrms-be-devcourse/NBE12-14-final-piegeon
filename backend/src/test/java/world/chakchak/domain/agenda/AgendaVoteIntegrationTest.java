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
import world.chakchak.domain.agenda.entity.FinalPlanChoice;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.agenda.repository.AgendaOptionRepository;
import world.chakchak.domain.comment.entity.Comment;
import world.chakchak.domain.comment.repository.CommentRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/**
 * V-03 투표를 실제 서버에 HTTP로 보내 방 잠금 → 표 저장 → 7-1 판정 → 코멘트 저장이 한 트랜잭션으로 DB에 반영되는지 확인한다.
 *
 * <p>서버는 실제 시각으로 동작하므로 출발일을 오늘 + 30일로 두고, 투표 자격이 생기도록 멤버를 안건보다 먼저 저장한다.
 */
@IntegrationTest
class AgendaVoteIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private AgendaOptionRepository agendaOptionRepository;
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

    // FK 순서대로 지운다. 안건과 결정된 선지가 서로를 참조하므로 그 연결을 먼저 끊는다 (명세 13-3)
    // ck_agenda_item_resolved(RESOLVED ⇔ 결정 선지·시각 있음) 때문에 결정 정보를 지울 때 상태도 함께 RESOLVED가 아닌 값으로 바꾼다
    @AfterEach
    void tearDown() {
        for (Long roomId : roomIds) {
            String agendasOfRoom = "select id from agenda_item where plan_room_id = ?";
            jdbcTemplate.update(
                    "delete from comment where agenda_id in (" + agendasOfRoom + ")", roomId);
            jdbcTemplate.update(
                    "delete from vote where agenda_id in (" + agendasOfRoom + ")", roomId);
            jdbcTemplate.update(
                    "update agenda_item set resolved_option_id = null, resolved_at = null,"
                            + " status = 'EXPIRED' where plan_room_id = ?",
                    roomId);
            jdbcTemplate.update(
                    "delete from agenda_option where agenda_id in (" + agendasOfRoom + ")", roomId);
            jdbcTemplate.update("delete from agenda_item where plan_room_id = ?", roomId);
            // 안건을 결정한 투표자가 활동 기록의 행위자로 남으므로 멤버보다 먼저 지운다
            jdbcTemplate.update("delete from change_log where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room where id = ?", roomId);
        }
    }

    /** 민수와 지은이 멤버인 방. 멤버를 먼저 저장해야 이후에 여는 안건의 투표 자격이 생긴다. */
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

    private AgendaItem openAgenda(Room room, AgendaSubject subject) {
        LocalDateTime now = LocalDateTime.now(clock);
        return agendaItemRepository.save(
                AgendaItem.create(room, subject, 1, "안건 제목", 2, now, now.plusDays(3)));
    }

    private HttpResponse<String> vote(Room room, AgendaItem agenda, String body, String cookie)
            throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(
                                uri(
                                        "/api/rooms/"
                                                + room.getId()
                                                + "/agendas/"
                                                + agenda.getId()
                                                + "/vote"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", cookie)
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String voteBody(AgendaOption option, String comment) {
        String commentJson = comment == null ? "null" : "\"" + comment + "\"";
        return "{\"optionId\":" + option.getId() + ",\"comment\":" + commentJson + "}";
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("두 자격자가 같은 선지에 투표하면 두 번째 표에서 안건이 결정되고 VOTE_SAVED로 응답한다")
            void resolvesWhenBothVoteSameOption() throws java.lang.Exception {
                Room room = roomWithMembers(RoomStatus.SURVEYING);
                AgendaItem budget = openAgenda(room, AgendaSubject.BUDGET);
                AgendaOption low =
                        agendaOptionRepository.save(AgendaOption.budget(budget, 600_000L, 0, null));

                HttpResponse<String> first = vote(room, budget, voteBody(low, null), minsuCookie);
                HttpResponse<String> second = vote(room, budget, voteBody(low, null), jieunCookie);

                assertThat(first.statusCode()).isEqualTo(200);
                assertThat(first.body())
                        .contains("\"code\":\"VOTE_SAVED\"")
                        .contains("투표했습니다.")
                        .contains("\"status\":\"VOTING\"")
                        .contains("\"votedCount\":1");
                assertThat(second.statusCode()).isEqualTo(200);
                assertThat(second.body())
                        .contains("\"status\":\"RESOLVED\"")
                        .contains("\"votedCount\":2");
                AgendaItem saved = agendaItemRepository.findById(budget.getId()).orElseThrow();
                assertThat(saved.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(saved.getResolvedOptionId()).isEqualTo(low.getId());
            }

            @Test
            @DisplayName("최종 확정에 이유와 함께 반대하면 안건이 반대로 끝나고 방이 PLANNING으로 돌아가며 코멘트가 저장된다")
            void rejectsFinalPlanWithComment() throws java.lang.Exception {
                Room room = roomWithMembers(RoomStatus.REVIEWING);
                AgendaItem finalPlan = openAgenda(room, AgendaSubject.FINAL_PLAN);
                agendaOptionRepository.save(
                        AgendaOption.finalPlan(finalPlan, FinalPlanChoice.AGREE));
                AgendaOption reject =
                        agendaOptionRepository.save(
                                AgendaOption.finalPlan(finalPlan, FinalPlanChoice.REJECT));

                HttpResponse<String> response =
                        vote(room, finalPlan, voteBody(reject, "일정이 너무 빡빡해요"), minsuCookie);

                assertThat(response.statusCode()).isEqualTo(200);
                AgendaItem saved = agendaItemRepository.findById(finalPlan.getId()).orElseThrow();
                assertThat(saved.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(saved.getResolvedOptionId()).isEqualTo(reject.getId());
                assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                        .isEqualTo(RoomStatus.PLANNING);
                List<Comment> comments =
                        commentRepository.findAllByAgendaItemIdOrderByIdAsc(finalPlan.getId());
                assertThat(comments)
                        .singleElement()
                        .extracting(Comment::getBody)
                        .isEqualTo("일정이 너무 빡빡해요");
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("optionId가 없으면 400 INVALID_INPUT으로 응답한다")
            void rejectsMissingOptionId() throws java.lang.Exception {
                Room room = roomWithMembers(RoomStatus.SURVEYING);
                AgendaItem budget = openAgenda(room, AgendaSubject.BUDGET);

                HttpResponse<String> response =
                        vote(room, budget, "{\"comment\":null}", minsuCookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("\"code\":\"INVALID_INPUT\"");
            }

            @Test
            @DisplayName("최종 확정에 이유 없이 반대하면 400 REJECT_COMMENT_REQUIRED로 응답하고 안건과 방이 그대로다")
            void rejectsRejectWithoutComment() throws java.lang.Exception {
                Room room = roomWithMembers(RoomStatus.REVIEWING);
                AgendaItem finalPlan = openAgenda(room, AgendaSubject.FINAL_PLAN);
                AgendaOption reject =
                        agendaOptionRepository.save(
                                AgendaOption.finalPlan(finalPlan, FinalPlanChoice.REJECT));

                HttpResponse<String> response =
                        vote(room, finalPlan, voteBody(reject, null), minsuCookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("\"code\":\"REJECT_COMMENT_REQUIRED\"");
                assertThat(
                                agendaItemRepository
                                        .findById(finalPlan.getId())
                                        .orElseThrow()
                                        .getStatus())
                        .isEqualTo(AgendaStatus.VOTING);
                assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                        .isEqualTo(RoomStatus.REVIEWING);
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
