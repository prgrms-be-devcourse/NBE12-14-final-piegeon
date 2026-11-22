package world.chakchak.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.errorcode.PlaceErrorCode;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.schedule.dto.AddItemRequest;
import world.chakchak.domain.schedule.dto.AddItemResponse;
import world.chakchak.domain.schedule.dto.DayChangeResponse;
import world.chakchak.domain.schedule.dto.DaySnapshot;
import world.chakchak.domain.schedule.dto.DaySnapshot.ItemResponse;
import world.chakchak.domain.schedule.dto.MoveItemRequest;
import world.chakchak.domain.schedule.dto.ScheduleBoardResponse;
import world.chakchak.domain.schedule.dto.ScheduleVersionConflictData;
import world.chakchak.domain.schedule.entity.ScheduleDay;
import world.chakchak.domain.schedule.entity.ScheduleItem;
import world.chakchak.domain.schedule.errorcode.ScheduleErrorCode;
import world.chakchak.domain.schedule.repository.ScheduleDayRepository;
import world.chakchak.domain.schedule.repository.ScheduleItemRepository;
import world.chakchak.domain.schedule.service.ScheduleBoardService;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;
import world.chakchak.global.error.ErrorCode;

/** B-01 일정 보드 조회, B-02 블록 추가, B-03 블록 이동. */
@IntegrationTest
class ScheduleBoardServiceTest {

    @Autowired private ScheduleBoardService scheduleBoardService;
    @Autowired private ScheduleDayRepository scheduleDayRepository;
    @Autowired private ScheduleItemRepository scheduleItemRepository;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ChangeLogRepository changeLogRepository;
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

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("빈 날에 넣으면 순서값 1000으로 들어가고, 버전이 1 오르고, 그날 모습이 돌아온다")
            void addsToEmptyDay() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");

                AddItemResponse response =
                        addItem(
                                minsu,
                                day,
                                new AddItemRequest(
                                        market.getId(), null, LocalTime.of(14, 0), "예약 필요", 0L));

