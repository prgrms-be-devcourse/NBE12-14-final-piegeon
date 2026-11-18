package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
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
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/** place_candidate 테이블 저장·조회 테스트. Testcontainers의 MySQL에 V6 마이그레이션이 적용된 상태로 돈다. */
@IntegrationTest
class PlaceCandidateRepositoryTest {

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
            @DisplayName("장소 후보를 저장한 뒤 ID로 조회하면 저장한 값과 연관관계가 유지된다")
            void savesAndFindsCandidate() {
                Room room = saveRoom();
                RoomMember member = saveMember(room);
                PlaceCandidate saved =
                        placeCandidateRepository.saveAndFlush(
                                PlaceCandidate.create(
                                        room,
                                        "place-1",
                                        "광장시장",
                                        "market",
                                        "서울 종로구",
                                        "https://maps.google.com/?cid=1",
                                        "places/place-1/photos/p1",
                                        member));

                PlaceCandidate found =
                        placeCandidateRepository.findById(saved.getId()).orElseThrow();

                assertThat(found.getRoom().getId()).isEqualTo(room.getId());
                assertThat(found.getSuggestedBy().getId()).isEqualTo(member.getId());
                assertThat(found.getGooglePlaceId()).isEqualTo("place-1");
                assertThat(found.getName()).isEqualTo("광장시장");
                assertThat(found.getPlaceType()).isEqualTo("market");
                assertThat(found.getAddress()).isEqualTo("서울 종로구");
                assertThat(found.getGoogleMapsUrl()).isEqualTo("https://maps.google.com/?cid=1");
                assertThat(found.getPhotoName()).isEqualTo("places/place-1/photos/p1");
                assertThat(found.getConfirmedAt()).isNull();
                assertThat(found.getCreatedAt()).isNotNull();
            }

            @Test
            @DisplayName("같은 Google 장소라도 다른 방에는 각각 후보로 저장할 수 있다")
            void allowsSamePlaceInDifferentRooms() {
                Room room1 = saveRoom();
                Room room2 = saveRoom();
                placeCandidateRepository.saveAndFlush(newCandidate(room1, saveMember(room1)));

                PlaceCandidate other =
                        placeCandidateRepository.saveAndFlush(
                                newCandidate(room2, saveMember(room2)));

                assertThat(other.getId()).isNotNull();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("같은 방에 같은 Google 장소를 두 번 저장하면 UNIQUE 제약 위반이 난다")
            void rejectsDuplicatePlaceInSameRoom() {
                Room room = saveRoom();
                RoomMember member = saveMember(room);
                placeCandidateRepository.saveAndFlush(newCandidate(room, member));

                assertThatThrownBy(() ->
                                placeCandidateRepository.saveAndFlush(newCandidate(room, member)))
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
        }
    }

    private PlaceCandidate newCandidate(Room room, RoomMember member) {
        return PlaceCandidate.create(room, "same-place", "광장시장", null, null, null, null, member);
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
                        RoomStatus.SURVEYING,
                        clock));
    }

    private RoomMember saveMember(Room room) {
        User user =
                userRepository.saveAndFlush(
                        User.create(UUID.randomUUID() + "@example.com", "테스터", "hashed-password"));
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }
}
