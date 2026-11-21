package world.chakchak.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.schedule.dto.DaySnapshot;
import world.chakchak.domain.schedule.dto.DaySnapshot.ItemResponse;
import world.chakchak.domain.schedule.dto.ScheduleBoardResponse;
import world.chakchak.domain.schedule.entity.ScheduleDay;
import world.chakchak.domain.schedule.entity.ScheduleItem;
import world.chakchak.domain.schedule.repository.ScheduleDayRepository;
import world.chakchak.domain.schedule.repository.ScheduleItemRepository;
import world.chakchak.domain.schedule.service.ScheduleBoardService;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

/** B-01 일정 보드 조회. */
@IntegrationTest
class ScheduleBoardServiceTest {

    @Autowired private ScheduleBoardService scheduleBoardService;
    @Autowired private ScheduleDayRepository scheduleDayRepository;
    @Autowired private ScheduleItemRepository scheduleItemRepository;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("날은 1일차부터 순서대로 나오고, 날짜는 시작일부터 하루씩 늘어난다")
            void listsDaysInOrder() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                // 일부러 거꾸로 저장해도 1일차부터 나와야 한다
                saveDay(room, 3);
                saveDay(room, 1);
                saveDay(room, 2);

                ScheduleBoardResponse board = getBoard(minsu);

                assertThat(board.days())
                        .extracting(DaySnapshot::dayIndex, DaySnapshot::date)
                        .containsExactly(
                                tuple(1L, LocalDate.of(2027, 7, 1)),
                                tuple(2L, LocalDate.of(2027, 7, 2)),
                                tuple(3L, LocalDate.of(2027, 7, 3)));
            }

            @Test
            @DisplayName("블록은 날마다 순서값 순으로 나오고, 장소 이름·주소·시간·메모가 들어 있다")
            void listsItemsInOrder() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day1 = saveDay(room, 1);
                ScheduleDay day2 = saveDay(room, 2);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                PlaceCandidate palace = saveConfirmedPlace(room, minsu, "경복궁", "서울 종로구 사직로");
                ScheduleItem lunch = saveItem(day1, market, 2000, LocalTime.of(12, 0), "점심");
                ScheduleItem morning = saveItem(day1, palace, 1000, LocalTime.of(9, 30), null);
                ScheduleItem again = saveItem(day2, market, 1000, null, "한 번 더");

                ScheduleBoardResponse board = getBoard(minsu);

                assertThat(board.days().get(0).items())
                        .containsExactly(
                                new ItemResponse(
                                        morning.getId(),
                                        palace.getId(),
                                        "경복궁",
                                        "서울 종로구 사직로",
                                        LocalTime.of(9, 30),
                                        null),
                                new ItemResponse(
                                        lunch.getId(),
                                        market.getId(),
                                        "광장시장",
                                        "서울 종로구",
                                        LocalTime.of(12, 0),
                                        "점심"));
                assertThat(board.days().get(1).items())
                        .containsExactly(
                                new ItemResponse(
                                        again.getId(),
                                        market.getId(),
                                        "광장시장",
                                        "서울 종로구",
                                        null,
                                        "한 번 더"));
            }

            @Test
            @DisplayName("블록이 없는 날은 빈 목록이다")
            void emptyDayHasNoItems() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                saveDay(room, 1);

                assertThat(getBoard(minsu).days().get(0).items()).isEmpty();
            }

            @Test
            @DisplayName("다른 방의 날과 블록은 섞이지 않는다")
            void excludesOtherRooms() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                Room otherRoom = saveRoom();
                RoomMember chulsu = saveMember(otherRoom, "철수");
                ScheduleDay otherDay = saveDay(otherRoom, 1);
                saveItem(
                        otherDay,
                        saveConfirmedPlace(otherRoom, chulsu, "해운대", "부산"),
                        1000,
                        null,
                        null);

                ScheduleBoardResponse board = getBoard(minsu);

                assertThat(board.days())
                        .extracting(DaySnapshot::scheduleDayId)
                        .containsExactly(day.getId());
                assertThat(board.days().get(0).items()).isEmpty();
            }

            @Test
            @DisplayName("방 상태와 날마다의 버전이 그대로 나온다")
            void showsStatusAndVersion() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                day.increaseVersion();
                day.increaseVersion();
                scheduleDayRepository.saveAndFlush(day);
                jdbcTemplate.update(
                        "UPDATE plan_room SET status = 'REVIEWING' WHERE id = ?", room.getId());

                ScheduleBoardResponse board = getBoard(minsu);

                assertThat(board.roomStatus()).isEqualTo(RoomStatus.REVIEWING);
                assertThat(board.days().get(0).version()).isEqualTo(2);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("다른 방의 멤버가 보면 FORBIDDEN이다")
            void rejectsNonMember() {
                Room room = saveRoom();
                saveDay(room, 1);
                RoomMember outsider = saveMember(saveRoom(), "철수");

                assertThatThrownBy(() ->
                                scheduleBoardService.getBoard(
                                        room.getId(), outsider.getUser().getId()))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
            }

            @Test
            @DisplayName("방에서 나간 멤버가 보면 FORBIDDEN이다")
            void rejectsLeftMember() {
                Room room = saveRoom();
                saveDay(room, 1);
                RoomMember minsu = saveMember(room, "민수");
                jdbcTemplate.update(
                        "UPDATE plan_room_member SET left_at = NOW(6) WHERE id = ?", minsu.getId());

                assertThatThrownBy(() -> getBoard(minsu))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
            }
        }
    }

    private ScheduleBoardResponse getBoard(RoomMember member) {
        return scheduleBoardService.getBoard(member.getRoom().getId(), member.getUser().getId());
    }

    private ScheduleDay saveDay(Room room, int dayIndex) {
        return scheduleDayRepository.saveAndFlush(ScheduleDay.create(room, dayIndex));
    }

    private ScheduleItem saveItem(
            ScheduleDay day,
            PlaceCandidate place,
            int sortOrder,
            LocalTime startTime,
            String memo) {
        return scheduleItemRepository.saveAndFlush(
                ScheduleItem.create(day, place, sortOrder, startTime, memo));
    }

    private PlaceCandidate saveConfirmedPlace(
            Room room, RoomMember member, String name, String address) {
        PlaceCandidate place =
                PlaceCandidate.create(
                        room,
                        UUID.randomUUID().toString(),
                        name,
                        null,
                        address,
                        null,
                        null,
                        member);
        place.confirm(clock);
        return placeCandidateRepository.saveAndFlush(place);
    }

    private Room saveRoom() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return roomRepository.saveAndFlush(
                Room.create(
                        "여행 " + suffix,
                        "서울",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        suffix,
                        RoomStatus.PLANNING,
                        clock));
    }

    private RoomMember saveMember(Room room, String name) {
        User user =
                userRepository.saveAndFlush(
                        User.create(UUID.randomUUID() + "@example.com", name, "hashed-password"));
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }
}