                assertThat(response.day().version()).isEqualTo(1);
                assertThat(response.day().items())
                        .containsExactly(
                                new ItemResponse(
                                        response.itemId(),
                                        market.getId(),
                                        "광장시장",
                                        "서울 종로구",
                                        LocalTime.of(14, 0),
                                        "예약 필요"));
                assertThat(sortOrdersOf(day)).containsExactly(1000);
                assertThat(scheduleDayRepository.findById(day.getId()).orElseThrow().getVersion())
                        .isEqualTo(1);
            }

            @Test
            @DisplayName("afterItemId가 없으면 맨 앞, 있으면 그 블록 바로 뒤에 들어간다")
            void addsAtPosition() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                ScheduleItem first = saveItem(day, market, 1000, null, null);
                ScheduleItem second = saveItem(day, market, 2000, null, null);

                Long front = addItem(minsu, day, request(market, null, 0L)).itemId();
                Long middle = addItem(minsu, day, request(market, first.getId(), 1L)).itemId();
                Long back = addItem(minsu, day, request(market, second.getId(), 2L)).itemId();

                assertThat(itemIdsOf(day))
                        .containsExactly(front, first.getId(), middle, second.getId(), back);
                // 맨 앞은 0과 1000의 가운데, 사이는 앞뒤의 가운데, 맨 뒤는 앞 + 1000
                assertThat(sortOrdersOf(day)).containsExactly(500, 1000, 1500, 2000, 3000);
            }

            @Test
            @DisplayName("사이에 넣을 순서값이 없으면 그날을 1000 간격으로 다시 매기고 순서는 그대로다")
            void renumbersWhenNoGap() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                ScheduleItem first = saveItem(day, market, 1, null, null);
                ScheduleItem second = saveItem(day, market, 2, null, null);

                AddItemResponse response = addItem(minsu, day, request(market, first.getId(), 0L));

                assertThat(itemIdsOf(day))
                        .containsExactly(first.getId(), response.itemId(), second.getId());
                assertThat(sortOrdersOf(day)).containsExactly(1000, 1500, 2000);
                assertThat(response.day().items())
                        .extracting(ItemResponse::itemId)
                        .containsExactly(first.getId(), response.itemId(), second.getId());
            }

            @Test
            @DisplayName("같은 장소를 같은 날에 두 번 넣어도 된다")
            void allowsSamePlaceTwice() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate hotel = saveConfirmedPlace(room, minsu, "숙소", "서울 중구");

                addItem(minsu, day, request(hotel, null, 0L));
                AddItemResponse response = addItem(minsu, day, request(hotel, null, 1L));

                assertThat(response.day().items())
                        .extracting(ItemResponse::placeId)
                        .containsExactly(hotel.getId(), hotel.getId());
            }

            @Test
            @DisplayName("넣으면 변경 이력에 \"1일차 광장시장: 추가됐어요\"가 남는다")
            void recordsChangeLog() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");

                AddItemResponse response = addItem(minsu, day, request(market, null, 0L));

                ChangeLog log =
                        changeLogRepository
                                .findTopByRoom_IdOrderByIdDesc(room.getId())
                                .orElseThrow();
                assertThat(log.getEventKind()).isEqualTo(EventKind.SCHEDULE_CHANGED);
                assertThat(log.getActionStatus()).isEqualTo(ActionStatus.CREATE);
                assertThat(log.getEntityId()).isEqualTo(response.itemId());
                assertThat(log.getSummary()).isEqualTo("1일차 광장시장: 추가됐어요");
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("방이 PLANNING이 아니면 BOARD_LOCKED이고 블록은 안 생긴다")
            void rejectsLockedBoard() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                jdbcTemplate.update(
                        "UPDATE plan_room SET status = 'REVIEWING' WHERE id = ?", room.getId());

                assertFails(
                        () -> addItem(minsu, day, request(market, null, 0L)),
                        ScheduleErrorCode.BOARD_LOCKED);
                assertThat(sortOrdersOf(day)).isEmpty();
            }

            @Test
            @DisplayName("내가 본 버전이 낡았으면 SCHEDULE_VERSION_CONFLICT와 지금 버전을 알려 주고, 아무것도 안 바뀐다")
            void rejectsStaleVersion() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                addItem(minsu, day, request(market, null, 0L));

                assertThatThrownBy(() -> addItem(minsu, day, request(market, null, 0L)))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> {
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(ScheduleErrorCode.SCHEDULE_VERSION_CONFLICT);
                                    assertThat(e.getData())
                                            .isEqualTo(
                                                    new ScheduleVersionConflictData(
                                                            day.getId(), 1L));
                                });
                assertThat(sortOrdersOf(day)).hasSize(1);
                assertThat(scheduleDayRepository.findById(day.getId()).orElseThrow().getVersion())
                        .isEqualTo(1);
            }

            @Test
            @DisplayName("다른 방의 날이면 SCHEDULE_DAY_NOT_FOUND이다")
            void rejectsOtherRoomDay() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                ScheduleDay otherDay = saveDay(saveRoom(), 1);

                assertFails(
                        () -> addItem(minsu, otherDay, request(market, null, 0L)),
                        ScheduleErrorCode.SCHEDULE_DAY_NOT_FOUND);
            }

            @Test
            @DisplayName("다른 방의 장소면 PLACE_CANDIDATE_NOT_FOUND이다")
            void rejectsOtherRoomPlace() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                Room otherRoom = saveRoom();
                PlaceCandidate otherPlace =
                        saveConfirmedPlace(otherRoom, saveMember(otherRoom, "철수"), "해운대", "부산");

                assertFails(
                        () -> addItem(minsu, day, request(otherPlace, null, 0L)),
                        PlaceErrorCode.PLACE_CANDIDATE_NOT_FOUND);
            }

            @Test
            @DisplayName("확정 안 된 장소면 PLACE_NOT_CONFIRMED이고 버전은 그대로다")
            void rejectsUnconfirmedPlace() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate place =
                        placeCandidateRepository.saveAndFlush(
                                PlaceCandidate.create(
                                        room,
                                        UUID.randomUUID().toString(),
                                        "광장시장",
                                        null,
                                        "서울 종로구",
                                        null,
                                        null,
                                        minsu));

                assertFails(
                        () -> addItem(minsu, day, request(place, null, 0L)),
                        PlaceErrorCode.PLACE_NOT_CONFIRMED);
                assertThat(scheduleDayRepository.findById(day.getId()).orElseThrow().getVersion())
                        .isZero();
            }

            @Test
            @DisplayName("그날 블록이 이미 10개면 DAY_ITEM_LIMIT_EXCEEDED이다")
            void rejectsEleventhItem() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                for (int i = 1; i <= 10; i++) {
                    saveItem(day, market, i * 1000, null, null);
                }

                assertFails(
                        () -> addItem(minsu, day, request(market, null, 0L)),
                        ScheduleErrorCode.DAY_ITEM_LIMIT_EXCEEDED);
                assertThat(sortOrdersOf(day)).hasSize(10);
            }

            @Test
            @DisplayName("afterItemId가 그날의 블록이 아니면 SCHEDULE_ITEM_NOT_FOUND이다")
            void rejectsAfterItemOfOtherDay() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day1 = saveDay(room, 1);
                ScheduleDay day2 = saveDay(room, 2);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                ScheduleItem otherDayItem = saveItem(day2, market, 1000, null, null);

                assertFails(
                        () -> addItem(minsu, day1, request(market, otherDayItem.getId(), 0L)),
                        ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND);
            }

            @Test
            @DisplayName("다른 방의 멤버가 넣으면 FORBIDDEN이다")
            void rejectsNonMember() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                RoomMember outsider = saveMember(saveRoom(), "철수");

                assertFails(
                        () ->
                                scheduleBoardService.addItem(
                                        room.getId(),
                                        day.getId(),
                                        outsider.getUser().getId(),
                                        request(market, null, 0L)),
                        CommonErrorCode.FORBIDDEN);
            }
        }
    }

    @Nested
    class Move {

        @Nested
        class Success {

            @Test
            @DisplayName("맨 앞, 다른 블록 뒤, 맨 뒤로 옮기면 그 자리로 가고 순서값은 앞뒤의 가운데가 된다")
            void movesToPosition() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                ScheduleItem a = saveItem(day, market, 1000, null, null);
                ScheduleItem b = saveItem(day, market, 2000, null, null);
                ScheduleItem c = saveItem(day, market, 3000, null, null);

                // c를 맨 앞으로: c a b
                moveItem(minsu, c, new MoveItemRequest(null, 0L));
                assertThat(itemIdsOf(day)).containsExactly(c.getId(), a.getId(), b.getId());
                // c를 a 뒤로: a c b
                moveItem(minsu, c, new MoveItemRequest(a.getId(), 1L));
                assertThat(itemIdsOf(day)).containsExactly(a.getId(), c.getId(), b.getId());
                // a를 b 뒤로(맨 뒤): c b a
                moveItem(minsu, a, new MoveItemRequest(b.getId(), 2L));
                assertThat(itemIdsOf(day)).containsExactly(c.getId(), b.getId(), a.getId());
                assertThat(sortOrdersOf(day)).containsExactly(1500, 2000, 3000);
            }

            @Test
            @DisplayName("옮기면 버전이 1 오르고, 새 순서의 그날 모습과 변경 이력이 남는다")
            void returnsSnapshotAndRecordsLog() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                PlaceCandidate palace = saveConfirmedPlace(room, minsu, "경복궁", "서울 종로구 사직로");
                ScheduleItem first = saveItem(day, palace, 1000, null, null);
                ScheduleItem second = saveItem(day, market, 2000, null, null);

                DayChangeResponse response = moveItem(minsu, second, new MoveItemRequest(null, 0L));

                assertThat(response.day().version()).isEqualTo(1);
                assertThat(response.warnings()).isEmpty();
                assertThat(response.day().items())
                        .extracting(ItemResponse::itemId)
                        .containsExactly(second.getId(), first.getId());
                assertThat(scheduleDayRepository.findById(day.getId()).orElseThrow().getVersion())
                        .isEqualTo(1);
                ChangeLog log =
                        changeLogRepository
                                .findTopByRoom_IdOrderByIdDesc(room.getId())
                                .orElseThrow();
                assertThat(log.getEventKind()).isEqualTo(EventKind.SCHEDULE_CHANGED);
                assertThat(log.getActionStatus()).isEqualTo(ActionStatus.UPDATE);
                assertThat(log.getEntityId()).isEqualTo(second.getId());
                assertThat(log.getSummary()).isEqualTo("1일차 광장시장: 순서가 바뀌었어요");
            }

            @Test
            @DisplayName("틈이 없어 다시 매길 때도 옮길 블록의 옛 순서값과 겹치지 않는다")
            void renumbersWithoutClash() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                ScheduleItem a = saveItem(day, market, 1, null, null);
                ScheduleItem b = saveItem(day, market, 2, null, null);
                // 나머지를 다시 매기면 b가 2000이 되는데, c의 옛 값도 2000이다
                ScheduleItem c = saveItem(day, market, 2000, null, null);

                moveItem(minsu, c, new MoveItemRequest(a.getId(), 0L));

                assertThat(itemIdsOf(day)).containsExactly(a.getId(), c.getId(), b.getId());
                assertThat(sortOrdersOf(day)).containsExactly(1000, 1500, 2000);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("방이 PLANNING이 아니면 BOARD_LOCKED이고 순서는 그대로다")
            void rejectsLockedBoard() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                ScheduleItem a = saveItem(day, market, 1000, null, null);
                ScheduleItem b = saveItem(day, market, 2000, null, null);
                jdbcTemplate.update(
                        "UPDATE plan_room SET status = 'REVIEWING' WHERE id = ?", room.getId());

                assertFails(
                        () -> moveItem(minsu, b, new MoveItemRequest(null, 0L)),
                        ScheduleErrorCode.BOARD_LOCKED);
                assertThat(itemIdsOf(day)).containsExactly(a.getId(), b.getId());
            }

            @Test
            @DisplayName("내가 본 버전이 낡았으면 SCHEDULE_VERSION_CONFLICT와 지금 버전을 알려 주고, 순서·버전은 그대로다")
            void rejectsStaleVersion() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                ScheduleItem a = saveItem(day, market, 1000, null, null);
                ScheduleItem b = saveItem(day, market, 2000, null, null);
                moveItem(minsu, b, new MoveItemRequest(null, 0L));

                assertThatThrownBy(() -> moveItem(minsu, a, new MoveItemRequest(null, 0L)))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> {
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(ScheduleErrorCode.SCHEDULE_VERSION_CONFLICT);
                                    assertThat(e.getData())
                                            .isEqualTo(
                                                    new ScheduleVersionConflictData(
                                                            day.getId(), 1L));
                                });
                assertThat(itemIdsOf(day)).containsExactly(b.getId(), a.getId());
                assertThat(scheduleDayRepository.findById(day.getId()).orElseThrow().getVersion())
                        .isEqualTo(1);
            }

            @Test
            @DisplayName("다른 방의 블록이면 SCHEDULE_ITEM_NOT_FOUND이다")
            void rejectsOtherRoomItem() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                saveDay(room, 1);
                Room otherRoom = saveRoom();
                ScheduleItem otherItem =
                        saveItem(
                                saveDay(otherRoom, 1),
                                saveConfirmedPlace(
                                        otherRoom, saveMember(otherRoom, "철수"), "해운대", "부산"),
                                1000,
                                null,
                                null);

                assertFails(
                        () -> moveItem(minsu, otherItem, new MoveItemRequest(null, 0L)),
                        ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND);
            }

            @Test
            @DisplayName("afterItemId가 다른 날의 블록이거나 옮길 블록 자신이면 SCHEDULE_ITEM_NOT_FOUND이다")
            void rejectsInvalidAfterItem() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day1 = saveDay(room, 1);
                ScheduleDay day2 = saveDay(room, 2);
                PlaceCandidate market = saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구");
                ScheduleItem item = saveItem(day1, market, 1000, null, null);
                ScheduleItem otherDayItem = saveItem(day2, market, 1000, null, null);

                assertFails(
                        () -> moveItem(minsu, item, new MoveItemRequest(otherDayItem.getId(), 0L)),
                        ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND);
                assertFails(
                        () -> moveItem(minsu, item, new MoveItemRequest(item.getId(), 0L)),
                        ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND);
                assertThat(sortOrdersOf(day1)).containsExactly(1000);
            }

            @Test
            @DisplayName("다른 방의 멤버가 옮기면 FORBIDDEN이다")
            void rejectsNonMember() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                ScheduleDay day = saveDay(room, 1);
                ScheduleItem item =
                        saveItem(
                                day,
                                saveConfirmedPlace(room, minsu, "광장시장", "서울 종로구"),
                                1000,
                                null,
                                null);
                RoomMember outsider = saveMember(saveRoom(), "철수");

                assertFails(
                        () ->
                                scheduleBoardService.moveItem(
                                        room.getId(),
                                        item.getId(),
                                        outsider.getUser().getId(),
                                        new MoveItemRequest(null, 0L)),
                        CommonErrorCode.FORBIDDEN);
            }
        }
    }

    private DayChangeResponse moveItem(
            RoomMember member, ScheduleItem item, MoveItemRequest request) {
        return scheduleBoardService.moveItem(
                member.getRoom().getId(), item.getId(), member.getUser().getId(), request);
    }

    private AddItemResponse addItem(RoomMember member, ScheduleDay day, AddItemRequest request) {
        return scheduleBoardService.addItem(
                member.getRoom().getId(), day.getId(), member.getUser().getId(), request);
    }

    private AddItemRequest request(PlaceCandidate place, Long afterItemId, long dayVersion) {
        return new AddItemRequest(place.getId(), afterItemId, null, null, dayVersion);
    }

    private List<Long> itemIdsOf(ScheduleDay day) {
        return scheduleItemRepository
                .findAllByScheduleDay_IdOrderBySortOrderAsc(day.getId())
                .stream()
                .map(ScheduleItem::getId)
                .toList();
    }

    private List<Integer> sortOrdersOf(ScheduleDay day) {
        return scheduleItemRepository
                .findAllByScheduleDay_IdOrderBySortOrderAsc(day.getId())
                .stream()
                .map(ScheduleItem::getSortOrder)
                .toList();
    }

    private void assertFails(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
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
