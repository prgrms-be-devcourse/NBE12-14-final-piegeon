package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.room.service.ActivePlaceFilterProvider;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/**
 * 비접속 요약(H-02)에서 그 사이 지워진 장소의 등록 소식이 빠지는지 실제 DB로 확인한다.
 *
 * <p>인터페이스로 주입받아, 받은 목록을 그대로 돌려주던 기본 구현이 아니라 장소 도메인의 구현이 쓰이는지도 함께 확인한다.
 */
@IntegrationTest
class ActivePlaceFilterIntegrationTest {

    @Autowired private ActivePlaceFilterProvider activePlaceFilterProvider;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private ChangeLogRepository changeLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    private final List<Long> roomIds = new ArrayList<>();

    // FK 순서대로 지운다. 변경 기록이 멤버를 가리키므로 기록부터 지운다
    @AfterEach
    void tearDown() {
        for (Long roomId : roomIds) {
            jdbcTemplate.update("delete from change_log where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from place_candidate where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room_member where plan_room_id = ?", roomId);
            jdbcTemplate.update("delete from plan_room where id = ?", roomId);
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("아직 남아 있는 후보의 등록 기록만 돌려주고, 올렸다 지운 후보의 기록은 뺀다")
            void keepsOnlyRegistrationsOfExistingPlaces() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                PlaceCandidate market = savePlace(room, minsu, "광장시장");
                PlaceCandidate temple = savePlace(room, minsu, "해동용궁사");
                PlaceCandidate beach = savePlace(room, minsu, "해운대");
                ChangeLog marketLog = saveRegistration(room, minsu, market);
                ChangeLog templeLog = saveRegistration(room, minsu, temple);
                ChangeLog beachLog = saveRegistration(room, minsu, beach);
                placeCandidateRepository.delete(temple);

                List<ChangeLog> active =
                        activePlaceFilterProvider.filterActivePlaces(
                                List.of(marketLog, templeLog, beachLog));

                assertThat(active).containsExactly(marketLog, beachLog);
            }

            @Test
            @DisplayName("지운 장소를 다시 올리면 새로 올린 등록 기록만 남는다")
            void keepsOnlyNewRegistrationAfterReRegister() {
                Room room = saveRoom();
                RoomMember minsu = saveMember(room, "민수");
                PlaceCandidate first = savePlace(room, minsu, "광장시장");
                ChangeLog firstLog = saveRegistration(room, minsu, first);
                placeCandidateRepository.delete(first);
                PlaceCandidate second = savePlace(room, minsu, "광장시장");
                ChangeLog secondLog = saveRegistration(room, minsu, second);

                List<ChangeLog> active =
                        activePlaceFilterProvider.filterActivePlaces(List.of(firstLog, secondLog));

                assertThat(active).containsExactly(secondLog);
            }

            @Test
            @DisplayName("등록 기록이 없으면 빈 목록을 돌려준다")
            void returnsEmptyWhenNoRegistrations() {
                assertThat(activePlaceFilterProvider.filterActivePlaces(List.of())).isEmpty();
            }
        }
    }

    private Room saveRoom() {
        LocalDate startDate = LocalDate.now(clock).plusDays(10);
        Room room =
                roomRepository.saveAndFlush(
                        Room.create(
                                "지워진 장소 거르기",
                                "부산",
                                startDate,
                                startDate.plusDays(2),
                                UUID.randomUUID().toString().substring(0, 8),
                                RoomStatus.PLANNING,
                                clock));
        roomIds.add(room.getId());
        return room;
    }

    private RoomMember saveMember(Room room, String name) {
        User user =
                userRepository.saveAndFlush(
                        User.create(UUID.randomUUID() + "@example.com", name, "hash"));
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }

    private PlaceCandidate savePlace(Room room, RoomMember suggestedBy, String name) {
        return placeCandidateRepository.saveAndFlush(
                PlaceCandidate.create(
                        room,
                        "place-" + UUID.randomUUID(),
                        name,
                        "market",
                        "부산",
                        "https://maps.google.com/?cid=1",
                        null,
                        suggestedBy));
    }

    // 서비스가 등록할 때 남기는 것과 같은 모양의 기록
    private ChangeLog saveRegistration(Room room, RoomMember actor, PlaceCandidate place) {
        return changeLogRepository.save(
                ChangeLog.create(
                        room,
                        actor,
                        EntityType.PLACE_CANDIDATE,
                        place.getId(),
                        EventKind.PLACE_REGISTERED,
                        ActionStatus.CREATE,
                        place.getName() + ": 후보로 올라왔어요"));
    }
}
