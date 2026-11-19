package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.entity.PlaceReaction;
import world.chakchak.domain.place.entity.PlaceReactionType;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.place.repository.PlaceReactionRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/** place_reaction 테이블 저장·조회 테스트. V11에서 place_agreement를 바꾼 테이블이다. */
@IntegrationTest
@Transactional
class PlaceReactionRepositoryTest {

    @Autowired private PlaceReactionRepository placeReactionRepository;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private Clock clock;
    @Autowired private EntityManager entityManager;

    private Room room;
    private RoomMember member;
    private PlaceCandidate candidate;

    @BeforeEach
    void setUp() {
        room = saveRoom();
        member = saveMember(room, "테스터");
        candidate =
                placeCandidateRepository.save(
                        PlaceCandidate.create(
                                room, "place-1", "광장시장", "market", null, null, null, member));
    }

    private Room saveRoom() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return roomRepository.save(
                Room.create(
                        "여행 " + suffix,
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        suffix,
                        RoomStatus.PLANNING,
                        clock));
    }

    private RoomMember saveMember(Room room, String name) {
        User user =
                userRepository.save(
                        User.create(UUID.randomUUID() + "@example.com", name, "hashed-password"));
        return roomMemberRepository.save(RoomMember.create(room, user, clock));
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("반응을 저장한 뒤 조회하면 반응 종류가 enum 문자열로 유지된다")
            void savesAndFindsReaction() {
                PlaceReaction saved =
                        placeReactionRepository.saveAndFlush(
                                PlaceReaction.create(candidate, member, PlaceReactionType.NOPE));

                PlaceReaction found = placeReactionRepository.findById(saved.getId()).orElseThrow();

                assertThat(found.getReaction()).isEqualTo(PlaceReactionType.NOPE);
                assertThat(found.getPlaceCandidate().getId()).isEqualTo(candidate.getId());
                assertThat(found.getMember().getId()).isEqualTo(member.getId());
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("같은 멤버가 같은 장소 후보에 반응을 두 번 저장할 수 없다")
            void rejectsDuplicateReaction() {
                placeReactionRepository.saveAndFlush(
                        PlaceReaction.create(candidate, member, PlaceReactionType.WANT));

                assertThatThrownBy(() ->
                                placeReactionRepository.saveAndFlush(
                                        PlaceReaction.create(
                                                candidate, member, PlaceReactionType.NOPE)))
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("uk_place_reaction_candidate_member");
            }
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("방의 반응 중 고른 종류(WANT)만 가져오고 NOPE와 다른 방의 반응은 빼고 가져온다")
            void findsOnlyWantInRoom() {
                RoomMember other = saveMember(room, "철수");
                PlaceReaction want =
                        placeReactionRepository.save(
                                PlaceReaction.create(candidate, member, PlaceReactionType.WANT));
                placeReactionRepository.save(
                        PlaceReaction.create(candidate, other, PlaceReactionType.NOPE));
                Room otherRoom = saveRoom();
                RoomMember otherRoomMember = saveMember(otherRoom, "영희");
                PlaceCandidate otherRoomCandidate =
                        placeCandidateRepository.save(
                                PlaceCandidate.create(
                                        otherRoom,
                                        "place-1",
                                        "광장시장",
                                        null,
                                        null,
                                        null,
                                        null,
                                        otherRoomMember));
                placeReactionRepository.save(
                        PlaceReaction.create(
                                otherRoomCandidate, otherRoomMember, PlaceReactionType.WANT));

                List<PlaceReaction> found =
                        placeReactionRepository.findAllByPlaceCandidate_RoomAndReaction(
                                room, PlaceReactionType.WANT);

                assertThat(found).extracting(PlaceReaction::getId).containsExactly(want.getId());
            }

            @Test
            @DisplayName("반응과 함께 멤버와 그 사용자까지 가져와 따로 조회하지 않아도 이름을 쓸 수 있다")
            void fetchesMemberWithReactions() {
                placeReactionRepository.save(
                        PlaceReaction.create(candidate, member, PlaceReactionType.WANT));
                entityManager.flush();
                entityManager.clear();

                PlaceReaction found =
                        placeReactionRepository
                                .findAllByPlaceCandidate_RoomAndReaction(
                                        room, PlaceReactionType.WANT)
                                .getFirst();

                assertThat(Hibernate.isInitialized(found.getMember())).isTrue();
                assertThat(Hibernate.isInitialized(found.getMember().getUser())).isTrue();
                assertThat(found.getMember().getUser().getName()).isEqualTo("테스터");
            }
        }
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("반응을 바꾸면 DB에도 새 반응이 저장된다")
            void updatesReaction() {
                PlaceReaction saved =
                        placeReactionRepository.saveAndFlush(
                                PlaceReaction.create(candidate, member, PlaceReactionType.WANT));

                saved.change(PlaceReactionType.NOPE);
                placeReactionRepository.flush();

                assertThat(
                                placeReactionRepository
                                        .findById(saved.getId())
                                        .orElseThrow()
                                        .getReaction())
                        .isEqualTo(PlaceReactionType.NOPE);
            }
        }
    }
}
