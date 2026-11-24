package world.chakchak.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.schedule.entity.ScheduleDay;
import world.chakchak.domain.schedule.entity.ScheduleItem;
import world.chakchak.domain.schedule.repository.ScheduleDayRepository;
import world.chakchak.domain.schedule.repository.ScheduleItemRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/** schedule_item 테이블 저장·조회 테스트. Testcontainers의 MySQL에 V15 마이그레이션이 적용된 상태로 돈다. */
@IntegrationTest
class ScheduleItemRepositoryTest {

    @Autowired private ScheduleItemRepository scheduleItemRepository;
    @Autowired private ScheduleDayRepository scheduleDayRepository;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private Clock clock;

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("블록을 저장한 뒤 ID로 조회하면 날, 장소, 순서값, 시간, 메모가 유지된다")
            void savesAndFindsItem() {
                Room room = saveRoom();
                ScheduleDay day = scheduleDayRepository.saveAndFlush(ScheduleDay.create(room, 1));
                PlaceCandidate place = saveConfirmedPlace(room, "place-1");
                ScheduleItem saved =
                        scheduleItemRepository.saveAndFlush(
                                ScheduleItem.create(day, place, 1000, LocalTime.of(11, 0), "점심"));

                ScheduleItem found = scheduleItemRepository.findById(saved.getId()).orElseThrow();

                assertThat(found.getScheduleDay().getId()).isEqualTo(day.getId());
                assertThat(found.getPlaceCandidate().getId()).isEqualTo(place.getId());
                assertThat(found.getSortOrder()).isEqualTo(1000);
                assertThat(found.getStartTime()).isEqualTo(LocalTime.of(11, 0));
                assertThat(found.getMemo()).isEqualTo("점심");
                assertThat(found.getCreatedAt()).isNotNull();
            }

            @Test
            @DisplayName("같은 장소를 같은 날의 여러 블록에 저장할 수 있다")
            void allowsSamePlaceInMultipleItems() {
                Room room = saveRoom();
                ScheduleDay day = scheduleDayRepository.saveAndFlush(ScheduleDay.create(room, 1));
                PlaceCandidate place = saveConfirmedPlace(room, "place-1");
                scheduleItemRepository.saveAndFlush(
                        ScheduleItem.create(day, place, 1000, null, null));

                ScheduleItem other =
                        scheduleItemRepository.saveAndFlush(
                                ScheduleItem.create(day, place, 2000, null, null));

                assertThat(other.getId()).isNotNull();
            }

            @Test
            @DisplayName("같은 순서값이라도 다른 날에는 각각 저장할 수 있다")
            void allowsSameSortOrderInDifferentDays() {
                Room room = saveRoom();
                ScheduleDay day1 = scheduleDayRepository.saveAndFlush(ScheduleDay.create(room, 1));
                ScheduleDay day2 = scheduleDayRepository.saveAndFlush(ScheduleDay.create(room, 2));
                PlaceCandidate place = saveConfirmedPlace(room, "place-1");
                scheduleItemRepository.saveAndFlush(
                        ScheduleItem.create(day1, place, 1000, null, null));

                ScheduleItem other =
                        scheduleItemRepository.saveAndFlush(
                                ScheduleItem.create(day2, place, 1000, null, null));

                assertThat(other.getId()).isNotNull();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("같은 날에 같은 순서값으로 두 블록을 저장하면 UNIQUE 제약 위반이 난다")
            void rejectsDuplicateSortOrderInSameDay() {
                Room room = saveRoom();
                ScheduleDay day = scheduleDayRepository.saveAndFlush(ScheduleDay.create(room, 1));
                PlaceCandidate place1 = saveConfirmedPlace(room, "place-1");
                PlaceCandidate place2 = saveConfirmedPlace(room, "place-2");
                scheduleItemRepository.saveAndFlush(
                        ScheduleItem.create(day, place1, 1000, null, null));

                assertThatThrownBy(() ->
                                scheduleItemRepository.saveAndFlush(
                                        ScheduleItem.create(day, place2, 1000, null, null)))
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
        }
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("시간·메모·순서값을 바꿔 저장하면 다시 조회해도 바뀐 값이 유지된다")
            void persistsUpdatedValues() {
                Room room = saveRoom();
                ScheduleDay day = scheduleDayRepository.saveAndFlush(ScheduleDay.create(room, 1));
                ScheduleItem item =
                        scheduleItemRepository.saveAndFlush(
                                ScheduleItem.create(
                                        day,
                                        saveConfirmedPlace(room, "place-1"),
                                        1000,
                                        LocalTime.of(11, 0),
                                        "점심"));
                item.update(LocalTime.of(15, 30), "시간 변경");
                item.changeSortOrder(1500);
                scheduleItemRepository.saveAndFlush(item);

                ScheduleItem found = scheduleItemRepository.findById(item.getId()).orElseThrow();

                assertThat(found.getStartTime()).isEqualTo(LocalTime.of(15, 30));
                assertThat(found.getMemo()).isEqualTo("시간 변경");
                assertThat(found.getSortOrder()).isEqualTo(1500);
            }
        }
    }

    private Room saveRoom() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return roomRepository.saveAndFlush(
                Room.create(
                        "여행 " + suffix,
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        suffix,
                        RoomStatus.PLANNING,
                        clock));
    }

    private PlaceCandidate saveConfirmedPlace(Room room, String googlePlaceId) {
        User user =
                userRepository.saveAndFlush(
                        User.create(UUID.randomUUID() + "@example.com", "테스터", "hashed-password"));
        RoomMember member = roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
        PlaceCandidate place =
                PlaceCandidate.create(room, googlePlaceId, "광장시장", null, null, null, null, member);
        place.confirm(clock);
        return placeCandidateRepository.saveAndFlush(place);
    }
}
