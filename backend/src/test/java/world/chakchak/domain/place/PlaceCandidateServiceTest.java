package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import world.chakchak.domain.place.service.PlaceSearchClient;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

/**
 * P-02 장소 후보 등록. 실제 MySQL에서 방 잠금과 저장을 확인한다.
 *
 * <p>Google 조회만 가짜로 바꾸고 register를 그대로 부른다. 그래야 트랜잭션이 실제로 열리는지, 멤버의 user를 트랜잭션 안에서 읽는지까지 함께 확인된다.
 */
@IntegrationTest
class PlaceCandidateServiceTest {

    @Autowired private PlaceCandidateService placeCandidateService;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private PlaceReactionRepository placeReactionRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    @MockitoBean private PlaceSearchClient placeSearchClient;

    @BeforeEach
    void stubGoogle() {
        given(placeSearchClient.findById(anyString()))
                .willAnswer(invocation -> Optional.of(googlePlace(invocation.getArgument(0))));
    }

    @Nested
    class Register {

        @Nested
        class Success {

            @Test
            @DisplayName("후보를 Google 조회 값으로 저장하고, 올린 사람의 가고 싶어요를 함께 남긴다")
            void savesCandidateAndSuggesterWant() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember member = saveMember(room, "민수");

                PlaceCandidateItem item = register(member, "place-1");

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
                saveMember(room, "지영");
                saveMember(room, "철수");

                PlaceCandidateItem item = register(member, "place-1");

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

                PlaceCandidateItem item = register(member, "place-1");

                assertThat(item.stage()).isEqualTo(PlaceStage.FULL);
            }

            @Test
            @DisplayName("칸은 나간 멤버를 뺀 인원으로 계산한다")
            void stageExcludesLeftMembers() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember member = saveMember(room, "민수");
                leave(saveMember(room, "지영"));

                PlaceCandidateItem item = register(member, "place-1");

                // 남은 사람이 나 혼자라 전원 동의
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
                    register(member, "place-" + i);
                }

                assertThatThrownBy(() -> register(member, "place-10"))
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
                PlaceCandidateItem first = register(member, "place-1");

                assertThatThrownBy(() -> register(member, "place-1"))
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

            @Test
            @DisplayName("Google에 없는 장소면 PLACE_NOT_FOUND로 막고 아무것도 저장하지 않는다")
            void rejectsUnknownPlace() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember member = saveMember(room, "민수");
                given(placeSearchClient.findById("missing")).willReturn(Optional.empty());

                assertThatThrownBy(() -> register(member, "missing"))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND));
                assertThat(placeCandidateRepository.countByRoom(room)).isZero();
            }

            @Test
            @DisplayName("일정 조율(PLANNING) 중이 아닌 방이면 INVALID_STATUS로 막고 아무것도 저장하지 않는다")
            void rejectsRoomNotPlanning() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember member = saveMember(room, "민수");
                jdbcTemplate.update(
                        "UPDATE plan_room SET status = 'SURVEYING' WHERE id = ?", room.getId());

                assertThatThrownBy(() -> register(member, "place-1"))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.INVALID_STATUS));
                assertThat(placeCandidateRepository.countByRoom(room)).isZero();
            }

            @Test
            @DisplayName("방을 나간 멤버면 FORBIDDEN으로 막고 아무것도 저장하지 않는다")
            void rejectsLeftMember() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember member = saveMember(room, "민수");
                leave(member);

                assertThatThrownBy(() -> register(member, "place-1"))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
                assertThat(placeCandidateRepository.countByRoom(room)).isZero();
            }

            @Test
            @DisplayName("다른 방의 멤버면 FORBIDDEN으로 막는다")
            void rejectsMemberOfOtherRoom() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember outsider = saveMember(otherRoom, "민수");

                assertThatThrownBy(() ->
                                placeCandidateService.register(
                                        room.getId(), outsider.getUser().getId(), "place-1"))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
                assertThat(placeCandidateRepository.countByRoom(room)).isZero();
            }
        }
    }

    private PlaceCandidateItem register(RoomMember member, String googlePlaceId) {
        return placeCandidateService.register(
                member.getRoom().getId(), member.getUser().getId(), googlePlaceId);
    }

    private void leave(RoomMember member) {
        jdbcTemplate.update(
                "UPDATE plan_room_member SET left_at = NOW(6) WHERE id = ?", member.getId());
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
