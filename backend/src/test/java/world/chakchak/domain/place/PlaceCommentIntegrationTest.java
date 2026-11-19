package world.chakchak.domain.place;

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
import world.chakchak.domain.comment.repository.CommentRepository;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/** P-08·P-09 장소 코멘트를 실제 서버에 HTTP로 보내 요청 검증, 방 단계 확인, 다른 방 장소 차단, 저장과 목록 순서를 확인한다. */
@IntegrationTest
class PlaceCommentIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
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

    // FK 순서대로 지운다
    @AfterEach
    void tearDown() {
        for (Long roomId : roomIds) {
            String placesOfRoom = "select id from place_candidate where plan_room_id = ?";
            jdbcTemplate.update(
                    "delete from comment where place_candidate_id in (" + placesOfRoom + ")",
                    roomId);
            jdbcTemplate.update(
                    "delete from place_reaction where place_candidate_id in (" + placesOfRoom + ")",
                    roomId);
            jdbcTemplate.update("delete from place_candidate where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room where id = ?", roomId);
        }
    }

    /** 민수와 지은이 멤버인 방. 민수가 올린 장소 후보 하나를 함께 돌려준다. */
    private PlaceCandidate placeInRoom(RoomStatus status) {
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
        RoomMember suggester = roomMemberRepository.save(RoomMember.create(room, minsu, clock));
        roomMemberRepository.save(RoomMember.create(room, jieun, clock));
        return placeCandidateRepository.save(
                PlaceCandidate.create(
                        room,
                        "place-" + UUID.randomUUID(),
                        "광장시장",
                        "market",
                        "서울 종로구",
                        "https://maps.google.com/?cid=1",
                        null,
                        suggester));
    }

    private static String commentsPath(Long roomId, PlaceCandidate place) {
        return "/api/rooms/" + roomId + "/place-candidates/" + place.getId() + "/comments";
    }

    private static String commentsPath(PlaceCandidate place) {
        return commentsPath(place.getRoom().getId(), place);
    }

    private static String commentBody(String body) {
        return "{\"body\":\"" + body + "\"}";
    }

    private List<?> savedComments(PlaceCandidate place) {
        return commentRepository.findAllByPlaceCandidateIdOrderByIdAsc(place.getId());
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("장소 코멘트를 남기면 201 COMMENT_CREATED로 응답하고 목록에서 오래된 순으로 보인다")
            void createsAndListsInOrder() throws java.lang.Exception {
                PlaceCandidate place = placeInRoom(RoomStatus.PLANNING);

                HttpResponse<String> first =
                        post(commentsPath(place), commentBody("줄이 길대요"), minsuCookie);
                HttpResponse<String> second =
                        post(commentsPath(place), commentBody("아침에 가면 돼요"), jieunCookie);
                HttpResponse<String> list = get(commentsPath(place), minsuCookie);

                assertThat(first.statusCode()).isEqualTo(201);
                assertThat(first.body())
                        .contains("\"code\":\"COMMENT_CREATED\"")
                        .contains("\"body\":\"줄이 길대요\"")
                        .contains("\"name\":\"여행자\"");
                assertThat(second.statusCode()).isEqualTo(201);
                assertThat(list.statusCode()).isEqualTo(200);
                assertThat(list.body()).contains("\"code\":\"COMMENTS_FETCHED\"");
                assertThat(list.body().indexOf("줄이 길대요"))
                        .isLessThan(list.body().indexOf("아침에 가면 돼요"));
                assertThat(savedComments(place)).hasSize(2);
            }

            @Test
            @DisplayName("이미 확정된 장소에도 코멘트를 남길 수 있다")
            void createsOnConfirmedPlace() throws java.lang.Exception {
                PlaceCandidate place = placeInRoom(RoomStatus.PLANNING);
                place.confirm(clock);
                placeCandidateRepository.save(place);

                HttpResponse<String> response =
                        post(commentsPath(place), commentBody("확정 좋아요"), minsuCookie);

                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(savedComments(place)).hasSize(1);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("본문이 비었거나 500자를 넘으면 400 INVALID_INPUT으로 응답하고 저장하지 않는다")
            void rejectsInvalidBody() throws java.lang.Exception {
                PlaceCandidate place = placeInRoom(RoomStatus.PLANNING);

                HttpResponse<String> blank =
                        post(commentsPath(place), commentBody("  "), minsuCookie);
                HttpResponse<String> tooLong =
                        post(commentsPath(place), commentBody("가".repeat(501)), minsuCookie);

                assertThat(blank.statusCode()).isEqualTo(400);
                assertThat(blank.body()).contains("\"code\":\"INVALID_INPUT\"");
                assertThat(tooLong.statusCode()).isEqualTo(400);
                assertThat(tooLong.body()).contains("\"code\":\"INVALID_INPUT\"");
                assertThat(savedComments(place)).isEmpty();
            }

            @Test
            @DisplayName("확정된 방에서는 409 INVALID_STATUS로 응답하고 저장하지 않는다")
            void rejectsFinalizedRoom() throws java.lang.Exception {
                PlaceCandidate place = placeInRoom(RoomStatus.FINALIZED);

                HttpResponse<String> response =
                        post(commentsPath(place), commentBody("늦었나요"), minsuCookie);

                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(response.body()).contains("\"code\":\"INVALID_STATUS\"");
                assertThat(savedComments(place)).isEmpty();
            }

            @Test
            @DisplayName("다른 방의 장소에 코멘트를 남기면 404 PLACE_CANDIDATE_NOT_FOUND로 응답하고 저장하지 않는다")
            void rejectsPlaceOfOtherRoom() throws java.lang.Exception {
                PlaceCandidate myPlace = placeInRoom(RoomStatus.PLANNING);
                PlaceCandidate otherPlace = placeInRoom(RoomStatus.PLANNING);

                HttpResponse<String> response =
                        post(
                                commentsPath(myPlace.getRoom().getId(), otherPlace),
                                commentBody("여기 맞나요"),
                                minsuCookie);

                assertThat(response.statusCode()).isEqualTo(404);
                assertThat(response.body()).contains("\"code\":\"PLACE_CANDIDATE_NOT_FOUND\"");
                assertThat(savedComments(otherPlace)).isEmpty();
            }
        }
    }

    @Nested
    class Read {

        @Nested
        class Exception {

            @Test
            @DisplayName("다른 방의 장소 코멘트 목록을 조회하면 404 PLACE_CANDIDATE_NOT_FOUND로 응답한다")
            void rejectsPlaceOfOtherRoom() throws java.lang.Exception {
                PlaceCandidate myPlace = placeInRoom(RoomStatus.PLANNING);
                PlaceCandidate otherPlace = placeInRoom(RoomStatus.PLANNING);

                HttpResponse<String> response =
                        get(commentsPath(myPlace.getRoom().getId(), otherPlace), minsuCookie);

                assertThat(response.statusCode()).isEqualTo(404);
                assertThat(response.body()).contains("\"code\":\"PLACE_CANDIDATE_NOT_FOUND\"");
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
