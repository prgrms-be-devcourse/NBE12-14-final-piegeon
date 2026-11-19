package world.chakchak.domain.changelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
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
class ChangeLogTimelineIntegrationTest {

    private static final String PASSWORD = "password123!";
    private static final String STREAM_PATH = "/api/rooms/%d/change-logs/stream";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private Clock clock;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    @Nested
    class Stream {

        @Nested
        class Success {

            @Test
            @DisplayName("활동 멤버는 준비 기준점 이후 커밋된 방 활동을 SSE로 받는다")
            void sendsCommittedRoomActivityAfterReadyEvent() throws Exception {
                String ownerEmail = uniqueEmail();
                String ownerCookie = login(ownerEmail, "방장");
                User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
                Room room = saveRoom();
                join(room, owner);

                HttpResponse<InputStream> stream = openStream(room.getId(), ownerCookie, null);
                try (SseReader reader = new SseReader(stream.body())) {
                    String readyEvent = reader.readEvent();
                    assertThat(readyEvent).contains("event:ready");
                    assertThat(readyEvent).contains("\"latestActivityId\":null");

                    String inviteCode = room.getInviteCode();
                    String joinerCookie = login(uniqueEmail(), "새 멤버");
                    HttpResponse<String> joined =
                            post(
                                    "/api/rooms/invites/" + inviteCode + "/join",
                                    joinerCookie);
                    assertThat(joined.statusCode()).isEqualTo(200);

                    String activityEvent = reader.readEvent();
                    assertThat(activityEvent).contains("event:activity");
                    assertThat(activityEvent).contains("id:");
                    assertThat(activityEvent).contains("\"eventKind\":\"MEMBER_JOINED\"");
                    assertThat(activityEvent).contains("\"summary\":\"새 멤버님이 합류했어요\"");
                }
            }

            @Test
            @DisplayName("재연결은 마지막 활동 ID 다음의 기록을 오름차순으로 재생한다")
            void replaysActivitiesAfterLastEventId() throws Exception {
                String ownerEmail = uniqueEmail();
                String ownerCookie = login(ownerEmail, "방장");
                User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
                Room room = saveRoom();
                join(room, owner);
                joinRoom(room, "첫 활동");

                HttpResponse<InputStream> initialStream =
                        openStream(room.getId(), ownerCookie, null);
                String lastDeliveredId;
                try (SseReader reader = new SseReader(initialStream.body())) {
                    assertThat(reader.readEvent()).contains("event:ready");
                    joinRoom(room, "두 번째 활동");
                    String secondActivity = reader.readEvent();
                    assertThat(secondActivity).contains("event:activity");
                    lastDeliveredId = eventId(secondActivity);
                    joinRoom(room, "세 번째 활동");
                    String thirdActivity = reader.readEvent();
                    assertThat(thirdActivity).contains("event:activity");
                    lastDeliveredId = eventId(thirdActivity);
                }

                joinRoom(room, "연결이 끊긴 동안의 활동");
                HttpResponse<InputStream> resumed =
                        openStream(room.getId(), ownerCookie, lastDeliveredId);
                try (SseReader reader = new SseReader(resumed.body())) {
                    String replayed = reader.readEvent();
                    assertThat(replayed).contains("event:activity");
                    assertThat(eventId(replayed)).isGreaterThan(Long.parseLong(lastDeliveredId));
                    assertThat(replayed).contains("연결이 끊긴 동안의 활동님이 합류했어요");
                    assertThat(reader.readEvent()).contains("event:ready");
                }
            }

