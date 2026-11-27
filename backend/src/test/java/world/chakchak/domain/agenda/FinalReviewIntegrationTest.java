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
 * C-01 최종 확정 투표 열기를 실제 서버에 HTTP로 보내 방 잠금 → FINAL_PLAN 안건·선지 저장 → 요청자 동의 표 → 방 REVIEWING이 DB에 반영되는지
 * 확인한다.
 *
 * <p>서버는 실제 시각으로 동작하므로 출발일을 오늘 + 30일로 둔다.
 */
@IntegrationTest
class FinalReviewIntegrationTest {

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
    private User minsu;
    private User jieun;

    @BeforeEach
    void setUp() throws Exception {
        String minsuEmail = UUID.randomUUID() + "@example.com";
        minsuCookie = login(minsuEmail);
        minsu = userRepository.findByEmail(minsuEmail).orElseThrow();
        String jieunEmail = UUID.randomUUID() + "@example.com";
        login(jieunEmail);
        jieun = userRepository.findByEmail(jieunEmail).orElseThrow();
    }

    // FK 순서대로 지운다. 안건과 결정된 선지가 서로를 참조하므로 그 연결을 먼저 끊는다 (명세 13-3)
    @AfterEach
    void tearDown() {
        for (Long roomId : roomIds) {
            String agendasOfRoom = "select id from agenda_item where plan_room_id = ?";
            jdbcTemplate.update(
                    "delete from vote where agenda_id in (" + agendasOfRoom + ")", roomId);
            jdbcTemplate.update(
                    "update agenda_item set resolved_option_id = null, resolved_at = null,"
                            + " status = 'EXPIRED' where plan_room_id = ?",
                    roomId);
            jdbcTemplate.update(
                    "delete from agenda_option where agenda_id in (" + agendasOfRoom + ")", roomId);
            jdbcTemplate.update("delete from agenda_item where plan_room_id = ?", roomId);
            // 최종 확정을 연 멤버가 활동 기록의 행위자로 남으므로 멤버보다 먼저 지운다
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

    private HttpResponse<String> openFinalReview(Room room) throws Exception {
        return post("/api/rooms/" + room.getId() + "/final-review", "", minsuCookie);
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("PLANNING 방에서 최종 확정 투표를 열면 201로 응답하고 안건·선지·내 동의 표가 저장되며 방이 REVIEWING이 된다")
            void opensFinalReview() throws Exception {
                Room room = roomIn(RoomStatus.PLANNING);

                HttpResponse<String> response = openFinalReview(room);

                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.body())
                        .contains("\"code\":\"FINAL_REVIEW_OPENED\"")
                        .contains("최종 확정 투표를 열었습니다.")
                        .contains("\"subject\":\"FINAL_PLAN\"")
                        .contains("\"round\":1")
                        .contains("\"votedCount\":1");
                AgendaItem agenda =
                        agendaItemRepository
                                .findTopByRoomIdAndSubjectOrderByRoundDesc(
                                        room.getId(), AgendaSubject.FINAL_PLAN)
                                .orElseThrow();
                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.VOTING);
                assertThat(agenda.getTotalVoter()).isEqualTo(2);
                assertThat(
                                agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(
                                        agenda.getId()))
                        .extracting(AgendaOption::getValue)
                        .containsExactly("AGREE", "REJECT");
                assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                        .isEqualTo(RoomStatus.REVIEWING);
            }

            @Test
            @DisplayName("최종 확정 투표를 열면 투표 중인 자유 안건은 미결로 끝나고 이미 정해진 자유 안건은 그대로다")
            void expiresVotingCustomAgendas() throws Exception {
                Room room = roomIn(RoomStatus.PLANNING);
                LocalDateTime now = LocalDateTime.now(clock);
                AgendaItem voting =
                        agendaItemRepository.save(AgendaItem.openCustom(room, "렌터카 빌릴까?", 2, now));
                agendaOptionRepository.saveAll(
                        AgendaOption.customOptions(voting, List.of("빌린다", "안 빌린다")));
                AgendaItem resolved =
                        agendaItemRepository.save(AgendaItem.openCustom(room, "조식 포함할까?", 2, now));
                List<AgendaOption> options =
                        agendaOptionRepository.saveAll(
                                AgendaOption.customOptions(resolved, List.of("포함", "미포함")));
                resolved.resolve(options.getFirst(), now);
                agendaItemRepository.save(resolved);

                HttpResponse<String> response = openFinalReview(room);

                assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
                assertThat(agendaItemRepository.findById(voting.getId()).orElseThrow().getStatus())
                        .isEqualTo(AgendaStatus.EXPIRED);
                assertThat(
                                agendaItemRepository
                                        .findById(resolved.getId())
                                        .orElseThrow()
                                        .getStatus())
                        .isEqualTo(AgendaStatus.RESOLVED);
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("설문 조율 중인 방에서 열면 409 INVALID_STATUS로 응답하고 안건을 만들지 않는다")
            void rejectsSurveyingRoom() throws Exception {
                Room room = roomIn(RoomStatus.SURVEYING);

                HttpResponse<String> response = openFinalReview(room);

                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(response.body()).contains("\"code\":\"INVALID_STATUS\"");
                assertThat(
                                agendaItemRepository.findTopByRoomIdAndSubjectOrderByRoundDesc(
                                        room.getId(), AgendaSubject.FINAL_PLAN))
                        .isEmpty();
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
}
