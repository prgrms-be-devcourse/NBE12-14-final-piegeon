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
import world.chakchak.domain.room.repository.RoomRepository;

/** P-02 장소 후보 등록 API를 HTTP로 호출한다. Google은 PlaceSearchClient를 가짜로 바꿔 부르지 않는다. */
@IntegrationTest
class PlaceCandidateApiIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

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
