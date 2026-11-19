package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

@IntegrationTest
@Import(RoomInviteApiIntegrationTest.TestClockConfiguration.class)
class RoomInviteApiIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private ChangeLogRepository changeLogRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MutableClock clock;
    @Autowired private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void startsPreviewRequestsInNewLimitWindow() {
        clock.advance(Duration.ofMinutes(1));
    }

    @Nested
    @TestClassOrder(ClassOrderer.OrderAnnotation.class)
    class Preview {

        @Nested
        @Order(1)
        class Success {

            @Test
            @DisplayName("초대 미리보기는 공개 정보와 사용자별 합류 가능 여부를 제공한다")
            void returnsPreviewForAnonymousAndLoggedInVisitors() throws java.lang.Exception {
                Room room = saveRoom("부산 연말 여행", RoomStatus.PLANNING);
                join(room, saveUser("서연"));
                HttpResponse<String> anonymousPreview = getPreview(room.getInviteCode(), null);

                assertThat(anonymousPreview.statusCode()).isEqualTo(200);
                JsonNode anonymousBody = objectMapper.readTree(anonymousPreview.body());
                assertThat(anonymousBody.path("success").booleanValue()).isTrue();
                assertThat(anonymousBody.path("code").stringValue())
                        .isEqualTo("INVITE_PREVIEW_FETCHED");
                assertThat(anonymousBody.has("message")).isFalse();

                JsonNode anonymousData = anonymousBody.path("data");
                assertThat(anonymousData.path("roomId").longValue()).isEqualTo(room.getId());
                assertThat(anonymousData.path("name").stringValue()).isEqualTo("부산 연말 여행");
                assertThat(anonymousData.path("destination").stringValue()).isEqualTo("부산");
                assertThat(anonymousData.path("startDate").stringValue())
                        .isEqualTo(room.getStartDate().toString());
                assertThat(anonymousData.path("endDate").stringValue())
                        .isEqualTo(room.getEndDate().toString());
                assertThat(anonymousData.path("status").stringValue()).isEqualTo("PLANNING");
                assertThat(anonymousData.path("memberCount").intValue()).isEqualTo(1);
                assertThat(anonymousData.path("joinable").booleanValue()).isFalse();
                assertThat(anonymousData.path("notJoinableReason").stringValue())
                        .isEqualTo("LOGIN_REQUIRED");
                assertThat(anonymousData.path("surveyRequired").booleanValue()).isTrue();
                assertThat(anonymousData.has("inviteCode")).isFalse();
                assertThat(anonymousData.has("members")).isFalse();

                Room historyRoom = saveRoom("초대할 여행", RoomStatus.SURVEYING);
                LoginSession activeSession = login("활동 멤버");
                join(historyRoom, userRepository.findByEmail(activeSession.email()).orElseThrow());

                LoginSession departedSession = login("탈퇴 멤버");
                RoomMember departedMember =
                        join(
                                historyRoom,
                                userRepository.findByEmail(departedSession.email()).orElseThrow());
                jdbcTemplate.update(
                        "update plan_room_member set left_at = ? where id = ?",
                        LocalDateTime.now(clock),
                        departedMember.getId());
                LoginSession newVisitorSession = login("새 방문자");

                HttpResponse<String> activePreview =
                        getPreview(historyRoom.getInviteCode(), activeSession.cookie());
                HttpResponse<String> departedPreview =
                        getPreview(historyRoom.getInviteCode(), departedSession.cookie());
                HttpResponse<String> newVisitorPreview =
                        getPreview(historyRoom.getInviteCode(), newVisitorSession.cookie());

                JsonNode activeData = objectMapper.readTree(activePreview.body()).path("data");
                JsonNode departedData = objectMapper.readTree(departedPreview.body()).path("data");
                JsonNode newVisitorData =
                        objectMapper.readTree(newVisitorPreview.body()).path("data");
                assertThat(activeData.path("joinable").booleanValue()).isFalse();
                assertThat(activeData.path("notJoinableReason").stringValue())
                        .isEqualTo("ALREADY_MEMBER");
                assertThat(departedData.path("joinable").booleanValue()).isFalse();
                assertThat(departedData.path("notJoinableReason").stringValue())
                        .isEqualTo("REJOIN_NOT_ALLOWED");
                assertThat(newVisitorData.path("joinable").booleanValue()).isTrue();
                assertThat(newVisitorData.path("notJoinableReason").isNull()).isTrue();
                assertThat(newVisitorData.path("memberCount").intValue()).isEqualTo(1);

                Room finalizedRoom = saveRoom("확정된 방", RoomStatus.FINALIZED);
                Room expiredRoom = saveRoom("만료된 방", RoomStatus.EXPIRED);
                HttpResponse<String> finalizedPreview =
                        getPreview(finalizedRoom.getInviteCode(), null);
                HttpResponse<String> expiredPreview = getPreview(expiredRoom.getInviteCode(), null);
                assertThat(
                                objectMapper
                                        .readTree(finalizedPreview.body())
                                        .path("data")
                                        .path("surveyRequired")
                                        .booleanValue())
                        .isFalse();
                assertThat(
                                objectMapper
                                        .readTree(expiredPreview.body())
                                        .path("data")
                                        .path("surveyRequired")
                                        .booleanValue())
                        .isTrue();
            }
        }

        @Nested
        @Order(2)
        class Exception {

            @Test
            @DisplayName("존재하지 않는 코드는 요청 횟수와 관계없이 404를 반환한다")
            void rejectsUnknownCodesWithoutRequestLimit() throws java.lang.Exception {
                HttpResponse<String> unknownCode = getPreview("MISSING1", null);
                assertThat(unknownCode.statusCode()).isEqualTo(404);
                assertThat(objectMapper.readTree(unknownCode.body()).path("code").stringValue())
                        .isEqualTo("INVITE_CODE_NOT_FOUND");

                Room validRoom = saveRoom("미리보기 여행", RoomStatus.PLANNING);
                for (int requestNumber = 0; requestNumber < 25; requestNumber++) {
                    boolean invalidCode = requestNumber % 2 == 0;
                    HttpResponse<String> response =
                            getPreview(invalidCode ? "NO-CODE1" : validRoom.getInviteCode(), null);
                    assertThat(response.statusCode()).isEqualTo(invalidCode ? 404 : 200);
                }
            }
        }
    }

    @Nested
    @TestClassOrder(ClassOrderer.OrderAnnotation.class)
    class Join {

        @Nested
        @Order(1)
        class Success {

            @Test
            @DisplayName("로그인 사용자가 초대 코드로 합류하면 멤버와 활동 기록이 함께 저장된다")
            void joinsRoomAndRecordsMemberActivity() throws java.lang.Exception {
                Room room = saveRoom("합류할 여행", RoomStatus.SURVEYING);
                LocalDateTime expiredDeadline = LocalDateTime.now(clock).minusMinutes(1);
                jdbcTemplate.update(
                        "update plan_room set opinion_deadline_at = ? where id = ?",
                        expiredDeadline,
                        room.getId());
                LoginSession session = login("서연");
                User user = userRepository.findByEmail(session.email()).orElseThrow();

                HttpResponse<String> response = postJoin(room.getInviteCode(), session.cookie());

                assertThat(response.statusCode()).isEqualTo(200);
                JsonNode body = objectMapper.readTree(response.body());
                assertThat(body.path("success").booleanValue()).isTrue();
                assertThat(body.path("code").stringValue()).isEqualTo("ROOM_JOINED");
                assertThat(body.path("message").stringValue()).isEqualTo("여행방에 합류했습니다.");
                JsonNode data = body.path("data");
                assertThat(data.path("roomId").longValue()).isEqualTo(room.getId());
                assertThat(data.path("surveyRequired").booleanValue()).isTrue();

                RoomMember member =
                        roomMemberRepository
                                .findByRoom_IdAndUser_Id(room.getId(), user.getId())
                                .orElseThrow();
                assertThat(data.path("memberId").longValue()).isEqualTo(member.getId());
                assertThat(member.getJoinedAt()).isEqualTo(member.getLastSeenAt());
                assertThat(member.getLeftAt()).isNull();
                assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                        .isEqualTo(RoomStatus.SURVEYING);
                assertThat(
                                roomRepository
                                        .findById(room.getId())
                                        .orElseThrow()
                                        .getOpinionDeadlineAt())
                        .isEqualTo(expiredDeadline);

                List<ChangeLog> logs =
                        changeLogRepository
                                .findAllByRoom_IdOrderByIdDesc(
                                        room.getId(),
                                        org.springframework.data.domain.PageRequest.of(0, 10))
                                .getContent();
                assertThat(logs).hasSize(1);
                ChangeLog log = logs.get(0);
                assertThat(log.getEntityType()).isEqualTo(EntityType.MEMBER);
                assertThat(log.getEntityId()).isEqualTo(member.getId());
                assertThat(log.getEventKind()).isEqualTo(EventKind.MEMBER_JOINED);
                assertThat(log.getActionStatus()).isEqualTo(ActionStatus.CREATE);
                assertThat(log.getSummary()).isEqualTo("서연님이 합류했어요");
                assertThat(log.getActor().getId()).isEqualTo(member.getId());
            }

            @Test
            @DisplayName("각 방 상태에 합류해도 상태를 유지하고 확정 방만 설문이 필요하지 않다")
            void preservesRoomStatusAndReportsSurveyRequirement() throws java.lang.Exception {
                for (RoomStatus status :
                        List.of(
                                RoomStatus.SURVEYING,
                                RoomStatus.PLANNING,
                                RoomStatus.REVIEWING,
                                RoomStatus.FINALIZED)) {
                    Room room = saveRoom("상태 확인 " + status, status);
                    LoginSession session = login("멤버 " + status);

                    HttpResponse<String> response =
                            postJoin(room.getInviteCode(), session.cookie());

                    JsonNode data = objectMapper.readTree(response.body()).path("data");
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(data.path("surveyRequired").booleanValue())
                            .isEqualTo(status != RoomStatus.FINALIZED);
                    assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                            .isEqualTo(status);
                }
            }

            @Test
            @DisplayName("검토 중인 방에 합류한 멤버는 기존 안건에는 부적격이고 이후 안건부터 적격이다")
            void appliesExistingAgendaEligibilityRulesAfterJoining() throws java.lang.Exception {
                Room room = saveRoom("안건 자격 여행", RoomStatus.REVIEWING);
                AgendaItem existingAgenda = saveAgenda(room, AgendaSubject.BUDGET);
                clock.advance(Duration.ofSeconds(1));
                LoginSession session = login("새 안건 멤버");
                HttpResponse<String> joined = postJoin(room.getInviteCode(), session.cookie());
                assertThat(joined.statusCode()).isEqualTo(200);

                HttpResponse<String> existingResponse =
                        getAgenda(room.getId(), existingAgenda.getId(), session.cookie());
                assertThat(
                                objectMapper
                                        .readTree(existingResponse.body())
                                        .path("data")
                                        .path("eligible")
                                        .booleanValue())
                        .isFalse();

                AgendaItem laterAgenda = saveAgenda(room, AgendaSubject.PACE);
                HttpResponse<String> laterResponse =
                        getAgenda(room.getId(), laterAgenda.getId(), session.cookie());
                assertThat(
                                objectMapper
                                        .readTree(laterResponse.body())
                                        .path("data")
                                        .path("eligible")
                                        .booleanValue())
                        .isTrue();
            }

            @Test
            @DisplayName("동시에 합류 요청을 보내도 멤버와 합류 기록은 한 건만 생성된다")
            void acceptsOnlyOneConcurrentJoinRequest() throws java.lang.Exception {
                Room room = saveRoom("동시 합류 여행", RoomStatus.PLANNING);
                LoginSession session = login("동시 합류 멤버");
                HttpRequest request = joinRequest(room.getInviteCode(), session.cookie());

                CompletableFuture<HttpResponse<String>> first =
                        client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                CompletableFuture<HttpResponse<String>> second =
                        client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                CompletableFuture.allOf(first, second).join();
                List<HttpResponse<String>> responses = List.of(first.join(), second.join());

                assertThat(responses.stream().map(HttpResponse::statusCode).toList())
                        .containsExactlyInAnyOrder(200, 409);
                HttpResponse<String> rejected =
                        responses.stream()
                                .filter(response -> response.statusCode() == 409)
                                .findFirst()
                                .orElseThrow();
                assertThat(objectMapper.readTree(rejected.body()).path("code").stringValue())
                        .isEqualTo("ALREADY_MEMBER");
                assertThat(roomMemberRepository.countByRoom_IdAndLeftAtIsNull(room.getId()))
                        .isEqualTo(1);
                assertThat(
                                changeLogRepository.findAllByRoom_IdOrderByIdDesc(
                                        room.getId(),
                                        org.springframework.data.domain.PageRequest.of(0, 10)))
                        .hasSize(1);
            }
        }

        @Nested
        @Order(2)
        class Exception {

            @Test
            @DisplayName("비로그인 요청은 거절하고 존재하지 않는 초대 코드는 찾을 수 없음으로 응답한다")
            void rejectsAnonymousAndUnknownInvite() throws java.lang.Exception {
                HttpResponse<String> anonymous = postJoin("MISSING1", null);
                assertThat(anonymous.statusCode()).isEqualTo(401);
                assertThat(objectMapper.readTree(anonymous.body()).path("code").stringValue())
                        .isEqualTo("UNAUTHORIZED");

                LoginSession session = login("신규 멤버");
                HttpResponse<String> unknown = postJoin("MISSING1", session.cookie());
                assertThat(unknown.statusCode()).isEqualTo(404);
                assertThat(objectMapper.readTree(unknown.body()).path("code").stringValue())
                        .isEqualTo("INVITE_CODE_NOT_FOUND");
            }

            @Test
            @DisplayName("활동 멤버와 탈퇴 이력이 있는 사용자는 합류할 수 없다")
            void rejectsAlreadyActiveAndPreviouslyDepartedMember() throws java.lang.Exception {
                Room room = saveRoom("멤버 제한 여행", RoomStatus.PLANNING);
                LoginSession activeSession = login("활동 멤버");
                LoginSession departedSession = login("탈퇴 멤버");
                User activeUser = userRepository.findByEmail(activeSession.email()).orElseThrow();
                User departedUser =
                        userRepository.findByEmail(departedSession.email()).orElseThrow();
                join(room, activeUser);
                RoomMember departedMember = join(room, departedUser);
                jdbcTemplate.update(
                        "update plan_room_member set left_at = ? where id = ?",
                        LocalDateTime.now(clock),
                        departedMember.getId());

                HttpResponse<String> active =
                        postJoin(room.getInviteCode(), activeSession.cookie());
                HttpResponse<String> departed =
                        postJoin(room.getInviteCode(), departedSession.cookie());

                assertThat(active.statusCode()).isEqualTo(409);
                assertThat(objectMapper.readTree(active.body()).path("code").stringValue())
                        .isEqualTo("ALREADY_MEMBER");
                assertThat(departed.statusCode()).isEqualTo(409);
                assertThat(objectMapper.readTree(departed.body()).path("code").stringValue())
                        .isEqualTo("REJOIN_NOT_ALLOWED");
                assertThat(
                                roomMemberRepository
                                        .findByRoom_IdAndUser_Id(room.getId(), departedUser.getId())
                                        .orElseThrow()
                                        .getId())
                        .isEqualTo(departedMember.getId());
                assertThat(
                                changeLogRepository.findAllByRoom_IdOrderByIdDesc(
                                        room.getId(),
                                        org.springframework.data.domain.PageRequest.of(0, 10)))
                        .isEmpty();
            }
        }
    }

    private LoginSession login(String name) throws java.lang.Exception {
        String email = uniqueEmail();
        post(
                "/api/auth/signup",
                "{\"email\":\""
                        + email
                        + "\",\"name\":\""
                        + name
                        + "\",\"password\":\""
                        + PASSWORD
                        + "\"}");
        HttpResponse<String> response =
                post(
                        "/api/auth/login",
                        "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
        String cookie =
                response.headers().allValues("Set-Cookie").stream()
                        .filter(value -> value.startsWith("SESSION="))
                        .map(value -> value.split(";", 2)[0])
                        .findFirst()
                        .orElseThrow();
        return new LoginSession(email, cookie);
    }

    private HttpResponse<String> post(String path, String body) throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getPreview(String inviteCode, String cookie)
            throws java.lang.Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri("/api/rooms/invites/" + inviteCode)).GET();
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJoin(String inviteCode, String cookie)
            throws java.lang.Exception {
        return client.send(joinRequest(inviteCode, cookie), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest joinRequest(String inviteCode, String cookie) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri("/api/rooms/invites/" + inviteCode + "/join"))
                        .POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return builder.build();
    }

    private HttpResponse<String> getAgenda(Long roomId, Long agendaId, String cookie)
            throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(uri("/api/rooms/" + roomId + "/agendas/" + agendaId))
                        .header("Cookie", cookie)
                        .GET()
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private Room saveRoom(String name, RoomStatus status) {
        String inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        LocalDate startDate = LocalDate.now(clock).plusDays(10);
        return roomRepository.saveAndFlush(
                Room.create(
                        name, "부산", startDate, startDate.plusDays(2), inviteCode, status, clock));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private User saveUser(String name) {
        return userRepository.saveAndFlush(User.create(uniqueEmail(), name, "hashed-password"));
    }

    private RoomMember join(Room room, User user) {
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }

    private AgendaItem saveAgenda(Room room, AgendaSubject subject) {
        LocalDateTime now = LocalDateTime.now(clock);
        return agendaItemRepository.saveAndFlush(
                AgendaItem.create(room, subject, 1, subject.name(), 1, now, now.plusDays(1)));
    }

    private record LoginSession(String email, String cookie) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-11-19T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    static class MutableClock extends Clock {

        private final AtomicReference<Instant> currentInstant;
        private final ZoneId zone;

        MutableClock(Instant currentInstant, ZoneId zone) {
            this.currentInstant = new AtomicReference<>(currentInstant);
            this.zone = zone;
        }

        void advance(Duration duration) {
            currentInstant.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant(), zone);
        }

        @Override
        public Instant instant() {
            return currentInstant.get();
        }
    }
}
