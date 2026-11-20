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
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/**
 * V-04 다음 회차 열기를 실제 서버에 HTTP로 보내 방 잠금 → 다음 회차 저장 → 설문 답과 직전 회차 추가 선지로 선지 생성이 DB에 반영되는지 확인한다.
 *
 * <p>서버는 실제 시각으로 동작하므로 출발일을 오늘 + 30일로 두고, 투표 자격이 생기도록 멤버를 안건보다 먼저 저장한다.
 */
@IntegrationTest
class AgendaNextRoundIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private AgendaOptionRepository agendaOptionRepository;
    @Autowired private SurveyResponseRepository surveyResponseRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Long> roomIds = new ArrayList<>();

    private String minsuCookie;
    private User minsu;
    private User jieun;

    // surveyingRoom()이 저장한 방 멤버
    private RoomMember minsuMember;
    private RoomMember jieunMember;

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
            jdbcTemplate.update(
                    "delete from survey_response where plan_room_member_id in ("
                            + membersOfRoom
                            + ")",
                    roomId);
            jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room where id = ?", roomId);
        }
    }

    /** 민수와 지은이 멤버인 설문 조율 중 방. 둘 다 설문을 냈다(민수 60만원, 지은 100만원). */
    private Room surveyingRoom() {
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
        minsuMember = roomMemberRepository.save(RoomMember.create(room, minsu, clock));
        jieunMember = roomMemberRepository.save(RoomMember.create(room, jieun, clock));
        surveyResponseRepository.save(answer(minsuMember, 600_000L));
        surveyResponseRepository.save(answer(jieunMember, 1_000_000L));
        return room;
    }

    private SurveyResponse answer(RoomMember member, long budget) {
        return SurveyResponse.createForSurveying(
                member,
                budget,
                StayType.HOTEL,
                Pace.NORMAL,
                List.of(ActivityCode.FOOD),
                "없음",
                clock);
    }

    /** 미결 마감된 1회차 예산 안건. 설문 선지 2개와 지은이 추가한 80만원 선지가 있다. */
    private AgendaItem expiredBudgetAgenda(Room room) {
        LocalDateTime now = LocalDateTime.now(clock);
        AgendaItem budget =
                AgendaItem.create(
                        room, AgendaSubject.BUDGET, 1, "예산을 정해주세요", 2, now, now.plusDays(3));
        budget.expire();
        agendaItemRepository.save(budget);
        agendaOptionRepository.save(AgendaOption.budget(budget, 600_000L, 0, null));
        agendaOptionRepository.save(AgendaOption.budget(budget, 1_000_000L, 1, null));
        agendaOptionRepository.save(AgendaOption.budget(budget, 800_000L, 2, jieunMember));
        return budget;
    }

    private HttpResponse<String> openNextRound(Room room, AgendaItem agenda) throws Exception {
        return post(
                "/api/rooms/" + room.getId() + "/agendas/" + agenda.getId() + "/next-round",
                "",
                minsuCookie);
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("미결 마감된 예산 안건의 다음 회차를 열면 201로 응답하고 2회차가 설문 금액과 직전 추가 선지로 저장된다")
            void opensNextRound() throws Exception {
                Room room = surveyingRoom();
                AgendaItem previous = expiredBudgetAgenda(room);

                HttpResponse<String> response = openNextRound(room, previous);

                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.body())
                        .contains("\"code\":\"AGENDA_ROUND_OPENED\"")
                        .contains("다음 회차 투표를 열었습니다.")
                        .contains("\"round\":2");
                AgendaItem next =
                        agendaItemRepository
                                .findTopByRoomIdAndSubjectOrderByRoundDesc(
                                        room.getId(), AgendaSubject.BUDGET)
                                .orElseThrow();
                assertThat(next.getRound()).isEqualTo(2);
                assertThat(next.getStatus()).isEqualTo(AgendaStatus.VOTING);
                assertThat(next.getTotalVoter()).isEqualTo(2);
                List<AgendaOption> options =
                        agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(
                                next.getId());
                assertThat(options)
                        .extracting(AgendaOption::getValue)
                        .containsExactly("600000", "800000", "1000000");
                assertThat(options.get(1).getProposedBy().getId()).isEqualTo(jieunMember.getId());
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("이미 다음 회차를 연 안건을 다시 열면 409 AGENDA_ALREADY_REOPENED와 새 회차 id로 응답한다")
            void rejectsAlreadyReopened() throws Exception {
                Room room = surveyingRoom();
                AgendaItem previous = expiredBudgetAgenda(room);
                openNextRound(room, previous);
                Long nextId =
                        agendaItemRepository
                                .findTopByRoomIdAndSubjectOrderByRoundDesc(
                                        room.getId(), AgendaSubject.BUDGET)
                                .orElseThrow()
                                .getId();

                HttpResponse<String> second = openNextRound(room, previous);

                assertThat(second.statusCode()).isEqualTo(409);
                assertThat(second.body())
                        .contains("\"code\":\"AGENDA_ALREADY_REOPENED\"")
                        .contains("\"agendaId\":" + nextId);
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
