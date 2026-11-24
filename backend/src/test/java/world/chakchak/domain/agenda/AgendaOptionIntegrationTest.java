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
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.entity.Vote;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.agenda.repository.AgendaOptionRepository;
import world.chakchak.domain.agenda.repository.VoteRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/**
 * V-07 예산 선지 추가, V-08 삭제를 실제 서버에 HTTP로 보내 요청 검증 → 방 잠금 → 선지·표 저장과 삭제가 DB에 반영되는지 확인한다.
 *
 * <p>서버는 실제 시각으로 동작하므로 출발일을 오늘 + 30일로 두고, 투표 자격이 생기도록 멤버를 안건보다 먼저 저장한다.
 */
@IntegrationTest
class AgendaOptionIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private AgendaOptionRepository agendaOptionRepository;
    @Autowired private VoteRepository voteRepository;
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
    void setUp() throws java.lang.Exception {
        String minsuEmail = UUID.randomUUID() + "@example.com";
        minsuCookie = login(minsuEmail);
        minsu = userRepository.findByEmail(minsuEmail).orElseThrow();
        String jieunEmail = UUID.randomUUID() + "@example.com";
        login(jieunEmail);
        jieun = userRepository.findByEmail(jieunEmail).orElseThrow();
    }

    // FK 순서대로 지운다. 선지가 멤버(proposed_by)를 참조하므로 멤버보다 먼저 지운다 (명세 13-3)
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
            jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room where id = ?", roomId);
        }
    }

    /** 민수와 지은이 멤버인 설문 조율 중 방. 멤버를 먼저 저장해야 이후에 여는 안건의 투표 자격이 생긴다. */
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
        return room;
    }

    /** 설문 응답에서 나온 60만원 선지가 하나 있는 예산 안건 */
    private AgendaItem budgetAgenda(Room room) {
        LocalDateTime now = LocalDateTime.now(clock);
        AgendaItem budget =
                agendaItemRepository.save(
                        AgendaItem.create(
                                room, AgendaSubject.BUDGET, 1, "안건 제목", 2, now, now.plusDays(3)));
        agendaOptionRepository.save(AgendaOption.budget(budget, 600_000L, 0, null));
        return budget;
    }

    private HttpResponse<String> addOption(Room room, AgendaItem agenda, String body)
            throws java.lang.Exception {
        return post(
                "/api/rooms/" + room.getId() + "/agendas/" + agenda.getId() + "/options",
                body,
                minsuCookie);
    }

    private HttpResponse<String> deleteOption(Room room, AgendaItem agenda, Long optionId)
            throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(
                                uri(
                                        "/api/rooms/"
                                                + room.getId()
                                                + "/agendas/"
                                                + agenda.getId()
                                                + "/options/"
                                                + optionId))
                        .header("Cookie", minsuCookie)
                        .DELETE()
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** 민수가 V-07로 올린 120만원 선지 */
    private AgendaOption minsusOption(AgendaItem budget) {
        return agendaOptionRepository.save(AgendaOption.budget(budget, 1_200_000L, 1, minsuMember));
    }

    private void voteFor(AgendaItem agenda, AgendaOption option, RoomMember member) {
        voteRepository.save(Vote.create(agenda, option, member, LocalDateTime.now(clock)));
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("예산 선지를 추가하면 201 OPTION_CREATED로 응답하고 반올림한 금액이 내가 올린 선지로 맨 뒤에 저장된다")
            void addsBudgetOption() throws java.lang.Exception {
                Room room = surveyingRoom();
                AgendaItem budget = budgetAgenda(room);

                HttpResponse<String> response = addOption(room, budget, "{\"amount\":1234567}");

                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.body())
                        .contains("\"code\":\"OPTION_CREATED\"")
                        .contains("선지를 추가했습니다.")
                        .contains("\"label\":\"123만원\"")
                        .contains("\"value\":\"1230000\"");
                List<AgendaOption> options =
                        agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(
                                budget.getId());
                assertThat(options).hasSize(2);
                AgendaOption added = options.get(1);
                assertThat(added.getValue()).isEqualTo("1230000");
                assertThat(added.getDisplayIndex()).isEqualTo(1);
                assertThat(added.getProposedBy().getUser().getId()).isEqualTo(minsu.getId());
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("amount가 없거나 범위를 벗어나면 400 INVALID_INPUT으로 응답하고 선지를 저장하지 않는다")
            void rejectsInvalidAmount() throws java.lang.Exception {
                Room room = surveyingRoom();
                AgendaItem budget = budgetAgenda(room);

                HttpResponse<String> missing = addOption(room, budget, "{}");
                HttpResponse<String> tooLarge = addOption(room, budget, "{\"amount\":100000000}");

                assertThat(missing.statusCode()).isEqualTo(400);
                assertThat(missing.body()).contains("\"code\":\"INVALID_INPUT\"");
                assertThat(tooLarge.statusCode()).isEqualTo(400);
                assertThat(tooLarge.body()).contains("\"code\":\"INVALID_INPUT\"");
                assertThat(
                                agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(
                                        budget.getId()))
                        .hasSize(1);
            }

            @Test
            @DisplayName("같은 회차에 두 번째 선지를 추가하면 409 OPTION_ADD_LIMIT_EXCEEDED로 응답한다")
            void rejectsSecondOption() throws java.lang.Exception {
                Room room = surveyingRoom();
                AgendaItem budget = budgetAgenda(room);
                addOption(room, budget, "{\"amount\":1000000}");

                HttpResponse<String> second = addOption(room, budget, "{\"amount\":1500000}");

                assertThat(second.statusCode()).isEqualTo(409);
                assertThat(second.body()).contains("\"code\":\"OPTION_ADD_LIMIT_EXCEEDED\"");
                assertThat(
                                agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(
                                        budget.getId()))
                        .hasSize(2);
            }
        }
    }

    @Nested
    class Delete {

        @Nested
        class Success {

            @Test
            @DisplayName("내가 올리고 나만 투표한 선지를 지우면 200 OPTION_DELETED로 응답하고 선지와 내 표가 사라져 다시 추가할 수 있다")
            void deletesMyOption() throws java.lang.Exception {
                Room room = surveyingRoom();
                AgendaItem budget = budgetAgenda(room);
                AgendaOption mine = minsusOption(budget);
                voteFor(budget, mine, minsuMember);

                HttpResponse<String> response = deleteOption(room, budget, mine.getId());

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .contains("\"code\":\"OPTION_DELETED\"")
                        .contains("선지를 삭제했습니다.")
                        .doesNotContain("120만원");
                assertThat(agendaOptionRepository.findById(mine.getId())).isEmpty();
                assertThat(voteRepository.countByAgendaItemId(budget.getId())).isZero();
                // 지운 뒤에는 이 회차에 다시 1개를 추가할 수 있다
                assertThat(addOption(room, budget, "{\"amount\":1500000}").statusCode())
                        .isEqualTo(201);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("다른 멤버가 투표한 선지를 지우면 409 OPTION_HAS_VOTES와 그 표 수로 응답하고 선지와 표가 그대로다")
            void rejectsOptionWithOthersVotes() throws java.lang.Exception {
                Room room = surveyingRoom();
                AgendaItem budget = budgetAgenda(room);
                AgendaOption mine = minsusOption(budget);
                voteFor(budget, mine, jieunMember);

                HttpResponse<String> response = deleteOption(room, budget, mine.getId());

                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(response.body())
                        .contains("\"code\":\"OPTION_HAS_VOTES\"")
                        .contains("\"voteCount\":1");
                assertThat(agendaOptionRepository.findById(mine.getId())).isPresent();
                assertThat(voteRepository.countByAgendaItemId(budget.getId())).isEqualTo(1);
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