            @Test
            @DisplayName("누락 활동이 200건을 넘으면 재동기화 이벤트를 보내고 200건은 재생한다")
            void requiresResyncAboveReplayLimit() throws Exception {
                String ownerEmail = uniqueEmail();
                String ownerCookie = login(ownerEmail, "방장");
                User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
                Room room = saveRoom();
                RoomMember member = join(room, owner);
                insertActivities(room.getId(), member.getId(), 202);
                List<Long> activityIds =
                        jdbcTemplate.queryForList(
                                "select id from change_log where plan_room_id = ? order by id",
                                Long.class,
                                room.getId());

                HttpResponse<InputStream> overflow =
                        openStream(room.getId(), ownerCookie, activityIds.getFirst().toString());
                try (SseReader reader = new SseReader(overflow.body())) {
                    assertThat(reader.readEvent()).contains("event:resync.required");
                    assertThat(reader.readEvent()).contains("event:ready");
                }

                HttpResponse<InputStream> atLimit =
                        openStream(room.getId(), ownerCookie, activityIds.get(1).toString());
                try (SseReader reader = new SseReader(atLimit.body())) {
                    String firstReplay = reader.readEvent();
                    assertThat(firstReplay).contains("event:activity");
                    assertThat(eventId(firstReplay)).isEqualTo(activityIds.get(2));
                }
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("비인증 사용자와 활동 멤버가 아닌 사용자는 방 활동 스트림을 열 수 없다")
            void requiresAuthenticationAndActiveMembership() throws Exception {
                Room room = saveRoom();
                String outsiderCookie = login(uniqueEmail(), "방 외부 사용자");

                HttpResponse<InputStream> unauthenticated = openStream(room.getId(), null, null);
                HttpResponse<InputStream> outsider =
                        openStream(room.getId(), outsiderCookie, null);
                try {
                    assertThat(unauthenticated.statusCode()).isEqualTo(401);
                    assertThat(outsider.statusCode()).isEqualTo(403);
                } finally {
                    unauthenticated.body().close();
                    outsider.body().close();
                }
            }

            @Test
            @DisplayName("방을 나간 멤버의 활성 스트림은 이후 활동 전달을 중단한다")
            void closesStreamAfterMembershipEnds() throws Exception {
                String ownerEmail = uniqueEmail();
                String ownerCookie = login(ownerEmail, "방장");
                User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
                Room room = saveRoom();
                RoomMember member = join(room, owner);
                HttpResponse<InputStream> stream = openStream(room.getId(), ownerCookie, null);

                try (SseReader reader = new SseReader(stream.body())) {
                    assertThat(reader.readEvent()).contains("event:ready");
                    jdbcTemplate.update(
                            "update plan_room_member set left_at = ? where id = ?",
                            LocalDateTime.now(clock),
                            member.getId());
                    joinRoom(room, "다른 멤버");

                    assertThatThrownBy(reader::readEvent)
                            .isInstanceOf(java.util.concurrent.ExecutionException.class);
                }
            }
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("활동 멤버는 기준 ID와 커서로 타임라인을 연속해서 조회한다")
            void pagesTimelineThroughBaselineWithoutDuplicates() throws Exception {
                String ownerEmail = uniqueEmail();
                String ownerCookie = login(ownerEmail, "방장");
                User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
                Room room = saveRoom();
                join(room, owner);
                joinRoom(room, "첫 번째 멤버");
                joinRoom(room, "두 번째 멤버");
                joinRoom(room, "세 번째 멤버");

                HttpResponse<String> firstPage =
                        get("/api/rooms/" + room.getId() + "/change-logs?size=2", ownerCookie);

                assertThat(firstPage.statusCode()).isEqualTo(200);
                JsonNode firstPageData = objectMapper.readTree(firstPage.body()).path("data");
                JsonNode firstItems = firstPageData.path("items");
                assertThat(firstItems.size()).isEqualTo(2);
                Long cursor = firstPageData.path("nextCursor").longValue();
                Long throughId = firstItems.get(1).path("id").longValue();
                assertThat(firstItems.get(0).path("id").longValue()).isGreaterThan(cursor);

                HttpResponse<String> nextPage =
                        get(
                                "/api/rooms/" + room.getId() + "/change-logs?cursor=" + cursor
                                        + "&size=2",
                                ownerCookie);
                HttpResponse<String> boundedPage =
                        get(
                                "/api/rooms/" + room.getId() + "/change-logs?throughId="
                                        + throughId + "&size=10",
                                ownerCookie);

                JsonNode nextItems =
                        objectMapper.readTree(nextPage.body()).path("data").path("items");
                JsonNode boundedItems =
                        objectMapper.readTree(boundedPage.body()).path("data").path("items");
                assertThat(nextPage.statusCode()).isEqualTo(200);
                assertThat(nextItems.size()).isEqualTo(1);
                assertThat(nextItems.get(0).path("id").longValue()).isLessThan(cursor);
                assertThat(boundedPage.statusCode()).isEqualTo(200);
                assertThat(boundedItems.size()).isEqualTo(2);
                assertThat(boundedItems.get(0).path("id").longValue()).isEqualTo(throughId);
                assertThat(boundedItems.get(1).path("id").longValue())
                        .isLessThan(boundedItems.get(0).path("id").longValue());
            }

            @Test
            @DisplayName("활동 멤버는 실제 표시한 활동까지만 확인하고 이전 시각으로 되돌리지 않는다")
            void advancesSeenTimeMonotonicallyThroughDisplayedActivity() throws Exception {
                String ownerEmail = uniqueEmail();
                String ownerCookie = login(ownerEmail, "방장");
                User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
                Room room = saveRoom();
                RoomMember ownerMember = join(room, owner);
                LocalDateTime initialSeenAt = ownerMember.getLastSeenAt();
                joinRoom(room, "새 멤버");

                HttpResponse<String> timeline =
                        get("/api/rooms/" + room.getId() + "/change-logs", ownerCookie);
                JsonNode item =
                        objectMapper.readTree(timeline.body()).path("data").path("items").get(0);
                LocalDateTime displayedActivityAt =
                        LocalDateTime.parse(item.path("createdAt").stringValue());
                HttpResponse<String> marked =
                        postJson(
                                "/api/rooms/" + room.getId() + "/seen",
                                "{\"seenUntil\":\"" + displayedActivityAt + "\"}",
                                ownerCookie);

                assertThat(marked.statusCode()).isEqualTo(200);
                assertThat(lastSeenAt(ownerMember.getId())).isEqualTo(displayedActivityAt);

                HttpResponse<String> older =
                        postJson(
                                "/api/rooms/" + room.getId() + "/seen",
                                "{\"seenUntil\":\"" + initialSeenAt + "\"}",
                                ownerCookie);
                assertThat(older.statusCode()).isEqualTo(200);
                assertThat(lastSeenAt(ownerMember.getId())).isEqualTo(displayedActivityAt);

                get("/api/rooms/" + room.getId() + "/change-logs", ownerCookie);
                assertThat(lastSeenAt(ownerMember.getId())).isEqualTo(displayedActivityAt);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("다른 방의 활동 멤버가 아닌 사용자는 타임라인을 조회할 수 없다")
            void forbidsTimelineForNonMember() throws Exception {
                Room room = saveRoom();
                String outsiderCookie = login(uniqueEmail(), "방 외부 사용자");

                HttpResponse<String> response =
                        get("/api/rooms/" + room.getId() + "/change-logs", outsiderCookie);

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(response.body()).contains("FORBIDDEN");
            }
        }
    }

