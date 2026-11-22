package world.chakchak.domain.place.service;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.service.ChangeLogService;
import world.chakchak.domain.comment.dto.CommentListResponse;
import world.chakchak.domain.comment.dto.CommentRequest;
import world.chakchak.domain.comment.dto.CommentResponse;
import world.chakchak.domain.comment.service.CommentService;
import world.chakchak.domain.place.dto.GooglePlace;
import world.chakchak.domain.place.dto.MemberRef;
import world.chakchak.domain.place.dto.PlaceAlreadyRegisteredData;
import world.chakchak.domain.place.dto.PlaceBoardResponse;
import world.chakchak.domain.place.dto.PlaceCandidateItem;
import world.chakchak.domain.place.dto.PlaceCategory;
import world.chakchak.domain.place.dto.PlaceDetailResponse;
import world.chakchak.domain.place.dto.PlaceLimitExceededData;
import world.chakchak.domain.place.dto.PlaceStage;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.entity.PlaceReaction;
import world.chakchak.domain.place.entity.PlaceReactionType;
import world.chakchak.domain.place.errorcode.PlaceErrorCode;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.place.repository.PlaceReactionRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

/** 장소 후보 기능. (P-02 등록, P-03 칸반 보드, P-04, P-05 반응, P-08, P-09 장소 코멘트, P-10 상세) */
@Service
@RequiredArgsConstructor
public class PlaceCandidateService {

    // 여행 하루에 올릴 수 있는 후보 수 (한도 = 여행 일수 x 10)
    private static final int CANDIDATES_PER_DAY = 10;

    // 칸반 정렬: 칸(확정 -> 전체 -> 부분 -> 여집합) -> 가고 싶어요 많은 순 -> 먼저 올린 순
    private static final Comparator<PlaceCandidateItem> BOARD_ORDER =
            Comparator.comparing(PlaceCandidateItem::stage, Comparator.reverseOrder())
                    .thenComparing(PlaceCandidateItem::agreeCount, Comparator.reverseOrder())
                    .thenComparing(PlaceCandidateItem::placeId);

    private final RoomService roomService;
    private final PlatformTransactionManager transactionManager;
    private final PlaceSearchClient placeSearchClient;
    private final RoomMemberService roomMemberService;
    private final PlaceCandidateRepository placeCandidateRepository;
    private final PlaceReactionRepository placeReactionRepository;
    private final CommentService commentService;
    private final ChangeLogService changeLogService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteReactionsByMember(Long memberId) {
        placeReactionRepository.deleteAllByMemberId(memberId);
    }

