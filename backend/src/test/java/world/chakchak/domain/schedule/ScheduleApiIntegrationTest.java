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
import org.springframework.jdbc.core.JdbcTemplate;
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

/** B-01 일정 보드 조회, B-02 블록 추가, B-03 블록 이동, B-04 블록 수정, B-05 블록 삭제 API를 HTTP로 호출한다. */
@IntegrationTest
class ScheduleApiIntegrationTest {

    private static final String PASSWORD = "password123!";

    @LocalServerPort private int port;

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private ScheduleDayRepository scheduleDayRepository;
    @Autowired private ScheduleItemRepository scheduleItemRepository;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
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

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("블록을 넣으면 201과 SCHEDULE_ITEM_CREATED, 새 블록 id와 그날의 새 버전이 반환된다")
            void addsItem() throws java.lang.Exception {
                String cookie = login();
                long roomId = createPlanningRoom(cookie);
                ScheduleDay day =
                        scheduleDayRepository.findAllByRoom_IdOrderByDayIndexAsc(roomId).get(0);
                PlaceCandidate place = saveConfirmedPlace(roomId);

                HttpResponse<String> response =
                        post(
                                itemsPath(roomId, day),
                                "{\"placeId\":"
                                        + place.getId()
                                        + ",\"afterItemId\":null,\"startTime\":\"14:00\","
                                        + "\"memo\":\"예약 필요\",\"dayVersion\":0}",
                                cookie);

                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.body())
                        .contains("\"code\":\"SCHEDULE_ITEM_CREATED\"")
                        .contains("\"itemId\":")
                        .contains("\"version\":1")
                        .contains("\"startTime\":\"14:00\"")
                        .contains("\"memo\":\"예약 필요\"")
                        .doesNotContain("sortOrder");
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("placeId가 없거나 memo가 200자를 넘으면 400 INVALID_INPUT이다")
            void rejectsInvalidBody() throws java.lang.Exception {
                String cookie = login();
                long roomId = createPlanningRoom(cookie);
                ScheduleDay day =
                        scheduleDayRepository.findAllByRoom_IdOrderByDayIndexAsc(roomId).get(0);
                PlaceCandidate place = saveConfirmedPlace(roomId);

                HttpResponse<String> noPlace =
                        post(itemsPath(roomId, day), "{\"dayVersion\":0}", cookie);
                HttpResponse<String> longMemo =
                        post(
                                itemsPath(roomId, day),
                                "{\"placeId\":"
                                        + place.getId()
                                        + ",\"memo\":\""
                                        + "가".repeat(201)
                                        + "\",\"dayVersion\":0}",
                                cookie);

                assertThat(noPlace.statusCode()).isEqualTo(400);
                assertThat(noPlace.body()).contains("\"code\":\"INVALID_INPUT\"");
                assertThat(longMemo.statusCode()).isEqualTo(400);
                assertThat(longMemo.body()).contains("\"code\":\"INVALID_INPUT\"");
            }
        }
    }

    @Nested
    class Move {

        @Nested
        class Success {

            @Test
            @DisplayName("블록을 옮기면 200과 SCHEDULE_ITEM_MOVED, 그날의 새 버전이 반환되고 warnings 칸은 없다")
            void movesItem() throws java.lang.Exception {
                String cookie = login();
                long roomId = createPlanningRoom(cookie);
                ScheduleDay day =
                        scheduleDayRepository.findAllByRoom_IdOrderByDayIndexAsc(roomId).get(0);
                PlaceCandidate place = saveConfirmedPlace(roomId);
                scheduleItemRepository.saveAndFlush(
                        ScheduleItem.create(day, place, 1000, null, null));
                ScheduleItem second =
                        scheduleItemRepository.saveAndFlush(
                                ScheduleItem.create(day, place, 2000, null, null));

                HttpResponse<String> response =
                        patch(
                                "/api/rooms/"
                                        + roomId
                                        + "/schedule-items/"
                                        + second.getId()
                                        + "/position",
                                "{\"afterItemId\":null,\"dayVersion\":0}",
                                cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .contains("\"code\":\"SCHEDULE_ITEM_MOVED\"")
                        .contains("\"version\":1")
                        .contains("\"itemId\":" + second.getId())
                        .doesNotContain("warnings")
                        .doesNotContain("sortOrder");
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("dayVersion이 없으면 400 INVALID_INPUT이다")
            void rejectsMissingVersion() throws java.lang.Exception {
                String cookie = login();
                long roomId = createPlanningRoom(cookie);
                ScheduleDay day =
                        scheduleDayRepository.findAllByRoom_IdOrderByDayIndexAsc(roomId).get(0);
                ScheduleItem item =
                        scheduleItemRepository.saveAndFlush(
                                ScheduleItem.create(
                                        day, saveConfirmedPlace(roomId), 1000, null, null));

                HttpResponse<String> response =
                        patch(
                                "/api/rooms/"
                                        + roomId
                                        + "/schedule-items/"
                                        + item.getId()
                                        + "/position",
                                "{\"afterItemId\":null}",
                                cookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("\"code\":\"INVALID_INPUT\"");
            }
        }
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName(
                    "블록을 고치면 200과 SCHEDULE_ITEM_UPDATED가 오고, 시간이 어긋나면 warnings에 TIME_ORDER_MISMATCH가 붙는다")
            void updatesItem() throws java.lang.Exception {
                String cookie = login();
                long roomId = createPlanningRoom(cookie);
                ScheduleDay day =
                        scheduleDayRepository.findAllByRoom_IdOrderByDayIndexAsc(roomId).get(0);
                PlaceCandidate place = saveConfirmedPlace(roomId);
                scheduleItemRepository.saveAndFlush(
                        ScheduleItem.create(day, place, 1000, LocalTime.of(12, 0), null));
                ScheduleItem second =
                        scheduleItemRepository.saveAndFlush(
                                ScheduleItem.create(day, place, 2000, null, null));

                HttpResponse<String> response =
                        patch(
                                "/api/rooms/" + roomId + "/schedule-items/" + second.getId(),
                                "{\"startTime\":\"09:00\",\"memo\":\"아침\",\"dayVersion\":0}",
                                cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .contains("\"code\":\"SCHEDULE_ITEM_UPDATED\"")
                        .contains("\"version\":1")
                        .contains("\"startTime\":\"09:00\"")
                        .contains("\"memo\":\"아침\"")
                        .contains("\"warnings\":[\"TIME_ORDER_MISMATCH\"]");
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("memo가 200자를 넘거나 시각 형식이 틀리면 400 INVALID_INPUT이다")
            void rejectsInvalidBody() throws java.lang.Exception {
                String cookie = login();
                long roomId = createPlanningRoom(cookie);
                ScheduleDay day =
                        scheduleDayRepository.findAllByRoom_IdOrderByDayIndexAsc(roomId).get(0);
                ScheduleItem item =
                        scheduleItemRepository.saveAndFlush(
                                ScheduleItem.create(
                                        day, saveConfirmedPlace(roomId), 1000, null, null));
                String path = "/api/rooms/" + roomId + "/schedule-items/" + item.getId();

                HttpResponse<String> longMemo =
                        patch(
                                path,
                                "{\"memo\":\"" + "가".repeat(201) + "\",\"dayVersion\":0}",
                                cookie);
                HttpResponse<String> badTime =
                        patch(path, "{\"startTime\":\"25:00\",\"dayVersion\":0}", cookie);

                assertThat(longMemo.statusCode()).isEqualTo(400);
                assertThat(longMemo.body()).contains("\"code\":\"INVALID_INPUT\"");
                assertThat(badTime.statusCode()).isEqualTo(400);
                assertThat(badTime.body()).contains("\"code\":\"INVALID_INPUT\"");
            }
        }
    }

    @Nested
    class Delete {

        @Nested
        class Success {

            @Test
            @DisplayName("블록을 지우면 200과 SCHEDULE_ITEM_DELETED, 그날의 새 버전이 반환되고 지운 블록과 warnings 칸은 없다")
            void deletesItem() throws java.lang.Exception {
                String cookie = login();
                long roomId = createPlanningRoom(cookie);
                ScheduleDay day =
                        scheduleDayRepository.findAllByRoom_IdOrderByDayIndexAsc(roomId).get(0);
                PlaceCandidate place = saveConfirmedPlace(roomId);
                ScheduleItem first =
                        scheduleItemRepository.saveAndFlush(
                                ScheduleItem.create(day, place, 1000, null, null));
                ScheduleItem second =
                        scheduleItemRepository.saveAndFlush(
                                ScheduleItem.create(day, place, 2000, null, null));

                HttpResponse<String> response =
                        delete(
                                "/api/rooms/"
                                        + roomId
                                        + "/schedule-items/"
                                        + second.getId()
                                        + "?dayVersion=0",
                                cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .contains("\"code\":\"SCHEDULE_ITEM_DELETED\"")
                        .contains("\"version\":1")
                        .contains("\"itemId\":" + first.getId())
                        .doesNotContain("\"itemId\":" + second.getId())
                        .doesNotContain("warnings");
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("dayVersion이 없으면 400 INVALID_INPUT이다")
            void rejectsMissingVersion() throws java.lang.Exception {
                String cookie = login();
                long roomId = createPlanningRoom(cookie);
                ScheduleDay day =
                        scheduleDayRepository.findAllByRoom_IdOrderByDayIndexAsc(roomId).get(0);
                ScheduleItem item =
                        scheduleItemRepository.saveAndFlush(
                                ScheduleItem.create(
                                        day, saveConfirmedPlace(roomId), 1000, null, null));

                HttpResponse<String> response =
                        delete("/api/rooms/" + roomId + "/schedule-items/" + item.getId(), cookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("\"code\":\"INVALID_INPUT\"");
                assertThat(scheduleItemRepository.findById(item.getId())).isPresent();
            }
        }
    }

    @Nested
    class Swap {

        @Nested
        class Success {

            @Test
            @DisplayName("두 날을 맞바꾸면 200과 SCHEDULE_DAYS_SWAPPED, 바뀐 두 날이 일차 순서로 반환된다")
            void swapsDays() throws java.lang.Exception {
                String cookie = login();
                long roomId = createPlanningRoom(cookie);
                List<ScheduleDay> days =
                        scheduleDayRepository.findAllByRoom_IdOrderByDayIndexAsc(roomId);
                ScheduleItem item =
                        scheduleItemRepository.saveAndFlush(
                                ScheduleItem.create(
                                        days.get(0), saveConfirmedPlace(roomId), 1000, null, null));

                HttpResponse<String> response =
                        patch(
                                "/api/rooms/"
                                        + roomId
                                        + "/schedule-days/"
                                        + days.get(0).getId()
                                        + "/position",
                                "{\"dayVersion\":0,\"otherDayId\":"
                                        + days.get(1).getId()
                                        + ",\"otherDayVersion\":0}",
                                cookie);

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .contains("\"code\":\"SCHEDULE_DAYS_SWAPPED\"")
                        // 1일차가 된 날은 비어 있고, 블록이 있던 날은 2일차가 됐다
                        .contains(
                                "\"days\":[{\"scheduleDayId\":"
                                        + days.get(1).getId()
                                        + ",\"dayIndex\":1,")
                        .contains("{\"scheduleDayId\":" + days.get(0).getId() + ",\"dayIndex\":2,")
                        .contains("\"itemId\":" + item.getId());
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("otherDayId나 버전이 없으면 400 INVALID_INPUT이다")
            void rejectsMissingFields() throws java.lang.Exception {
                String cookie = login();
                long roomId = createPlanningRoom(cookie);
                List<ScheduleDay> days =
                        scheduleDayRepository.findAllByRoom_IdOrderByDayIndexAsc(roomId);

                HttpResponse<String> response =
                        patch(
                                "/api/rooms/"
                                        + roomId
                                        + "/schedule-days/"
                                        + days.get(0).getId()
                                        + "/position",
                                "{\"dayVersion\":0}",
                                cookie);

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("\"code\":\"INVALID_INPUT\"");
            }
        }
    }

    private HttpResponse<String> delete(String path, String cookie) throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Cookie", cookie)
                        .DELETE()
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path, String body, String cookie)
            throws java.lang.Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .header("Cookie", cookie)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String itemsPath(long roomId, ScheduleDay day) {
        return "/api/rooms/" + roomId + "/schedule-days/" + day.getId() + "/items";
    }

    // 방을 API로 만들면 설문 단계라서, 보드를 고칠 수 있게 PLANNING으로 바꿔 둔다
    private long createPlanningRoom(String cookie) throws java.lang.Exception {
        long roomId = createRoom(cookie);
        jdbcTemplate.update("UPDATE plan_room SET status = 'PLANNING' WHERE id = ?", roomId);
        return roomId;
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