    private HttpResponse<InputStream> openStream(Long roomId, String cookie, String lastEventId)
            throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri(STREAM_PATH.formatted(roomId)))
                        .header("Accept", "text/event-stream")
                        .GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        if (lastEventId != null) {
            request.header("Last-Event-ID", lastEventId);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private static class SseReader implements AutoCloseable {

        private final InputStream inputStream;
        private final BufferedReader reader;
        private final java.util.concurrent.ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor();

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

    private String login(String email, String name) throws Exception {
        postJson(
                "/api/auth/signup",
                "{\"email\":\"" + email + "\",\"name\":\"" + name
                        + "\",\"password\":\"" + PASSWORD + "\"}",
                null);
        HttpResponse<String> response =
                postJson(
                        "/api/auth/login",
                        "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}",
                        null);
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    private HttpResponse<String> post(String path, String cookie) throws Exception {
        return postJson(path, "", cookie);
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body, String cookie)
            throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Room saveRoom() {
        LocalDate startDate = LocalDate.now(clock).plusDays(10);
        String inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return roomRepository.saveAndFlush(
                Room.create(
                        "실시간 활동 방",
                        "부산",
                        startDate,
                        startDate.plusDays(2),
                        inviteCode,
                        RoomStatus.SURVEYING,
                        clock));
    }

    private RoomMember join(Room room, User user) {
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }

    private void joinRoom(Room room, String memberName) throws Exception {
        String cookie = login(uniqueEmail(), memberName);
        HttpResponse<String> response =
                post("/api/rooms/invites/" + room.getInviteCode() + "/join", cookie);
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private void insertActivities(Long roomId, Long memberId, int count) {
        LocalDateTime createdAt = LocalDateTime.now(clock);
        jdbcTemplate.batchUpdate(
                "insert into change_log (plan_room_id, plan_room_member_id, entity_type,"
                        + " entity_id, event_kind, action_status, summary, created_at, modified_at)"
                        + " values (?, ?, 'MEMBER', ?, 'MEMBER_JOINED', 'CREATE', ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index)
                            throws SQLException {
                        statement.setLong(1, roomId);
                        statement.setLong(2, memberId);
                        statement.setLong(3, memberId);
                        statement.setString(4, "활동 " + index);
                        statement.setObject(5, createdAt);
                        statement.setObject(6, createdAt);
                    }

                    @Override
                    public int getBatchSize() {
                        return count;
                    }
                });
    }

    private long eventId(String event) {
        return event.lines()
                .filter(line -> line.startsWith("id:"))
                .map(line -> line.substring("id:".length()).trim())
                .mapToLong(Long::parseLong)
                .findFirst()
                .orElseThrow();
    }

    private LocalDateTime lastSeenAt(Long memberId) {
        return jdbcTemplate.queryForObject(
                "select last_seen_at from plan_room_member where id = ?",
                LocalDateTime.class,
                memberId);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

}
