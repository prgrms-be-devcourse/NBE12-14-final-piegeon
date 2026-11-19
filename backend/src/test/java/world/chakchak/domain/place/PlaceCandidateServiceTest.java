package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.place.dto.GooglePlace;
import world.chakchak.domain.place.dto.MemberRef;
import world.chakchak.domain.place.dto.PlaceAlreadyRegisteredData;
import world.chakchak.domain.place.dto.PlaceCandidateItem;
import world.chakchak.domain.place.dto.PlaceLimitExceededData;
import world.chakchak.domain.place.dto.PlaceStage;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.entity.PlaceReaction;
import world.chakchak.domain.place.errorcode.PlaceErrorCode;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.place.repository.PlaceReactionRepository;
import world.chakchak.domain.place.service.PlaceCandidateService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;
import world.chakchak.global.error.BusinessException;

/** P-02 장소 후보 등록의 트랜잭션 부분. 실제 MySQL에서 방 잠금과 저장을 확인한다. */
@IntegrationTest
class PlaceCandidateServiceTest {

    @Autowired private PlaceCandidateService placeCandidateService;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private PlaceReactionRepository placeReactionRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private Clock clock;

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("후보를 Google 조회 값으로 저장하고, 올린 사람의 가고 싶어요를 함께 남긴다")
            void savesCandidateAndSuggesterWant() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember member = saveMember(room, "민수");

                PlaceCandidateItem item =
                        placeCandidateService.create(member, 3, googlePlace("place-1"));

                PlaceCandidate saved =
                        placeCandidateRepository.findById(item.placeId()).orElseThrow();
                assertThat(saved.getRoom().getId()).isEqualTo(room.getId());
                assertThat(saved.getSuggestedBy().getId()).isEqualTo(member.getId());
                assertThat(saved.getName()).isEqualTo("광장시장");
                assertThat(saved.getAddress()).isEqualTo("서울 종로구");
                assertThat(saved.getPlaceType()).isEqualTo("market");
                assertThat(saved.getGoogleMapsUrl()).isEqualTo("https://maps.google.com/?cid=1");
                assertThat(saved.getPhotoName()).isEqualTo("places/place-1/photos/p1");

                List<PlaceReaction> reactions = reactionsOf(item.placeId());
                assertThat(reactions).hasSize(1);
                assertThat(reactions.getFirst().getMember().getId()).isEqualTo(member.getId());
                assertThat(reactions.getFirst().isWant()).isTrue();
            }

            @Test
            @DisplayName("응답은 칸반 카드 모양이고, 올린 사람 1명만 동의한 COMPLEMENT다")
            void returnsBoardItem() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember member = saveMember(room, "민수");

                PlaceCandidateItem item =
                        placeCandidateService.create(member, 3, googlePlace("place-1"));

                MemberRef me = new MemberRef(member.getId(), "민수");
                assertThat(item.placeId()).isNotNull();
                assertThat(item.googlePlaceId()).isEqualTo("place-1");
                assertThat(item.name()).isEqualTo("광장시장");
                assertThat(item.address()).isEqualTo("서울 종로구");
                assertThat(item.placeType()).isEqualTo("market");
                assertThat(item.stage()).isEqualTo(PlaceStage.COMPLEMENT);
                assertThat(item.agreeCount()).isEqualTo(1);
                assertThat(item.agreedBy()).containsExactly(me);
                assertThat(item.suggestedBy()).isEqualTo(me);
                assertThat(item.confirmedAt()).isNull();
            }

            @Test
            @DisplayName("혼자 있는 방에서 올리면 바로 FULL이다")
            void soloRoomIsFull() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember member = saveMember(room, "민수");

                PlaceCandidateItem item =
                        placeCandidateService.create(member, 1, googlePlace("place-1"));

                assertThat(item.stage()).isEqualTo(PlaceStage.FULL);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("후보가 여행 일수 × 10개만큼 있으면 PLACE_LIMIT_EXCEEDED와 data.limit으로 막는다")
            void rejectsOverLimit() {
                // 1일 여행 → 한도 10개
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 1));
                RoomMember member = saveMember(room, "민수");
                for (int i = 0; i < 10; i++) {
                    placeCandidateService.create(member, 2, googlePlace("place-" + i));
                }

                assertThatThrownBy(() ->
                                placeCandidateService.create(member, 2, googlePlace("place-10")))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> {
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(PlaceErrorCode.PLACE_LIMIT_EXCEEDED);
                                    assertThat(e.getData())
                                            .isEqualTo(new PlaceLimitExceededData(10));
                                });
                assertThat(placeCandidateRepository.countByRoom(room)).isEqualTo(10);
            }

            @Test
            @DisplayName("같은 방에 이미 올라온 장소면 PLACE_ALREADY_REGISTERED와 기존 후보의 placeId로 막는다")
            void rejectsDuplicate() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember member = saveMember(room, "민수");
                PlaceCandidateItem first =
                        placeCandidateService.create(member, 2, googlePlace("place-1"));

                assertThatThrownBy(() ->
                                placeCandidateService.create(member, 2, googlePlace("place-1")))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> {
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(PlaceErrorCode.PLACE_ALREADY_REGISTERED);
                                    assertThat(e.getData())
                                            .isEqualTo(
                                                    new PlaceAlreadyRegisteredData(
                                                            first.placeId()));
                                });
                assertThat(placeCandidateRepository.countByRoom(room)).isEqualTo(1);
            }
        }
    }

    private GooglePlace googlePlace(String googlePlaceId) {
        return new GooglePlace(
                googlePlaceId,
                "광장시장",
                "서울 종로구",
                "market",
                "https://maps.google.com/?cid=1",
                "places/" + googlePlaceId + "/photos/p1");
    }

    private List<PlaceReaction> reactionsOf(Long placeId) {
        return placeReactionRepository.findAll().stream()
                .filter(reaction -> reaction.getPlaceCandidate().getId().equals(placeId))
                .toList();
    }

    private Room saveRoom(LocalDate startDate, LocalDate endDate) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return roomRepository.saveAndFlush(
                Room.create(
                        "여행 " + suffix,
                        "서울",
                        startDate,
                        endDate,
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
