package world.chakchak.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.schedule.entity.ScheduleDay;
import world.chakchak.domain.schedule.entity.ScheduleItem;
import world.chakchak.domain.schedule.repository.ScheduleDayRepository;
import world.chakchak.domain.schedule.repository.ScheduleItemRepository;

/** B-01 일정 보드 조회 API를 HTTP로 호출한다. */
@IntegrationTest
class ScheduleApiIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private ScheduleDayRepository scheduleDayRepository;
    @Autowired private ScheduleItemRepository scheduleItemRepository;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private Clock clock;

    private final HttpClient client = HttpClient.newHttpClient();

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("보드를 보면 200과 SCHEDULE_FETCHED, 여행 일수만큼의 날과 블록이 반환된다")
            void returnsBoard() throws java.lang.Exception {
                String cookie = login();
                long roomId = createRoom(cookie);
                List<ScheduleDay> days =
                        scheduleDayRepository.findAllByRoom_IdOrderByDayIndexAsc(roomId);
                PlaceCandidate place = saveConfirmedPlace(roomId);
                scheduleItemRepository.saveAndFlush(
                        ScheduleItem.create(days.get(0), place, 1000, LocalTime.of(11, 0), "점심"));

                HttpResponse<String> response = get(roomId, cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .contains("\"code\":\"SCHEDULE_FETCHED\"")
                        .contains("\"roomStatus\":\"SURVEYING\"")
                        .contains("\"dayIndex\":3")
                        .contains("\"startTime\":\"11:00\"")
                        .contains("\"memo\":\"점심\"")
                        .doesNotContain("sortOrder");
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("방 멤버가 아니면 403 FORBIDDEN이다")
            void rejectsNonMember() throws java.lang.Exception {
                long roomId = createRoom(login());

                HttpResponse<String> response = get(roomId, login());

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"");
            }
        }
    }

    private HttpResponse<String> get(long roomId, String cookie) throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://localhost:"
                                                + port
                                                + "/api/rooms/"
                                                + roomId
                                                + "/schedule"))
                        .header("Cookie", cookie)
                        .GET()
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // 방을 만든 사람(첫 멤버)이 올린 확정된 장소
    private PlaceCandidate saveConfirmedPlace(long roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow();
        RoomMember creator =
                roomMemberRepository.findAll().stream()
                        .filter(member -> member.getRoom().getId().equals(roomId))
                        .findFirst()
                        .orElseThrow();
        PlaceCandidate place =
                PlaceCandidate.create(
                        room,
                        UUID.randomUUID().toString(),
                        "광장시장",
                        null,
                        "서울 종로구",
                        null,
                        null,
                        creator);
        place.confirm(clock);
        return placeCandidateRepository.saveAndFlush(place);
    }

    // 방을 API로 만들면 여행 일수(3일)만큼 날이 생긴다
    private long createRoom(String cookie) throws java.lang.Exception {
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
