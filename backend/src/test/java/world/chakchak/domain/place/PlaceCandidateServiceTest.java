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
import world.chakchak.domain.comment.dto.CommentRequest;
import world.chakchak.domain.place.dto.GooglePlace;
import world.chakchak.domain.place.dto.MemberRef;
import world.chakchak.domain.place.dto.PlaceAlreadyRegisteredData;
import world.chakchak.domain.place.dto.PlaceBoardResponse;
import world.chakchak.domain.place.dto.PlaceCandidateItem;
import world.chakchak.domain.place.dto.PlaceDetailResponse;
import world.chakchak.domain.place.dto.PlaceDetailResponse.MemberReaction;
import world.chakchak.domain.place.dto.PlaceDetailResponse.ReactionLabel;
import world.chakchak.domain.place.dto.PlaceLimitExceededData;
import world.chakchak.domain.place.dto.PlaceStage;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.entity.PlaceReaction;
import world.chakchak.domain.place.entity.PlaceReactionType;
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
 * P-02 장소 후보 등록, P-03 칸반 보드. 실제 MySQL에서 방 잠금과 저장, 조회를 확인한다.
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

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("카드에 칸, 가고 싶어요 수와 누른 멤버, 올린 멤버가 채워지고 인원과 한도가 함께 온다")
            void fillsCards() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                saveMember(room, "철수");
                PlaceCandidateItem registered = register(minsu, "place-1");
                react(jiyoung, registered.placeId(), PlaceReactionType.WANT);

                PlaceBoardResponse board = getBoard(minsu);

                assertThat(board.activeMemberCount()).isEqualTo(3);
                assertThat(board.limit()).isEqualTo(30);
                assertThat(board.candidates()).hasSize(1);
                PlaceCandidateItem card = board.candidates().get(0);
                assertThat(card.placeId()).isEqualTo(registered.placeId());
                assertThat(card.name()).isEqualTo("광장시장");
                assertThat(card.stage()).isEqualTo(PlaceStage.PARTIAL);
                assertThat(card.agreeCount()).isEqualTo(2);
                assertThat(card.agreedBy())
                        .containsExactlyInAnyOrder(
                                new MemberRef(minsu.getId(), "민수"),
                                new MemberRef(jiyoung.getId(), "지영"));
                assertThat(card.nopeCount()).isZero();
                assertThat(card.nopedBy()).isEmpty();
                assertThat(card.suggestedBy()).isEqualTo(new MemberRef(minsu.getId(), "민수"));
                assertThat(card.confirmedAt()).isNull();
            }

            @Test
            @DisplayName("칸은 확정, 전체, 부분, 여집합 순이고 같은 칸은 가고 싶어요가 많은 순, 같으면 먼저 올린 순이다")
            void sortsByStageThenAgreeCount() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember a = saveMember(room, "A");
                RoomMember b = saveMember(room, "B");
                RoomMember c = saveMember(room, "C");
                RoomMember d = saveMember(room, "D");
                long complement = register(a, "complement").placeId();
                long partialTwo = register(a, "partial-2").placeId();
                react(b, partialTwo, PlaceReactionType.WANT);
                long partialThree = register(a, "partial-3").placeId();
                react(b, partialThree, PlaceReactionType.WANT);
                react(c, partialThree, PlaceReactionType.WANT);
                long full = register(a, "full").placeId();
                react(b, full, PlaceReactionType.WANT);
                react(c, full, PlaceReactionType.WANT);
                react(d, full, PlaceReactionType.WANT);
                long confirmed = register(a, "confirmed").placeId();
                confirm(confirmed);
                long laterPartialTwo = register(b, "later-partial-2").placeId();
                react(c, laterPartialTwo, PlaceReactionType.WANT);

                List<PlaceCandidateItem> cards = getBoard(a).candidates();

                assertThat(cards)
                        .extracting(PlaceCandidateItem::placeId)
                        .containsExactly(
                                confirmed,
                                full,
                                partialThree,
                                partialTwo,
                                laterPartialTwo,
                                complement);
                assertThat(cards)
                        .extracting(PlaceCandidateItem::stage)
                        .containsExactly(
                                PlaceStage.CONFIRMED,
                                PlaceStage.FULL,
                                PlaceStage.PARTIAL,
                                PlaceStage.PARTIAL,
                                PlaceStage.PARTIAL,
                                PlaceStage.COMPLEMENT);
            }

            @Test
            @DisplayName("별로예요(NOPE)는 가고 싶어요 수와 칸에 들어가지 않고, 누른 멤버는 nopedBy에 따로 담긴다")
            void separatesNope() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();
                react(jiyoung, placeId, PlaceReactionType.NOPE);

                PlaceCandidateItem card = getBoard(minsu).candidates().get(0);

                assertThat(card.agreeCount()).isEqualTo(1);
                assertThat(card.agreedBy()).containsExactly(new MemberRef(minsu.getId(), "민수"));
                assertThat(card.stage()).isEqualTo(PlaceStage.COMPLEMENT);
                assertThat(card.nopeCount()).isEqualTo(1);
                assertThat(card.nopedBy()).containsExactly(new MemberRef(jiyoung.getId(), "지영"));
            }

            @Test
            @DisplayName("다른 방의 후보와 가고 싶어요는 섞이지 않는다")
            void excludesOtherRooms() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember outsider = saveMember(otherRoom, "철수");
                RoomMember outsiderFriend = saveMember(otherRoom, "영희");
                long otherPlaceId = register(outsider, "place-1").placeId();
                react(outsiderFriend, otherPlaceId, PlaceReactionType.WANT);
                register(outsider, "place-2");

                List<PlaceCandidateItem> cards = getBoard(minsu).candidates();

                assertThat(cards).extracting(PlaceCandidateItem::placeId).containsExactly(placeId);
                assertThat(cards.get(0).agreeCount()).isEqualTo(1);
            }

            @Test
            @DisplayName("나간 멤버는 인원에서 빠지고, 한도는 여행 일수 × 10이다")
            void excludesLeftMembersFromCount() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 5));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                leave(saveMember(room, "철수"));
                long placeId = register(minsu, "place-1").placeId();
                react(jiyoung, placeId, PlaceReactionType.WANT);

                PlaceBoardResponse board = getBoard(minsu);

                assertThat(board.activeMemberCount()).isEqualTo(2);
                assertThat(board.limit()).isEqualTo(50);
                assertThat(board.candidates().get(0).stage()).isEqualTo(PlaceStage.FULL);
            }

            @Test
            @DisplayName("나간 멤버의 반응은 동의 수와 칸, nopedBy에 들어가지 않는다 (나간 사람 가고 싶어요로 FULL이 되지 않음)")
            void ignoresLeftMembersReactions() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember a = saveMember(room, "A");
                RoomMember b = saveMember(room, "B");
                saveMember(room, "C");
                RoomMember d = saveMember(room, "D");
                long placeId = register(b, "place-1").placeId();
                react(a, placeId, PlaceReactionType.WANT);
                react(d, placeId, PlaceReactionType.NOPE);
                leave(a);
                leave(d);

                PlaceCandidateItem card = getBoard(b).candidates().get(0);

                assertThat(card.agreeCount()).isEqualTo(1);
                assertThat(card.agreedBy()).containsExactly(new MemberRef(b.getId(), "B"));
                assertThat(card.stage()).isEqualTo(PlaceStage.COMPLEMENT);
                assertThat(card.nopeCount()).isZero();
                assertThat(card.nopedBy()).isEmpty();
            }

            @Test
            @DisplayName("후보가 없으면 빈 목록이다")
            void emptyBoard() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");

                PlaceBoardResponse board = getBoard(minsu);

                assertThat(board.candidates()).isEmpty();
                assertThat(board.activeMemberCount()).isEqualTo(1);
            }

            @Test
            @DisplayName("상세는 남아 있는 멤버 전원을 들어온 순서로 담고, 각자 가고 싶어요, 별로예요, 아직 안 고름(null)을 보여 준다")
            void detailShowsEveryMembersReaction() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                RoomMember cheolsu = saveMember(room, "철수");
                RoomMember younghee = saveMember(room, "영희");
                long placeId = register(minsu, "place-1").placeId();
                react(jiyoung, placeId, PlaceReactionType.WANT);
                react(cheolsu, placeId, PlaceReactionType.NOPE);

                PlaceDetailResponse detail = getDetail(minsu, placeId);

                assertThat(detail.reactions())
                        .containsExactly(
                                new MemberReaction(
                                        minsu.getId(),
                                        "민수",
                                        new ReactionLabel(PlaceReactionType.WANT, "가고 싶어요")),
                                new MemberReaction(
                                        jiyoung.getId(),
                                        "지영",
                                        new ReactionLabel(PlaceReactionType.WANT, "가고 싶어요")),
                                new MemberReaction(
                                        cheolsu.getId(),
                                        "철수",
                                        new ReactionLabel(PlaceReactionType.NOPE, "별로예요")),
                                new MemberReaction(younghee.getId(), "영희", null));
                assertThat(detail.agreeCount()).isEqualTo(2);
                assertThat(detail.activeMemberCount()).isEqualTo(4);
                assertThat(detail.stage()).isEqualTo(PlaceStage.PARTIAL);
            }

            @Test
            @DisplayName("상세에 장소 정보, 올린 사람, 등록 시각이 채워지고 댓글이 없으면 댓글 수는 0이다")
            void detailFillsPlaceInfo() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();

                PlaceDetailResponse detail = getDetail(minsu, placeId);

                assertThat(detail.placeId()).isEqualTo(placeId);
                assertThat(detail.googlePlaceId()).isEqualTo("place-1");
                assertThat(detail.name()).isEqualTo("광장시장");
                assertThat(detail.placeType()).isEqualTo("market");
                assertThat(detail.address()).isEqualTo("서울 종로구");
                assertThat(detail.googleMapsUrl()).isEqualTo("https://maps.google.com/?cid=1");
                assertThat(detail.suggestedBy()).isEqualTo(new MemberRef(minsu.getId(), "민수"));
                assertThat(detail.registeredAt()).isNotNull();
                assertThat(detail.confirmedAt()).isNull();
                assertThat(detail.commentCount()).isZero();
            }

            @Test
            @DisplayName("상세의 댓글 수는 그 후보에 달린 댓글만 센다")
            void detailCountsComments() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();
                long otherPlaceId = register(minsu, "place-2").placeId();
                comment(minsu, placeId, "좋아요");
                comment(minsu, placeId, "저도요");
                comment(minsu, otherPlaceId, "여긴 별로");

                assertThat(getDetail(minsu, placeId).commentCount()).isEqualTo(2);
            }

            @Test
            @DisplayName("상세에서 나간 멤버는 목록과 동의 수에서 빠져서, 나간 사람의 가고 싶어요로 FULL이 되지 않는다")
            void detailIgnoresLeftMembers() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember a = saveMember(room, "A");
                RoomMember b = saveMember(room, "B");
                RoomMember c = saveMember(room, "C");
                long placeId = register(b, "place-1").placeId();
                react(a, placeId, PlaceReactionType.WANT);
                leave(a);

                PlaceDetailResponse detail = getDetail(b, placeId);

                assertThat(detail.reactions())
                        .extracting(MemberReaction::memberId)
                        .containsExactly(b.getId(), c.getId());
                assertThat(detail.agreeCount()).isEqualTo(1);
                assertThat(detail.activeMemberCount()).isEqualTo(2);
                assertThat(detail.stage()).isEqualTo(PlaceStage.COMPLEMENT);
            }

            @Test
            @DisplayName("확정된 후보의 상세는 CONFIRMED이고 확정 시각이 있다")
            void detailOfConfirmed() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();
                confirm(placeId);

                PlaceDetailResponse detail = getDetail(minsu, placeId);

                assertThat(detail.stage()).isEqualTo(PlaceStage.CONFIRMED);
                assertThat(detail.confirmedAt()).isNotNull();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("다른 방의 멤버면 FORBIDDEN으로 막는다")
            void rejectsNonMember() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember outsider = saveMember(otherRoom, "민수");

                assertThatThrownBy(() ->
                                placeCandidateService.getBoard(
                                        room.getId(), outsider.getUser().getId()))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
            }

            @Test
            @DisplayName("방을 나간 멤버면 FORBIDDEN으로 막는다")
            void rejectsLeftMember() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember member = saveMember(room, "민수");
                leave(member);

                assertThatThrownBy(() -> getBoard(member))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
            }

            @Test
            @DisplayName("다른 방의 후보 상세를 열면 PLACE_CANDIDATE_NOT_FOUND다")
            void detailRejectsCandidateOfOtherRoom() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                long otherPlaceId = register(saveMember(otherRoom, "철수"), "place-1").placeId();

                assertThatThrownBy(() -> getDetail(minsu, otherPlaceId))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(
                                                        PlaceErrorCode.PLACE_CANDIDATE_NOT_FOUND));
            }

            @Test
            @DisplayName("없는 후보 상세를 열면 PLACE_CANDIDATE_NOT_FOUND다")
            void detailRejectsUnknownCandidate() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");

                assertThatThrownBy(() -> getDetail(minsu, 999_999L))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(
                                                        PlaceErrorCode.PLACE_CANDIDATE_NOT_FOUND));
            }

            @Test
            @DisplayName("다른 방의 멤버가 상세를 열면 FORBIDDEN이다")
            void detailRejectsNonMember() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                long placeId = register(saveMember(room, "민수"), "place-1").placeId();
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember outsider = saveMember(otherRoom, "철수");

                assertThatThrownBy(() ->
                                placeCandidateService.getDetail(
                                        room.getId(), placeId, outsider.getUser().getId()))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
            }
        }
    }

    private PlaceDetailResponse getDetail(RoomMember member, long placeId) {
        return placeCandidateService.getDetail(
                member.getRoom().getId(), placeId, member.getUser().getId());
    }

    private void comment(RoomMember member, long placeId, String body) {
        placeCandidateService.addComment(
                member.getRoom().getId(),
                placeId,
                member.getUser().getId(),
                new CommentRequest(body));
    }

    private PlaceBoardResponse getBoard(RoomMember member) {
        return placeCandidateService.getBoard(member.getRoom().getId(), member.getUser().getId());
    }

    private void react(RoomMember member, Long placeId, PlaceReactionType reaction) {
        PlaceCandidate candidate = placeCandidateRepository.findById(placeId).orElseThrow();
        placeReactionRepository.saveAndFlush(PlaceReaction.create(candidate, member, reaction));
    }

    private void confirm(Long placeId) {
        PlaceCandidate candidate = placeCandidateRepository.findById(placeId).orElseThrow();
        candidate.confirm(clock);
        placeCandidateRepository.saveAndFlush(candidate);
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
