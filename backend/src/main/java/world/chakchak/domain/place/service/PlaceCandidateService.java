package world.chakchak.domain.place.service;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import world.chakchak.domain.comment.dto.CommentListResponse;
import world.chakchak.domain.comment.dto.CommentRequest;
import world.chakchak.domain.comment.dto.CommentResponse;
import world.chakchak.domain.comment.service.CommentService;
import world.chakchak.domain.place.dto.GooglePlace;
import world.chakchak.domain.place.dto.MemberRef;
import world.chakchak.domain.place.dto.PlaceAlreadyRegisteredData;
import world.chakchak.domain.place.dto.PlaceCandidateItem;
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

/** 장소 후보 기능. (P-02, P-08·P-09 장소 코멘트) */
@Service
@RequiredArgsConstructor
public class PlaceCandidateService {

    // 여행 하루에 올릴 수 있는 후보 수 (한도 = 여행 일수 x 10)
    private static final int CANDIDATES_PER_DAY = 10;

    private final RoomService roomService;
    private final PlatformTransactionManager transactionManager;
    private final PlaceSearchClient placeSearchClient;
    private final RoomMemberService roomMemberService;
    private final PlaceCandidateRepository placeCandidateRepository;
    private final PlaceReactionRepository placeReactionRepository;
    private final CommentService commentService;

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
                me,
                candidate.getConfirmedAt());
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

    private PlaceCandidate getCandidateInRoom(Long roomId, Long placeId) {
        return placeCandidateRepository
                .findByIdAndRoom_Id(placeId, roomId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_CANDIDATE_NOT_FOUND));
    }

    // 여행 일수 × 10. 7월 1일~3일이면 3일이라 30개
    private long limitOf(Room room) {
        long days = ChronoUnit.DAYS.between(room.getStartDate(), room.getEndDate()) + 1;
        return days * CANDIDATES_PER_DAY;
    }
}