    /**
     * 장소 후보를 등록하고, 올린 사람의 "가고 싶어요"를 함께 남긴다. (P-02)
     *
     * <p>@Transactional을 붙이지 않는다. Google 조회까지 트랜잭션에 넣으면 외부 API를 기다리는 동안 방 잠금을 잡고 있게 된다. 그래서 Google
     * 조회는 트랜잭션 밖에서 하고, 잠금부터 저장까지만 TransactionTemplate으로 묶는다.
     *
     * @throws BusinessException Google에 없는 장소면 PLACE_NOT_FOUND, 방이 없으면 ROOM_NOT_FOUND, 활동 멤버가 아니면
     *     FORBIDDEN, 방이 PLANNING이 아니면 INVALID_STATUS, 한도를 넘으면 PLACE_LIMIT_EXCEEDED, 이미 올라온 장소면
     *     PLACE_ALREADY_REGISTERED
     */
    public PlaceCandidateItem register(Long roomId, Long userId, String googlePlaceId) {
        GooglePlace place =
                placeSearchClient
                        .findById(googlePlaceId)
                        .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));

        return new TransactionTemplate(transactionManager)
                .execute(status -> registerInTransaction(roomId, userId, place));
    }

    // 잠금 → 멤버 확인 → 상태 확인 → 한도·중복 확인 → 저장 (명세 1-4)
    private PlaceCandidateItem registerInTransaction(Long roomId, Long userId, GooglePlace place) {
        // 반드시 첫 쿼리. 방을 먼저 읽어 두면 잠금만 걸리고 값은 새로 읽지 않아 낡은 상태로 판단하게 된다.
        // 방이 없으면 getForUpdate가 ROOM_NOT_FOUND를 던진다
        Room room = roomService.getForUpdate(roomId);
        // 잠금 안에서 확인해야 확인 직후 방을 나간 사람의 등록을 막을 수 있다
        RoomMember member = roomMemberService.getActiveMember(roomId, userId);

        // 후보는 일정 조율(PLANNING) 중에만 올릴 수 있다. 잠금 안에서 봐야 그 사이 바뀐 상태를 놓치지 않는다
        if (room.getStatus() != RoomStatus.PLANNING) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS);
        }

        long limit = limitOf(room);
        if (placeCandidateRepository.countByRoom(room) >= limit) {
            throw new BusinessException(
                    PlaceErrorCode.PLACE_LIMIT_EXCEEDED, new PlaceLimitExceededData(limit));
        }

        Optional<PlaceCandidate> existing =
                placeCandidateRepository.findByRoomAndGooglePlaceId(room, place.googlePlaceId());
        if (existing.isPresent()) {
            throw new BusinessException(
                    PlaceErrorCode.PLACE_ALREADY_REGISTERED,
                    new PlaceAlreadyRegisteredData(existing.get().getId()));
        }

        PlaceCandidate candidate =
                placeCandidateRepository.save(
                        PlaceCandidate.create(
                                room,
                                place.googlePlaceId(),
                                place.name(),
                                place.placeType(),
                                place.address(),
                                place.googleMapsUrl(),
                                place.photoName(),
                                member));
        placeReactionRepository.save(
                PlaceReaction.create(candidate, member, PlaceReactionType.WANT));

        // TODO change_log PLACE_REGISTERED 기록 (PR #32에서 ChangeLogService가 빠짐. 기록 방식이 다시 정해지면 추가)

        // TODO 커밋 후 SSE place.registered (data는 이 메서드의 응답과 같은 객체)

        // 칸 계산용 인원도 잠금 안에서 센다. 잠금 밖에서 세면 그 사이 들어오거나 나간 사람이 반영되지 않는다.
        long activeMemberCount = roomMemberService.countActiveMembers(roomId);

        // user는 지연 로딩이라 트랜잭션 안에서 읽어야 한다 (open-in-view: false)
        MemberRef me = new MemberRef(member.getId(), member.getUser().getName());
        return new PlaceCandidateItem(
                candidate.getId(),
                candidate.getGooglePlaceId(),
                candidate.getName(),
                candidate.getAddress(),
                candidate.getPlaceType(),
                PlaceStage.of(false, 1, activeMemberCount),
                1,
                List.of(me),
                0,
                List.of(),
                me,
                candidate.getConfirmedAt());
    }

    /**
     * 장소 칸반 보드. 방의 후보 전부를 칸과 함께 돌려준다. (P-03)
     *
     * <p>읽기만 하므로 방 잠금을 잡지 않는다. 후보 목록과 가고 싶어요 목록을 한 번씩만 조회해서 메모리에서 합친다.
     *
     * @throws BusinessException 방이 없으면 ROOM_NOT_FOUND, 활동 멤버가 아니면 FORBIDDEN
     */
    @Transactional(readOnly = true)
    public PlaceBoardResponse getBoard(Long roomId, Long userId) {
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);
        Room room = me.getRoom();
        long activeMemberCount = roomMemberService.countActiveMembers(roomId);

        // 후보 id -> 그 반응을 누른 멤버들. 아무도 안 누른 후보는 맵에 없다
        Map<Long, List<MemberRef>> agreedByPlace = membersByPlace(room, PlaceReactionType.WANT);
        Map<Long, List<MemberRef>> nopedByPlace = membersByPlace(room, PlaceReactionType.NOPE);

        List<PlaceCandidateItem> candidates =
                placeCandidateRepository.findAllByRoom(room).stream()
                        .map(candidate ->
                                toItem(
                                        candidate,
                                        agreedByPlace.getOrDefault(candidate.getId(), List.of()),
                                        nopedByPlace.getOrDefault(candidate.getId(), List.of()),
                                        activeMemberCount))
                        .sorted(BOARD_ORDER)
                        .toList();

        return new PlaceBoardResponse(activeMemberCount, limitOf(room), candidates);
    }

    /**
     * 카드를 눌렀을 때 펼쳐지는 장소 상세. 남아 있는 멤버 전원의 반응을 함께 준다. (P-10)
     *
     * <p>읽기만 하므로 방 잠금을 잡지 않는다.
     *
     * @throws BusinessException 방이 없으면 ROOM_NOT_FOUND, 활동 멤버가 아니면 FORBIDDEN, 없거나 다른 방의 후보면
     *     PLACE_CANDIDATE_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public PlaceDetailResponse getDetail(Long roomId, Long placeId, Long userId) {
        roomMemberService.getActiveMember(roomId, userId);
        return toDetail(roomId, getCandidateInRoom(roomId, placeId));
    }

    /**
     * 상세 화면의 "나는 여기" 버튼. 가고 싶어요나 별로예요를 누른다. 이미 누른 게 있으면 바꾼다. (P-04)
     *
     * <p>같은 버튼을 다시 보내면 아무것도 바뀌지 않는다. 응답은 상세와 같은 모양이라 화면을 바로 갱신할 수 있다.
     *
     * @throws BusinessException 방이 없으면 ROOM_NOT_FOUND, 활동 멤버가 아니면 FORBIDDEN, 없거나 다른 방의 후보면
     *     PLACE_CANDIDATE_NOT_FOUND, 방이 PLANNING이 아니면 INVALID_STATUS, 확정된 후보면
     *     PLACE_ALREADY_CONFIRMED
     */
    @Transactional
    public PlaceDetailResponse react(
            Long roomId, Long placeId, Long userId, PlaceReactionType reaction) {
        // 반드시 첫 쿼리. 두 사람이 동시에 눌러도 칸 계산과 이력이 한 사람씩 차례로 처리된다
        Room room = roomService.getForUpdate(roomId);
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);
        PlaceCandidate candidate = getReactableCandidate(room, placeId);
        PlaceStage before = currentStage(candidate, roomId);

        placeReactionRepository
                .findByPlaceCandidateAndMember(candidate, me)
                .ifPresentOrElse(
                        mine -> mine.change(reaction),
                        () ->
                                placeReactionRepository.save(
                                        PlaceReaction.create(candidate, me, reaction)));

        return detailAfterChange(room, me, candidate, before);
    }

    /**
     * 누른 버튼을 다시 눌러 반응을 지운다. 누른 게 없어도 성공한다. (P-05)
     *
     * @throws BusinessException react와 같다
     */
    @Transactional
    public PlaceDetailResponse cancelReaction(Long roomId, Long placeId, Long userId) {
        Room room = roomService.getForUpdate(roomId);
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);
        PlaceCandidate candidate = getReactableCandidate(room, placeId);
        PlaceStage before = currentStage(candidate, roomId);

        placeReactionRepository
                .findByPlaceCandidateAndMember(candidate, me)
                .ifPresent(placeReactionRepository::delete);

        return detailAfterChange(room, me, candidate, before);
    }

    /** 장소 후보 코멘트 목록, 오래된 순. (P-08) */
    @Transactional(readOnly = true)
    public CommentListResponse getComments(Long roomId, Long placeId, Long userId) {
        roomMemberService.getActiveMember(roomId, userId);
        getCandidateInRoom(roomId, placeId);
        return new CommentListResponse(commentService.getOnPlace(placeId));
    }

    /**
     * 장소 후보에 코멘트를 남긴다. 확정된 장소에도 남길 수 있다. (P-09)
     *
     * <p>코멘트는 장소·방 상태를 바꾸지 않으므로 방 잠금을 잡지 않는다 (명세 V-06과 같음).
     */
    @Transactional
    public CommentResponse addComment(
            Long roomId, Long placeId, Long userId, CommentRequest request) {
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);
        // 명세 2-1: 코멘트는 확정 전 단계(SURVEYING·PLANNING·REVIEWING)에서만 받는다
        RoomStatus status = me.getRoom().getStatus();
        if (status != RoomStatus.SURVEYING
                && status != RoomStatus.PLANNING
                && status != RoomStatus.REVIEWING) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS);
        }
        PlaceCandidate candidate = getCandidateInRoom(roomId, placeId);
        CommentResponse response =
                CommentResponse.from(commentService.addOnPlace(candidate, me, request.body()));
        // TODO SSE (커밋 후): comment.created
        return response;
    }

    // 상세 응답을 만든다. 멤버, 후보 확인은 부르는 쪽에서 이미 끝낸 상태다 (상세 조회, 반응 누르기, 취소가 함께 씀)
    private PlaceDetailResponse toDetail(Long roomId, PlaceCandidate candidate) {
        // 멤버 id -> 그 멤버의 반응. 반응 안 한 멤버는 맵에 없다
        Map<Long, PlaceReactionType> reactionByMember =
                placeReactionRepository.findAllByPlaceCandidate(candidate).stream()
                        .collect(
                                Collectors.toMap(
                                        reaction -> reaction.getMember().getId(),
                                        PlaceReaction::getReaction));

        // 방에 남아 있는 멤버를 한 명씩 보면서 그 사람이 누른 반응을 찾아 붙인다.
        // 나간 사람은 멤버 목록에 없으니 반응 목록에도, 가고 싶어요 수에도 안 들어간다.
        List<PlaceDetailResponse.MemberReaction> reactions =
                roomMemberService.getActiveMemberSummaries(roomId).stream()
                        .map(member -> {
                            PlaceReactionType type = reactionByMember.get(member.getMemberId());
                            return new PlaceDetailResponse.MemberReaction(
                                    member.getMemberId(),
                                    member.getName(),
                                    type == null
                                            ? null
                                            : PlaceDetailResponse.ReactionLabel.of(type));
                        })
                        .toList();

        long agreeCount =
                reactions.stream()
                        .filter(reaction ->
                                reaction.reaction() != null
                                        && reaction.reaction().value() == PlaceReactionType.WANT)
                        .count();
        long activeMemberCount = reactions.size();

        return new PlaceDetailResponse(
                candidate.getId(),
                candidate.getGooglePlaceId(),
                candidate.getName(),
                PlaceDetailResponse.CategoryLabel.of(
                        PlaceCategory.fromGoogleType(candidate.getPlaceType())),
                candidate.getAddress(),
                candidate.getGoogleMapsUrl(),
                PlaceStage.of(candidate.isConfirmed(), agreeCount, activeMemberCount),
                agreeCount,
                activeMemberCount,
                toMemberRef(candidate.getSuggestedBy()),
                candidate.getCreatedAt(),
                candidate.getConfirmedAt(),
                reactions,
                commentService.countOnPlace(candidate.getId()));
    }

    // 반응은 일정 조율(PLANNING) 중에, 아직 확정 안 된 후보에만 남길 수 있다. 잠금 안에서 확인한다.
    // 순서는 팀 API와 같게 방 상태 -> 후보 조회다
    private PlaceCandidate getReactableCandidate(Room room, Long placeId) {
        if (room.getStatus() != RoomStatus.PLANNING) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS);
        }
        PlaceCandidate candidate = getCandidateInRoom(room.getId(), placeId);
        if (candidate.isConfirmed()) {
            throw new BusinessException(PlaceErrorCode.PLACE_ALREADY_CONFIRMED);
        }
        return candidate;
    }

    // 버튼을 누르기 전 칸. 누른 뒤 칸과 비교해서, 바뀌었으면 변경 이력을 남긴다. 나간 사람은 세지 않는다
    private PlaceStage currentStage(PlaceCandidate candidate, Long roomId) {
        long wantCount =
                placeReactionRepository.countByPlaceCandidateAndReactionAndMember_LeftAtIsNull(
                        candidate, PlaceReactionType.WANT);
        return PlaceStage.of(
                candidate.isConfirmed(), wantCount, roomMemberService.countActiveMembers(roomId));
    }

    // 바뀐 반응으로 상세를 다시 만들고, 칸이 바뀌었으면 변경 이력을 남긴다.
    // 같은 트랜잭션 안이라 방금 바꾼 반응이 상세에 그대로 보인다
    private PlaceDetailResponse detailAfterChange(
            Room room, RoomMember me, PlaceCandidate candidate, PlaceStage before) {
        PlaceDetailResponse detail = toDetail(room.getId(), candidate);
        if (detail.stage() != before) {
            // 예: "해동용궁사: 몇 명이 좋아해요 → 다 같이 가고 싶어요"
            changeLogService.record(
                    room,
                    me,
                    EntityType.PLACE_CANDIDATE,
                    candidate.getId(),
                    EventKind.PLACE_STAGE_CHANGED,
                    ActionStatus.UPDATE,
                    candidate.getName()
                            + ": "
                            + before.getLabel()
                            + " → "
                            + detail.stage().getLabel());
        }
        // TODO 커밋 후 SSE place.reaction_changed (보드 카드 갱신용. 명세에 data 모양이 정해지면 추가)
        return detail;
    }

    /**
     * 이 방의 장소 후보 하나. 일정 블록을 넣을 때도 쓴다.
     *
     * @throws BusinessException 없거나 다른 방의 후보면 PLACE_CANDIDATE_NOT_FOUND
     */
    public PlaceCandidate getCandidateInRoom(Long roomId, Long placeId) {
        return placeCandidateRepository
                .findByIdAndRoom_Id(placeId, roomId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_CANDIDATE_NOT_FOUND));
    }

    // 방의 반응 중 한 종류(WANT 또는 NOPE)를 후보별로 묶는다. 나간 멤버의 반응은 뺀다. 종류마다 SELECT 1번
    private Map<Long, List<MemberRef>> membersByPlace(Room room, PlaceReactionType type) {
        return placeReactionRepository
                .findAllByPlaceCandidate_RoomAndReactionAndMember_LeftAtIsNull(room, type)
                .stream()
                .collect(
                        Collectors.groupingBy(
                                reaction -> reaction.getPlaceCandidate().getId(),
                                Collectors.mapping(
                                        reaction -> toMemberRef(reaction.getMember()),
                                        Collectors.toList())));
    }

    // 칸반 카드 하나. 칸은 저장하지 않고 지금 인원으로 계산한다 (명세 8-1). 칸은 가고 싶어요로만 정한다
    private PlaceCandidateItem toItem(
            PlaceCandidate candidate,
            List<MemberRef> agreedBy,
            List<MemberRef> nopedBy,
            long activeMemberCount) {
        return new PlaceCandidateItem(
                candidate.getId(),
                candidate.getGooglePlaceId(),
                candidate.getName(),
                candidate.getAddress(),
                candidate.getPlaceType(),
                PlaceStage.of(candidate.isConfirmed(), agreedBy.size(), activeMemberCount),
                agreedBy.size(),
                agreedBy,
                nopedBy.size(),
                nopedBy,
                toMemberRef(candidate.getSuggestedBy()),
                candidate.getConfirmedAt());
    }

    // user는 지연 로딩이지만 조회 쿼리에서 @EntityGraph로 함께 가져와서 SELECT가 더 나가지 않는다
    private MemberRef toMemberRef(RoomMember member) {
        return new MemberRef(member.getId(), member.getUser().getName());
    }

    // 여행 일수 × 10. 7월 1일~3일이면 3일이라 30개
    private long limitOf(Room room) {
        long days = ChronoUnit.DAYS.between(room.getStartDate(), room.getEndDate()) + 1;
        return days * CANDIDATES_PER_DAY;
    }
}
