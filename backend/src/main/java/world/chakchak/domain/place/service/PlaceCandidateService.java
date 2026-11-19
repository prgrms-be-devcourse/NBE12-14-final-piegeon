package world.chakchak.domain.place.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import world.chakchak.global.error.BusinessException;

/**
 * 장소 후보 기능. (P-02)
 *
 * <p>TODO 입구 메서드 register(roomId, userId, googlePlaceId) (P-02): RoomMemberService.getActiveMember가
 * 생기면 작성한다. 1) 트랜잭션 밖에서 placeSearchClient.findById, 없으면 PLACE_NOT_FOUND 2) getActiveMember로 멤버, 활동
 * 멤버 수 구하기 3) create 호출
 */
@Service
@RequiredArgsConstructor
public class PlaceCandidateService {

    // 여행 하루에 올릴 수 있는 후보 수 (한도 = 여행 일수 x 10)
    private static final int CANDIDATES_PER_DAY = 10;

    private final EntityManager entityManager;
    private final PlaceCandidateRepository placeCandidateRepository;
    private final PlaceReactionRepository placeReactionRepository;

    /**
     * 장소 후보를 저장하고, 올린 사람의 "가고 싶어요"를 함께 남긴다. (P-02 트랜잭션 부분)
     *
     * @param member 요청한 활동 멤버
     * @param activeMemberCount 방의 활동 멤버 수 (칸 계산용)
     * @param place Google에서 다시 조회한 장소
     */
    @Transactional
    public PlaceCandidateItem create(RoomMember member, long activeMemberCount, GooglePlace place) {
        // 같은 방의 등록, 삭제가 동시에 일어나도 개수, 중복 확인이 어긋나지 않게 방 행을 잠근다
        // TODO 방 잠금: 시원님이 방 잠금 메서드 완성하시면 그걸로 바꾸기
        Room room =
                entityManager.find(
                        Room.class, member.getRoom().getId(), LockModeType.PESSIMISTIC_WRITE);

        // TODO 방이 PLANNING이 아니면 에러 코드 반환

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

    // 여행 일수 × 10. 7월 1일~3일이면 3일이라 30개
    private long limitOf(Room room) {
        long days = ChronoUnit.DAYS.between(room.getStartDate(), room.getEndDate()) + 1;
        return days * CANDIDATES_PER_DAY;
    }
}
