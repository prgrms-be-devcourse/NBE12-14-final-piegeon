package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.comment.dto.CommentRequest;
import world.chakchak.domain.place.dto.GooglePlace;
import world.chakchak.domain.place.dto.MemberRef;
import world.chakchak.domain.place.dto.NotFullyAgreedData;
import world.chakchak.domain.place.dto.PlaceAlreadyRegisteredData;
import world.chakchak.domain.place.dto.PlaceBoardResponse;
import world.chakchak.domain.place.dto.PlaceCandidateItem;
import world.chakchak.domain.place.dto.PlaceCategory;
import world.chakchak.domain.place.dto.PlaceConfirmResponse;
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
 * P-02 장소 후보 등록, P-03 칸반 보드, P-04·P-05 반응, P-06 확정, P-07 삭제(코멘트 작성과 겹칠 때 포함), P-10 상세, P-11 사진. 실제
 * MySQL에서 방 잠금과 저장, 조회를 확인한다.
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
    @Autowired private PlatformTransactionManager transactionManager;

    // 다른 트랜잭션이 잠금이나 저장한 줄을 쥐고 있는 시간
    private static final long HOLD_MILLIS = 500;
    private static final long WAIT_SECONDS = 10;

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
                assertThat(detail.category())
                        .isEqualTo(
                                new PlaceDetailResponse.CategoryLabel(
                                        PlaceCategory.SHOPPING, "쇼핑·투어"));
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

    @Nested
    class React {

        @Nested
        class Success {

            @Test
            @DisplayName("처음 누르면 내 반응이 저장되고, 응답 상세에 내 반응이 보인다")
            void savesFirstReaction() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();

                PlaceDetailResponse detail = press(jiyoung, placeId, PlaceReactionType.NOPE);

                assertThat(detail.reactions())
                        .containsExactly(
                                new MemberReaction(
                                        minsu.getId(),
                                        "민수",
                                        ReactionLabel.of(PlaceReactionType.WANT)),
                                new MemberReaction(
                                        jiyoung.getId(),
                                        "지영",
                                        ReactionLabel.of(PlaceReactionType.NOPE)));
                assertThat(reactionsOf(placeId)).hasSize(2);
            }

            @Test
            @DisplayName("다른 버튼을 누르면 새로 쌓이지 않고 내 반응이 바뀐다")
            void changesReaction() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();
                press(jiyoung, placeId, PlaceReactionType.WANT);

                PlaceDetailResponse detail = press(jiyoung, placeId, PlaceReactionType.NOPE);

                assertThat(detail.agreeCount()).isEqualTo(1);
                assertThat(detail.reactions().get(1).reaction())
                        .isEqualTo(ReactionLabel.of(PlaceReactionType.NOPE));
                assertThat(reactionsOf(placeId)).hasSize(2);
            }

            @Test
            @DisplayName("같은 버튼을 두 번 누르면 반응도 변경 이력도 그대로다")
            void sameReactionTwiceChangesNothing() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();
                press(jiyoung, placeId, PlaceReactionType.WANT);

                PlaceDetailResponse detail = press(jiyoung, placeId, PlaceReactionType.WANT);

                assertThat(detail.stage()).isEqualTo(PlaceStage.FULL);
                assertThat(reactionsOf(placeId)).hasSize(2);
                assertThat(stageLogs(room)).hasSize(1);
            }

            @Test
            @DisplayName("취소하면 내 반응이 지워지고 상세에서 아직 안 고름(null)이 된다")
            void cancelRemovesReaction() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();
                press(jiyoung, placeId, PlaceReactionType.NOPE);

                PlaceDetailResponse detail = cancel(jiyoung, placeId);

                assertThat(detail.reactions().get(1))
                        .isEqualTo(new MemberReaction(jiyoung.getId(), "지영", null));
                assertThat(reactionsOf(placeId)).hasSize(1);
            }

            @Test
            @DisplayName("누른 게 없을 때 취소해도 성공하고 아무것도 바뀌지 않는다")
            void cancelWithoutReactionSucceeds() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();

                PlaceDetailResponse detail = cancel(jiyoung, placeId);

                assertThat(detail.reactions().get(1).reaction()).isNull();
                assertThat(reactionsOf(placeId)).hasSize(1);
                assertThat(stageLogs(room)).isEmpty();
            }

            @Test
            @DisplayName("칸이 바뀌면 변경 이력에 '장소: 전 칸 → 새 칸'이 남는다")
            void logsStageChange() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();

                assertThat(press(jiyoung, placeId, PlaceReactionType.WANT).stage())
                        .isEqualTo(PlaceStage.FULL);
                assertThat(cancel(jiyoung, placeId).stage()).isEqualTo(PlaceStage.COMPLEMENT);

                assertThat(stageLogs(room))
                        .containsExactly(
                                "광장시장: 한 명만 원해요 → 다 같이 가고 싶어요", "광장시장: 다 같이 가고 싶어요 → 한 명만 원해요");
            }

            @Test
            @DisplayName("칸이 그대로면 변경 이력을 남기지 않는다")
            void noLogWhenStageStays() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                saveMember(room, "철수");
                long placeId = register(minsu, "place-1").placeId();

                PlaceDetailResponse detail = press(jiyoung, placeId, PlaceReactionType.NOPE);

                assertThat(detail.stage()).isEqualTo(PlaceStage.COMPLEMENT);
                assertThat(stageLogs(room)).isEmpty();
            }

            @Test
            @DisplayName("나간 사람의 가고 싶어요는 바뀌기 전 칸 계산에도 들어가지 않는다")
            void leftMemberNotCountedBeforeChange() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                RoomMember chulsu = saveMember(room, "철수");
                long placeId = register(minsu, "place-1").placeId();
                react(chulsu, placeId, PlaceReactionType.WANT);
                leave(chulsu);

                press(jiyoung, placeId, PlaceReactionType.WANT);

                // 철수를 세면 바뀌기 전이 "몇 명이 좋아해요"가 된다
                assertThat(stageLogs(room)).containsExactly("광장시장: 한 명만 원해요 → 다 같이 가고 싶어요");
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("확정된 장소에는 누를 수도 취소할 수도 없다 (PLACE_ALREADY_CONFIRMED)")
            void rejectsConfirmedPlace() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();
                confirm(placeId);

                assertThatThrownBy(() -> press(minsu, placeId, PlaceReactionType.NOPE))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(PlaceErrorCode.PLACE_ALREADY_CONFIRMED));
                assertThatThrownBy(() -> cancel(minsu, placeId))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(PlaceErrorCode.PLACE_ALREADY_CONFIRMED));
            }

            @Test
            @DisplayName("방이 PLANNING이 아니면 INVALID_STATUS다")
            void rejectsWhenNotPlanning() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();
                jdbcTemplate.update(
                        "UPDATE plan_room SET status = 'REVIEWING' WHERE id = ?", room.getId());

                assertThatThrownBy(() -> press(minsu, placeId, PlaceReactionType.NOPE))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.INVALID_STATUS));
            }

            @Test
            @DisplayName("PLANNING이 아닌 방에서는 없는 후보를 눌러도 방 상태부터 막혀 INVALID_STATUS다")
            void checksStatusBeforeCandidate() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                jdbcTemplate.update(
                        "UPDATE plan_room SET status = 'REVIEWING' WHERE id = ?", room.getId());

                assertThatThrownBy(() -> press(minsu, 999_999L, PlaceReactionType.WANT))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.INVALID_STATUS));
            }

            @Test
            @DisplayName("다른 방의 후보에 누르면 PLACE_CANDIDATE_NOT_FOUND다")
            void rejectsCandidateOfOtherRoom() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                long otherPlaceId = register(saveMember(otherRoom, "철수"), "place-1").placeId();

                assertThatThrownBy(() -> press(minsu, otherPlaceId, PlaceReactionType.WANT))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(
                                                        PlaceErrorCode.PLACE_CANDIDATE_NOT_FOUND));
            }

            @Test
            @DisplayName("다른 방의 멤버가 누르면 FORBIDDEN이다")
            void rejectsNonMember() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                long placeId = register(saveMember(room, "민수"), "place-1").placeId();
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember outsider = saveMember(otherRoom, "철수");

                assertThatThrownBy(() ->
                                placeCandidateService.react(
                                        room.getId(),
                                        placeId,
                                        outsider.getUser().getId(),
                                        PlaceReactionType.WANT))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
            }
        }
    }

    @Nested
    class Confirm {

        @Nested
        class Success {

            @Test
            @DisplayName("남아 있는 사람 전원이 가고 싶어요면 확정되고, 확정 시각과 변경 이력이 남는다")
            void confirmsWhenEveryoneWants() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();
                react(jiyoung, placeId, PlaceReactionType.WANT);
                LocalDateTime before = LocalDateTime.now(clock);

                PlaceConfirmResponse response = pressConfirm(jiyoung, placeId);

                assertThat(response.placeId()).isEqualTo(placeId);
                assertThat(response.confirmedAt()).isBetween(before, LocalDateTime.now(clock));
                assertThat(placeCandidateRepository.findById(placeId).orElseThrow().isConfirmed())
                        .isTrue();
                assertThat(confirmLogs(room)).containsExactly("광장시장: 확정됐어요");
            }

            @Test
            @DisplayName("이미 확정된 장소면 새 멤버가 들어와도 같은 확정 시각으로 성공하고 이력은 더 남지 않는다")
            void confirmedPlaceStaysConfirmed() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();
                pressConfirm(minsu, placeId);
                saveMember(room, "지영");

                PlaceConfirmResponse again = pressConfirm(minsu, placeId);

                assertThat(again.confirmedAt())
                        .isEqualTo(
                                placeCandidateRepository
                                        .findById(placeId)
                                        .orElseThrow()
                                        .getConfirmedAt());
                assertThat(confirmLogs(room)).hasSize(1);
            }

            @Test
            @DisplayName("나간 사람은 인원에서 빠져서, 남은 사람 전원이 가고 싶어요면 확정된다")
            void ignoresLeftMembers() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                RoomMember chulsu = saveMember(room, "철수");
                long placeId = register(minsu, "place-1").placeId();
                react(jiyoung, placeId, PlaceReactionType.WANT);
                leave(chulsu);

                assertThat(pressConfirm(minsu, placeId).confirmedAt()).isNotNull();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("아직 안 누른 사람이 있으면 NOT_FULLY_AGREED와 누른 수·인원을 주고 확정하지 않는다")
            void rejectsWhenSomeoneHasNotPressed() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                saveMember(room, "지영");
                saveMember(room, "철수");
                long placeId = register(minsu, "place-1").placeId();

                assertThatThrownBy(() -> pressConfirm(minsu, placeId))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> {
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(PlaceErrorCode.NOT_FULLY_AGREED);
                                    assertThat(e.getData()).isEqualTo(new NotFullyAgreedData(1, 3));
                                });
                assertThat(placeCandidateRepository.findById(placeId).orElseThrow().isConfirmed())
                        .isFalse();
                assertThat(confirmLogs(room)).isEmpty();
            }

            @Test
            @DisplayName("별로예요를 누른 사람이 있으면 NOT_FULLY_AGREED다")
            void rejectsWhenSomeoneNopes() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();
                react(jiyoung, placeId, PlaceReactionType.NOPE);

                assertThatThrownBy(() -> pressConfirm(minsu, placeId))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getData())
                                                .isEqualTo(new NotFullyAgreedData(1, 2)));
            }

            @Test
            @DisplayName("방이 PLANNING이 아니면 INVALID_STATUS다")
            void rejectsWhenNotPlanning() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();
                jdbcTemplate.update(
                        "UPDATE plan_room SET status = 'REVIEWING' WHERE id = ?", room.getId());

                assertThatThrownBy(() -> pressConfirm(minsu, placeId))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.INVALID_STATUS));
            }

            @Test
            @DisplayName("다른 방의 후보를 확정하면 PLACE_CANDIDATE_NOT_FOUND다")
            void rejectsCandidateOfOtherRoom() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                long otherPlaceId = register(saveMember(otherRoom, "철수"), "place-1").placeId();

                assertThatThrownBy(() -> pressConfirm(minsu, otherPlaceId))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(
                                                        PlaceErrorCode.PLACE_CANDIDATE_NOT_FOUND));
            }

            @Test
            @DisplayName("다른 방의 멤버가 확정하면 FORBIDDEN이다")
            void rejectsNonMember() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                long placeId = register(saveMember(room, "민수"), "place-1").placeId();
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember outsider = saveMember(otherRoom, "철수");

                assertThatThrownBy(() ->
                                placeCandidateService.confirm(
                                        room.getId(), placeId, outsider.getUser().getId()))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
            }
        }
    }

    @Nested
    class Delete {

        @Nested
        class Success {

            @Test
            @DisplayName("후보를 지우면 그 후보의 반응과 코멘트도 함께 지워진다")
            void deletesCandidateWithReactionsAndComments() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();
                react(jiyoung, placeId, PlaceReactionType.NOPE);
                comment(jiyoung, placeId, "너무 멀어요");
                long otherPlaceId = register(minsu, "place-2").placeId();
                comment(jiyoung, otherPlaceId, "좋아요");

                pressDelete(minsu, placeId);

                assertThat(placeCandidateRepository.findById(placeId)).isEmpty();
                assertThat(reactionsOf(placeId)).isEmpty();
                assertThat(commentCountOf(placeId)).isZero();
                // 다른 후보는 그대로
                assertThat(reactionsOf(otherPlaceId)).hasSize(1);
                assertThat(commentCountOf(otherPlaceId)).isEqualTo(1);
            }

            @Test
            @DisplayName("다른 사람이 코멘트를 저장하는 중에 지우면, 저장이 끝나길 기다렸다가 그 코멘트까지 지운다")
            void waitsForCommentBeingSaved() throws java.lang.Exception {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();
                CountDownLatch saved = new CountDownLatch(1);
                ExecutorService pool = Executors.newSingleThreadExecutor();
                try {
                    // 코멘트를 저장하고 커밋하기 전에 잠시 머문다
                    Future<?> commenting =
                            pool.submit(() ->
                                    new TransactionTemplate(transactionManager)
                                            .executeWithoutResult(status -> {
                                                comment(jiyoung, placeId, "늦게 단 코멘트");
                                                saved.countDown();
                                                sleep(HOLD_MILLIS);
                                            }));
                    assertThat(saved.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

                    pressDelete(minsu, placeId);

                    commenting.get(WAIT_SECONDS, TimeUnit.SECONDS);
                } finally {
                    pool.shutdownNow();
                }
                assertThat(placeCandidateRepository.findById(placeId)).isEmpty();
                assertThat(commentCountOf(placeId)).isZero();
            }

            @Test
            @DisplayName("올린 사람이 아니어도 방 멤버면 지울 수 있다")
            void anyMemberCanDelete() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();

                pressDelete(jiyoung, placeId);

                assertThat(placeCandidateRepository.findById(placeId)).isEmpty();
            }

            @Test
            @DisplayName("지운 장소는 같은 방에 다시 올릴 수 있다")
            void canRegisterAgainAfterDelete() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();

                pressDelete(minsu, placeId);
                PlaceCandidateItem again = register(minsu, "place-1");

                assertThat(again.placeId()).isNotEqualTo(placeId);
                assertThat(placeCandidateRepository.countByRoom(room)).isEqualTo(1);
            }

            @Test
            @DisplayName("지우면 '광장시장: 삭제됐어요' 변경 이력이 남는다")
            void recordsChangeLog() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();

                pressDelete(minsu, placeId);

                assertThat(deleteLogs(room)).containsExactly("광장시장: 삭제됐어요");
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("방이 PLANNING이 아니면 INVALID_STATUS이고 후보는 그대로다")
            void rejectsWhenNotPlanning() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();
                jdbcTemplate.update(
                        "UPDATE plan_room SET status = 'REVIEWING' WHERE id = ?", room.getId());

                assertThatThrownBy(() -> pressDelete(minsu, placeId))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.INVALID_STATUS));
                assertThat(placeCandidateRepository.findById(placeId)).isPresent();
            }

            @Test
            @DisplayName("확정된 장소는 PLACE_ALREADY_CONFIRMED로 막고 반응도 그대로 둔다")
            void rejectsConfirmedPlace() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();
                confirm(placeId);

                assertThatThrownBy(() -> pressDelete(minsu, placeId))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(PlaceErrorCode.PLACE_ALREADY_CONFIRMED));
                assertThat(placeCandidateRepository.findById(placeId)).isPresent();
                assertThat(reactionsOf(placeId)).hasSize(1);
                assertThat(deleteLogs(room)).isEmpty();
            }

            @Test
            @DisplayName("다른 방의 후보를 지우면 PLACE_CANDIDATE_NOT_FOUND이고 그 후보는 그대로다")
            void rejectsCandidateOfOtherRoom() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                long otherPlaceId = register(saveMember(otherRoom, "철수"), "place-1").placeId();

                assertThatThrownBy(() -> pressDelete(minsu, otherPlaceId))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(
                                                        PlaceErrorCode.PLACE_CANDIDATE_NOT_FOUND));
                assertThat(placeCandidateRepository.findById(otherPlaceId)).isPresent();
            }

            @Test
            @DisplayName("지우는 동안 기다리던 코멘트 작성은 후보가 사라졌으므로 500이 아니라 PLACE_CANDIDATE_NOT_FOUND이다")
            void rejectsCommentWaitingForDelete() throws java.lang.Exception {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                RoomMember jiyoung = saveMember(room, "지영");
                long placeId = register(minsu, "place-1").placeId();
                CountDownLatch locked = new CountDownLatch(1);
                ExecutorService pool = Executors.newSingleThreadExecutor();
                try {
                    // 삭제처럼 후보를 잠그고 잠시 머문 뒤 지운다
                    Future<?> deleting =
                            pool.submit(() ->
                                    new TransactionTemplate(transactionManager)
                                            .executeWithoutResult(status -> {
                                                PlaceCandidate candidate =
                                                        placeCandidateRepository
                                                                .findByIdForUpdate(placeId)
                                                                .orElseThrow();
                                                locked.countDown();
                                                sleep(HOLD_MILLIS);
                                                // 등록하면 올린 사람의 반응이 생기므로 반응부터 지운다
                                                placeReactionRepository.deleteAllByPlaceCandidate(
                                                        candidate);
                                                placeCandidateRepository.delete(candidate);
                                            }));
                    assertThat(locked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

                    assertThatThrownBy(() -> comment(jiyoung, placeId, "늦은 코멘트"))
                            .isInstanceOfSatisfying(
                                    BusinessException.class,
                                    e ->
                                            assertThat(e.getErrorCode())
                                                    .isEqualTo(
                                                            PlaceErrorCode
                                                                    .PLACE_CANDIDATE_NOT_FOUND));
                    deleting.get(WAIT_SECONDS, TimeUnit.SECONDS);
                } finally {
                    pool.shutdownNow();
                }
                assertThat(commentCountOf(placeId)).isZero();
            }

            @Test
            @DisplayName("다른 방의 멤버가 지우면 FORBIDDEN이고 후보는 그대로다")
            void rejectsNonMember() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                long placeId = register(saveMember(room, "민수"), "place-1").placeId();
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember outsider = saveMember(otherRoom, "철수");

                assertThatThrownBy(() ->
                                placeCandidateService.delete(
                                        room.getId(), placeId, outsider.getUser().getId()))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
                assertThat(placeCandidateRepository.findById(placeId)).isPresent();
            }
        }
    }

    @Nested
    class Photo {

        @Nested
        class Success {

            @Test
            @DisplayName("저장된 사진 이름으로 Google에서 받은 사진 주소를 돌려준다")
            void returnsGooglePhotoUri() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();
                given(placeSearchClient.findPhotoUri("places/place-1/photos/p1"))
                        .willReturn(Optional.of("https://lh3.googleusercontent.com/a"));

                assertThat(photoUri(minsu, placeId))
                        .isEqualTo("https://lh3.googleusercontent.com/a");
            }

            @Test
            @DisplayName("상세의 photoUrl은 사진이 있으면 사진 API 주소, 없으면 null이다")
            void detailHasPhotoUrlOnlyWhenPhotoSaved() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long withPhoto = register(minsu, "place-1").placeId();
                long noPhoto = register(minsu, "place-2").placeId();
                removePhoto(noPhoto);

                assertThat(getDetail(minsu, withPhoto).photoUrl())
                        .isEqualTo(
                                "/api/rooms/"
                                        + room.getId()
                                        + "/place-candidates/"
                                        + withPhoto
                                        + "/photo");
                assertThat(getDetail(minsu, noPhoto).photoUrl()).isNull();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("저장된 사진이 없으면 Google을 부르지 않고 PLACE_PHOTO_NOT_FOUND다")
            void rejectsWhenNoPhotoSaved() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();
                removePhoto(placeId);

                assertThatThrownBy(() -> photoUri(minsu, placeId))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(PlaceErrorCode.PLACE_PHOTO_NOT_FOUND));
                verify(placeSearchClient, never()).findPhotoUri(anyString());
            }

            @Test
            @DisplayName("Google에서 사진이 사라졌으면 PLACE_PHOTO_NOT_FOUND다")
            void rejectsWhenGoogleHasNoPhoto() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                long placeId = register(minsu, "place-1").placeId();
                given(placeSearchClient.findPhotoUri(anyString())).willReturn(Optional.empty());

                assertThatThrownBy(() -> photoUri(minsu, placeId))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(PlaceErrorCode.PLACE_PHOTO_NOT_FOUND));
            }

            @Test
            @DisplayName("다른 방의 후보 사진이면 PLACE_CANDIDATE_NOT_FOUND이고 Google을 부르지 않는다")
            void rejectsCandidateOfOtherRoom() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember minsu = saveMember(room, "민수");
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                long otherPlaceId = register(saveMember(otherRoom, "철수"), "place-1").placeId();

                assertThatThrownBy(() -> photoUri(minsu, otherPlaceId))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(
                                                        PlaceErrorCode.PLACE_CANDIDATE_NOT_FOUND));
                verify(placeSearchClient, never()).findPhotoUri(anyString());
            }

            @Test
            @DisplayName("다른 방의 멤버가 사진을 보면 FORBIDDEN이다")
            void rejectsNonMember() {
                Room room = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                long placeId = register(saveMember(room, "민수"), "place-1").placeId();
                Room otherRoom = saveRoom(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 3));
                RoomMember outsider = saveMember(otherRoom, "철수");

                assertThatThrownBy(() ->
                                placeCandidateService.getPhotoUri(
                                        room.getId(), placeId, outsider.getUser().getId()))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
            }
        }
    }

    private String photoUri(RoomMember member, long placeId) {
        return placeCandidateService.getPhotoUri(
                member.getRoom().getId(), placeId, member.getUser().getId());
    }

    // 사진 없이 등록된 장소로 만든다
    private void removePhoto(long placeId) {
        jdbcTemplate.update("UPDATE place_candidate SET photo_name = NULL WHERE id = ?", placeId);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void pressDelete(RoomMember member, long placeId) {
        placeCandidateService.delete(member.getRoom().getId(), placeId, member.getUser().getId());
    }

    // 이 방의 삭제 이력 문구, 남긴 순서대로
    private List<String> deleteLogs(Room room) {
        return jdbcTemplate.queryForList(
                "SELECT summary FROM change_log WHERE plan_room_id = ?"
                        + " AND event_kind = 'PLACE_DELETED' ORDER BY id",
                String.class,
                room.getId());
    }

    private long commentCountOf(long placeId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comment WHERE place_candidate_id = ?", Long.class, placeId);
    }

    private PlaceConfirmResponse pressConfirm(RoomMember member, long placeId) {
        return placeCandidateService.confirm(
                member.getRoom().getId(), placeId, member.getUser().getId());
    }

    // 이 방의 확정 이력 문구, 남긴 순서대로
    private List<String> confirmLogs(Room room) {
        return jdbcTemplate.queryForList(
                "SELECT summary FROM change_log WHERE plan_room_id = ?"
                        + " AND event_kind = 'PLACE_CONFIRMED' ORDER BY id",
                String.class,
                room.getId());
    }

    private PlaceDetailResponse press(RoomMember member, long placeId, PlaceReactionType type) {
        return placeCandidateService.react(
                member.getRoom().getId(), placeId, member.getUser().getId(), type);
    }

    private PlaceDetailResponse cancel(RoomMember member, long placeId) {
        return placeCandidateService.cancelReaction(
                member.getRoom().getId(), placeId, member.getUser().getId());
    }

    // 이 방의 칸 변경 이력 문구, 남긴 순서대로
    private List<String> stageLogs(Room room) {
        return jdbcTemplate.queryForList(
                "SELECT summary FROM change_log WHERE plan_room_id = ?"
                        + " AND event_kind = 'PLACE_STAGE_CHANGED' ORDER BY id",
                String.class,
                room.getId());
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
