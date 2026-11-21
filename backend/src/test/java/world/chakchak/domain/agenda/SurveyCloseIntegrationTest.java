package world.chakchak.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDate;
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
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
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

/**
 * 5-1 설문 마감 처리를 실제 서버로 확인한다. 설문을 내지 않은 마지막 멤버가 방을 나가면(R-06) 설문이 마감되고, 같은 트랜잭션에서 1회차 안건이 열린다.
 *
 * <p>서버는 실제 시각으로 동작하므로 출발일을 오늘 + 30일로 둔다.
 */
@IntegrationTest
class SurveyCloseIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private SurveyResponseRepository surveyResponseRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Long> roomIds = new ArrayList<>();

    private String jieunCookie;
    private User minsu;
    private User jieun;

    @BeforeEach
    void setUp() throws Exception {
        String minsuEmail = UUID.randomUUID() + "@example.com";
        login(minsuEmail);
        minsu = userRepository.findByEmail(minsuEmail).orElseThrow();
        String jieunEmail = UUID.randomUUID() + "@example.com";
        jieunCookie = login(jieunEmail);
        jieun = userRepository.findByEmail(jieunEmail).orElseThrow();
    }

    // FK 순서대로 지운다. 안건과 결정된 선지가 서로를 참조하므로 그 연결을 먼저 끊는다 (명세 13-3)
    @AfterEach
    void tearDown() {
        for (Long roomId : roomIds) {
            String agendasOfRoom = "select id from agenda_item where plan_room_id = ?";
            String membersOfRoom = "select id from plan_room_member where plan_room_id = ?";
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
            jdbcTemplate.update(
                    "delete from survey_response where plan_room_member_id in ("
                            + membersOfRoom
                            + ")",
                    roomId);
            jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room where id = ?", roomId);
        }
    }

    /** 민수와 지은이 멤버인 설문 조율 중 방. 민수만 설문을 냈다. */
    private Room surveyingRoomOnlyMinsuAnswered() {
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
        RoomMember minsuMember = roomMemberRepository.save(RoomMember.create(room, minsu, clock));
        roomMemberRepository.save(RoomMember.create(room, jieun, clock));
        surveyResponseRepository.save(
                SurveyResponse.createForSurveying(
                        minsuMember,
                        600_000L,
                        StayType.HOTEL,
                        Pace.NORMAL,
                        List.of(ActivityCode.FOOD),
                        "없음",
                        clock));
        return room;
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("설문을 내지 않은 마지막 멤버가 나가면 설문이 마감되고 세 1회차 안건이 결정되어 방이 PLANNING이 된다")
            void opensFirstRoundsWhenLastNonRespondentLeaves() throws Exception {
                Room room = surveyingRoomOnlyMinsuAnswered();

                HttpResponse<String> response =
                        send(
                                HttpRequest.newBuilder(
                                                uri("/api/rooms/" + room.getId() + "/members/me"))
                                        .header("Cookie", jieunCookie)
                                        .DELETE());

                assertThat(response.statusCode()).isEqualTo(200);
                Room reloaded = roomRepository.findById(room.getId()).orElseThrow();
                assertThat(reloaded.getSurveyClosedAt()).isNotNull();
                assertThat(reloaded.getStatus()).isEqualTo(RoomStatus.PLANNING);
                for (AgendaSubject subject :
                        List.of(AgendaSubject.BUDGET, AgendaSubject.STAY, AgendaSubject.PACE)) {
                    AgendaItem agenda =
                            agendaItemRepository
                                    .findTopByRoomIdAndSubjectOrderByRoundDesc(
                                            room.getId(), subject)
                                    .orElseThrow();
                    assertThat(agenda.getRound()).isEqualTo(1);
                    assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                    assertThat(agenda.getTotalVoter()).isEqualTo(1);
                }
            }
        }
    }

    private String login(String email) throws Exception {
        post(
                "/api/auth/signup",
                "{\"email\":\"" + email + "\",\"name\":\"여행자\",\"password\":\"" + PASSWORD + "\"}");
        HttpResponse<String> login =
                post(
                        "/api/auth/login",
                        "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
        return login.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return send(
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
