package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.place.dto.GooglePlace;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.place.service.PlaceSearchClient;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/** P-02 장소 후보 등록, P-03 칸반 보드 API를 HTTP로 호출한다. Google은 PlaceSearchClient를 가짜로 바꿔 부르지 않는다. */
@IntegrationTest
class PlaceCandidateApiIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private Clock clock;

    @MockitoBean private PlaceSearchClient placeSearchClient;

    private final HttpClient client = HttpClient.newHttpClient();

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("방 멤버가 장소를 올리면 201과 PLACE_CANDIDATE_CREATED, 칸반 카드가 반환된다")
            void registersPlace() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);
                given(placeSearchClient.findById("place-1"))
                        .willReturn(Optional.of(googlePlace("place-1")));

                HttpResponse<String> response = register(roomId, "place-1", cookie);

                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.body())
                        .contains("\"success\":true")
                        .contains("\"code\":\"PLACE_CANDIDATE_CREATED\"")
                        .contains("장소 후보를 올렸습니다.")
                        .contains("\"googlePlaceId\":\"place-1\"")
                        .contains("\"name\":\"광장시장\"")
                        .contains("\"stage\":\"FULL\"")
                        .contains("\"agreeCount\":1")
                        .contains("\"nopeCount\":0")
                        .contains("\"nopedBy\":[]")
                        .contains("\"name\":\"여행자\"");
                assertThat(placeCandidateRepository.countByRoom(room(roomId))).isEqualTo(1);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("googlePlaceId가 비어 있으면 400 INVALID_INPUT이고 Google을 부르지 않는다")
            void rejectsBlankGooglePlaceId() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);

                HttpResponse<String> response = register(roomId, " ", cookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("\"code\":\"INVALID_INPUT\"");
                verify(placeSearchClient, never()).findById(anyString());
            }

            @Test
            @DisplayName("방 멤버가 아니면 403 FORBIDDEN이고 아무것도 저장하지 않는다")
            void rejectsNonMember() throws java.lang.Exception {
                long roomId = createRoom(login());
                String outsider = login();
                given(placeSearchClient.findById("place-1"))
                        .willReturn(Optional.of(googlePlace("place-1")));

                HttpResponse<String> response = register(roomId, "place-1", outsider);

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"");
                assertThat(placeCandidateRepository.countByRoom(room(roomId))).isZero();
            }

            @Test
            @DisplayName("일정 조율(PLANNING) 중이 아닌 방이면 409 INVALID_STATUS이고 아무것도 저장하지 않는다")
            void rejectsRoomNotPlanning() throws java.lang.Exception {
                String cookie = login();
                long roomId = createSurveyingRoom(cookie);
                given(placeSearchClient.findById("place-1"))
                        .willReturn(Optional.of(googlePlace("place-1")));

                HttpResponse<String> response = register(roomId, "place-1", cookie);

                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(response.body()).contains("\"code\":\"INVALID_STATUS\"");
                assertThat(placeCandidateRepository.countByRoom(room(roomId))).isZero();
            }

            @Test
            @DisplayName("Google에 없는 장소면 404 PLACE_NOT_FOUND이고 아무것도 저장하지 않는다")
            void rejectsUnknownPlace() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);
                given(placeSearchClient.findById("missing")).willReturn(Optional.empty());

                HttpResponse<String> response = register(roomId, "missing", cookie);

                assertThat(response.statusCode()).isEqualTo(404);
                assertThat(response.body()).contains("\"code\":\"PLACE_NOT_FOUND\"");
                assertThat(placeCandidateRepository.countByRoom(room(roomId))).isZero();
            }

            @Test
            @DisplayName("이미 올라온 장소면 409 PLACE_ALREADY_REGISTERED와 기존 후보의 placeId를 돌려준다")
            void rejectsDuplicate() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);
                given(placeSearchClient.findById("place-1"))
                        .willReturn(Optional.of(googlePlace("place-1")));
                long placeId = placeIdFrom(register(roomId, "place-1", cookie).body());

                HttpResponse<String> response = register(roomId, "place-1", cookie);

                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(response.body())
                        .contains("\"code\":\"PLACE_ALREADY_REGISTERED\"")
                        .contains("\"data\":{\"placeId\":" + placeId + "}");
            }
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("방 멤버가 보드를 열면 200과 PLACE_CANDIDATES_FETCHED, 인원·한도·카드가 반환된다")
            void returnsBoard() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);
                given(placeSearchClient.findById("place-1"))
                        .willReturn(Optional.of(googlePlace("place-1")));
                long placeId = placeIdFrom(register(roomId, "place-1", cookie).body());

                HttpResponse<String> response = getBoard(roomId, cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .contains("\"success\":true")
                        .contains("\"code\":\"PLACE_CANDIDATES_FETCHED\"")
                        .contains("\"activeMemberCount\":1")
                        .contains("\"limit\":30")
                        .contains("\"placeId\":" + placeId)
                        .contains("\"stage\":\"FULL\"");
            }

            @Test
            @DisplayName("방 멤버가 카드 상세를 열면 200과 PLACE_CANDIDATE_FETCHED, 멤버별 반응이 반환된다")
            void returnsDetail() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);
                given(placeSearchClient.findById("place-1"))
                        .willReturn(Optional.of(googlePlace("place-1")));
                long placeId = placeIdFrom(register(roomId, "place-1", cookie).body());

                HttpResponse<String> response = get(roomId, "/" + placeId, cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .contains("\"code\":\"PLACE_CANDIDATE_FETCHED\"")
                        .contains("\"placeId\":" + placeId)
                        .contains("\"category\":{\"value\":\"SHOPPING\",\"label\":\"쇼핑·투어\"}")
                        .contains("\"reaction\":{\"value\":\"WANT\",\"label\":\"가고 싶어요\"}")
                        .contains("\"commentCount\":0");
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("방 멤버가 아니면 403 FORBIDDEN이다")
            void rejectsNonMember() throws java.lang.Exception {
                long roomId = createRoom(login());

                HttpResponse<String> response = getBoard(roomId, login());

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"");
            }

            @Test
            @DisplayName("없는 후보 상세를 열면 404 PLACE_CANDIDATE_NOT_FOUND다")
            void detailRejectsUnknownCandidate() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);

                HttpResponse<String> response = get(roomId, "/999999", cookie);

                assertThat(response.statusCode()).isEqualTo(404);
                assertThat(response.body()).contains("\"code\":\"PLACE_CANDIDATE_NOT_FOUND\"");
            }
        }
    }

    @Nested
    class React {

        @Nested
        class Success {

            @Test
            @DisplayName("별로예요를 누르면 200과 PLACE_REACTION_SAVED, 내 반응이 들어간 상세가 반환된다")
            void savesReaction() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);
                given(placeSearchClient.findById("place-1"))
                        .willReturn(Optional.of(googlePlace("place-1")));
                long placeId = placeIdFrom(register(roomId, "place-1", cookie).body());

                HttpResponse<String> response =
                        send("PUT", roomId, placeId, "{\"reaction\":\"NOPE\"}", cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .contains("\"code\":\"PLACE_REACTION_SAVED\"")
                        .contains("\"reaction\":{\"value\":\"NOPE\",\"label\":\"별로예요\"}")
                        .contains("\"agreeCount\":0");
            }

            @Test
            @DisplayName("반응을 지우면 200과 PLACE_REACTION_DELETED, 내 반응이 null인 상세가 반환된다")
            void deletesReaction() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);
                given(placeSearchClient.findById("place-1"))
                        .willReturn(Optional.of(googlePlace("place-1")));
                long placeId = placeIdFrom(register(roomId, "place-1", cookie).body());

                HttpResponse<String> response = send("DELETE", roomId, placeId, null, cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .contains("\"code\":\"PLACE_REACTION_DELETED\"")
                        .contains("\"reaction\":null");
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("reaction이 없거나 없는 값이면 400 INVALID_INPUT이다")
            void rejectsInvalidReaction() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);
                given(placeSearchClient.findById("place-1"))
                        .willReturn(Optional.of(googlePlace("place-1")));
                long placeId = placeIdFrom(register(roomId, "place-1", cookie).body());

                HttpResponse<String> missing = send("PUT", roomId, placeId, "{}", cookie);
                HttpResponse<String> unknown =
                        send("PUT", roomId, placeId, "{\"reaction\":\"MAYBE\"}", cookie);

                assertThat(missing.statusCode()).isEqualTo(400);
                assertThat(missing.body()).contains("\"code\":\"INVALID_INPUT\"");
                assertThat(unknown.statusCode()).isEqualTo(400);
                assertThat(unknown.body()).contains("\"code\":\"INVALID_INPUT\"");
            }
        }
    }

    @Nested
    class Confirm {

        @Nested
        class Success {

            @Test
            @DisplayName("전원이 가고 싶어요면 200과 PLACE_CONFIRMED, 확정된 장소 id와 시각이 반환된다")
            void confirmsPlace() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);
                given(placeSearchClient.findById("place-1"))
                        .willReturn(Optional.of(googlePlace("place-1")));
                long placeId = placeIdFrom(register(roomId, "place-1", cookie).body());

                HttpResponse<String> response = confirm(roomId, placeId, cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .contains("\"code\":\"PLACE_CONFIRMED\"")
                        .contains("\"placeId\":" + placeId)
                        .containsPattern("\"confirmedAt\":\"\\d{4}-\\d{2}-\\d{2}T");
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("아직 안 누른 사람이 있으면 409 NOT_FULLY_AGREED와 누른 수·인원이 반환된다")
            void rejectsNotFullyAgreed() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);
                given(placeSearchClient.findById("place-1"))
                        .willReturn(Optional.of(googlePlace("place-1")));
                long placeId = placeIdFrom(register(roomId, "place-1", cookie).body());
                joinOther(roomId);

                HttpResponse<String> response = confirm(roomId, placeId, cookie);

                assertThat(response.statusCode()).isEqualTo(409);
                assertThat(response.body())
                        .contains("\"code\":\"NOT_FULLY_AGREED\"")
                        .contains("\"agreeCount\":1")
                        .contains("\"activeMemberCount\":2");
            }
        }
    }

    private HttpResponse<String> confirm(long roomId, long placeId, String cookie)
            throws java.lang.Exception {
        return post(
                "/api/rooms/" + roomId + "/place-candidates/" + placeId + "/confirm", "", cookie);
    }

    // 아직 아무것도 안 누른 멤버를 한 명 더 넣는다
    private void joinOther(long roomId) {
        User user =
                userRepository.saveAndFlush(
                        User.create(UUID.randomUUID() + "@example.com", "지영", "hashed-password"));
        roomMemberRepository.saveAndFlush(RoomMember.create(room(roomId), user, clock));
    }

    // 반응 API 호출. body가 null이면 본문 없이 보낸다
    private HttpResponse<String> send(
            String method, long roomId, long placeId, String body, String cookie)
            throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://localhost:"
                                                + port
                                                + "/api/rooms/"
                                                + roomId
                                                + "/place-candidates/"
                                                + placeId
                                                + "/reaction"))
                        .header("Cookie", cookie)
                        .header("Content-Type", "application/json")
                        .method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body))
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(long roomId, String suffix, String cookie)
            throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://localhost:"
                                                + port
                                                + "/api/rooms/"
                                                + roomId
                                                + "/place-candidates"
                                                + suffix))
                        .header("Cookie", cookie)
                        .GET()
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getBoard(long roomId, String cookie) throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://localhost:"
                                                + port
                                                + "/api/rooms/"
                                                + roomId
                                                + "/place-candidates"))
                        .header("Cookie", cookie)
                        .GET()
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private GooglePlace googlePlace(String googlePlaceId) {
        return new GooglePlace(
                googlePlaceId, "광장시장", "서울 종로구", "market", "https://maps.google.com/?cid=1", null);
    }

    private HttpResponse<String> register(long roomId, String googlePlaceId, String cookie)
            throws java.lang.Exception {
        return post(
                "/api/rooms/" + roomId + "/place-candidates",
                "{\"googlePlaceId\":\"" + googlePlaceId + "\"}",
                cookie);
    }

    // 설문 단계 API가 아직 없어서, 만든 방(SURVEYING)을 DB에서 바로 PLANNING으로 넘긴다
    private long createRoom(String cookie) throws java.lang.Exception {
        long roomId = createSurveyingRoom(cookie);
        jdbcTemplate.update("UPDATE plan_room SET status = 'PLANNING' WHERE id = ?", roomId);
        return roomId;
    }

    private long createSurveyingRoom(String cookie) throws java.lang.Exception {
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        HttpResponse<String> response =
                post(
                        "/api/rooms",
                        "{\"name\":\"여행\",\"destination\":\"서울\",\"startDate\":\""
                                + start
                                + "\",\"endDate\":\""
                                + start.plusDays(2)
                                + "\"}",
                        cookie);
        Matcher matcher = Pattern.compile("\"roomId\":(\\d+)").matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private long placeIdFrom(String body) {
        Matcher matcher = Pattern.compile("\"placeId\":(\\d+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private Room room(long roomId) {
        return roomRepository.findById(roomId).orElseThrow();
    }

    private String login() throws java.lang.Exception {
        String email = UUID.randomUUID() + "@example.com";
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
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
