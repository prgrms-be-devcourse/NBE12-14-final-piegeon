package world.chakchak.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
 * 투표(V-03)와 안건 코멘트(V-06)가 커밋된 뒤 같은 방 멤버의 SSE 스트림에 실제로 전달되는지 확인한다.
 *
 * <p>표 수(vote.updated)와 코멘트(comment.created)는 기록하지 않는 일시 이벤트라 SSE id가 없다. 안건 결정은 방 활동 기록으로 남아 로그
 * id를 SSE id로 쓰는 activity 이벤트로 간다.
 */
@IntegrationTest
class AgendaSseIntegrationTest {

    private static final String PASSWORD = "password123!";

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
        String jieunEmail = UUID.randomUUID() + "@example.com";
        minsuCookie = login(minsuEmail);
        jieunCookie = login(jieunEmail);
        minsu = userRepository.findByEmail(minsuEmail).orElseThrow();
        jieun = userRepository.findByEmail(jieunEmail).orElseThrow();
    }

    // FK 순서대로 지운다. 안건과 결정된 선지가 서로를 참조하므로 그 연결을 먼저 끊고, 행위자가 있는 활동 기록은 멤버보다 먼저 지운다
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
            jdbcTemplate.update("delete from change_log where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room where id = ?", roomId);
        }
    }

    private Room saveRoom() {
        LocalDate start = LocalDate.now(clock).plusDays(30);
        Room room =
                roomRepository.save(
                        Room.create(
                                "여름 여행",
                                "부산",
                                start,
                                start.plusDays(2),
                                UUID.randomUUID().toString().substring(0, 8),
                                RoomStatus.SURVEYING,
                                clock));
        roomIds.add(room.getId());
        return room;
    }

    private RoomMember join(Room room, User user) {
        return roomMemberRepository.save(RoomMember.create(room, user, clock));
    }

    // 멤버보다 나중에 열어야 투표 자격이 생긴다
    private AgendaItem openBudget(Room room) {
        LocalDateTime now = LocalDateTime.now(clock);
        return agendaItemRepository.save(
                AgendaItem.create(
                        room, AgendaSubject.BUDGET, 1, "예산을 정해주세요", 2, now, now.plusDays(3)));
    }

    private static boolean hasSseId(String event) {
        return event.lines().anyMatch(line -> line.startsWith("id:"));
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName(
                    "투표하면 같은 방 멤버에게 id 없는 vote.updated가 가고, 결정되면 결정 기록이 id 있는 activity로 가며 투표자를 행위자로 저장된다")
            void streamsVoteUpdatedAndResolvedActivity() throws Exception {
                Room room = saveRoom();
                join(room, minsu);
                RoomMember jieunMember = join(room, jieun);
                AgendaItem budget = openBudget(room);
                AgendaOption low =
                        agendaOptionRepository.save(AgendaOption.budget(budget, 600_000L, 0, null));
                agendaOptionRepository.save(AgendaOption.budget(budget, 1_000_000L, 1, null));

                try (SseReader reader = new SseReader(openStream(room, jieunCookie).body())) {
                    assertThat(reader.readEvent()).contains("event:connected");

                    assertThat(vote(room, budget, low, minsuCookie).statusCode()).isEqualTo(200);
                    String firstVote = reader.readEvent();
                    assertThat(firstVote)
                            .contains("event:vote.updated")
                            .contains("\"agendaId\":" + budget.getId())
                            .contains("\"votedCount\":1")
                            .contains("{\"optionId\":" + low.getId() + ",\"voteCount\":1}");
                    assertThat(hasSseId(firstVote)).isFalse();

                    assertThat(vote(room, budget, low, jieunCookie).statusCode()).isEqualTo(200);
                    String resolved = reader.readEvent();
                    assertThat(resolved).contains("event:activity").contains("예산 1회차 결정: 60만원");
                    assertThat(hasSseId(resolved)).isTrue();
                    String secondVote = reader.readEvent();
                    assertThat(secondVote)
                            .contains("event:vote.updated")
                            .contains("\"votedCount\":2");
                    assertThat(hasSseId(secondVote)).isFalse();
                }

                List<Map<String, Object>> logs =
                        jdbcTemplate.queryForList(
                                "select event_kind, entity_type, entity_id, plan_room_member_id"
                                        + " from change_log where plan_room_id = ?",
                                room.getId());
                assertThat(logs).hasSize(1);
                assertThat(logs.getFirst())
                        .containsEntry("event_kind", "AGENDA_RESOLVED")
                        .containsEntry("entity_type", "AGENDA")
                        .containsEntry("entity_id", budget.getId())
                        .containsEntry("plan_room_member_id", jieunMember.getId());
            }
        }
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("안건 코멘트를 남기면 같은 방 멤버에게 id 없는 comment.created가 가고 활동 기록은 남지 않는다")
            void streamsCommentCreated() throws Exception {
                Room room = saveRoom();
                join(room, minsu);
                join(room, jieun);
                AgendaItem budget = openBudget(room);

                try (SseReader reader = new SseReader(openStream(room, jieunCookie).body())) {
                    assertThat(reader.readEvent()).contains("event:connected");

                    HttpResponse<String> response =
                            post(
                                    "/api/rooms/"
                                            + room.getId()
                                            + "/agendas/"
                                            + budget.getId()
                                            + "/comments",
                                    "{\"body\":\"60만원이 좋아요\"}",
                                    minsuCookie);

                    assertThat(response.statusCode()).isEqualTo(201);
                    String comment = reader.readEvent();
                    assertThat(comment)
                            .contains("event:comment.created")
                            .contains("\"agendaId\":" + budget.getId())
                            .contains("\"placeId\":null")
                            .contains("\"body\":\"60만원이 좋아요\"");
                    assertThat(hasSseId(comment)).isFalse();
                }

                assertThat(
                                jdbcTemplate.queryForObject(
                                        "select count(*) from change_log where plan_room_id = ?",
                                        Long.class,
                                        room.getId()))
                        .isZero();
            }
        }
    }

    private HttpResponse<InputStream> openStream(Room room, String cookie) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(uri("/api/rooms/" + room.getId() + "/events"))
                        .header("Accept", "text/event-stream")
                        .header("Cookie", cookie)
                        .GET()
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    private HttpResponse<String> vote(
            Room room, AgendaItem agenda, AgendaOption option, String cookie) throws Exception {
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
                        .PUT(
                                HttpRequest.BodyPublishers.ofString(
                                        "{\"optionId\":" + option.getId() + ",\"comment\":null}"))
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
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

    /** SSE 이벤트를 빈 줄 단위로 하나씩 읽는다. 5초 안에 오지 않으면 실패한다. */
    private static class SseReader implements AutoCloseable {

        private final InputStream inputStream;
        private final BufferedReader reader;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        private SseReader(InputStream inputStream) {
            this.inputStream = inputStream;
            this.reader =
                    new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }

        private String readEvent() throws Exception {
            CompletableFuture<String> event =
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    StringBuilder result = new StringBuilder();
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        if (line.isEmpty()) {
                                            if (!result.isEmpty()) {
                                                return result.toString();
                                            }
                                        } else {
                                            result.append(line).append('\n');
                                        }
                                    }
                                    throw new IllegalStateException("SSE 연결이 예상보다 일찍 닫혔습니다.");
                                } catch (Exception e) {
                                    throw new IllegalStateException(e);
                                }
                            },
                            executor);
            try {
                return event.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                inputStream.close();
                event.cancel(true);
                throw e;
            }
        }

        @Override
        public void close() throws Exception {
            inputStream.close();
            executor.close();
        }
    }
}
