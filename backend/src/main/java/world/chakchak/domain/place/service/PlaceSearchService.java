package world.chakchak.domain.place.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import world.chakchak.domain.place.dto.GooglePlace;
import world.chakchak.domain.place.dto.PlaceSearchResponse;
import world.chakchak.domain.place.dto.PlaceSearchResponse.PlaceSearchItem;
import world.chakchak.domain.place.dto.PlaceSearchResult;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

/**
 * 장소 검색. (P-01)
 *
 * <p>@Transactional을 붙이지 않는다. Google을 기다리는 동안 DB 연결을 잡고 있지 않으려고, DB 조회는 각각 짧게 끝낸다. 읽기만 하므로 방 잠금도 잡지
 * 않는다. 그 사이 누가 같은 장소를 올려서 alreadyRegistered가 어긋나도 등록(P-02)이 409로 막는다.
 */
@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private final RoomMemberService roomMemberService;
    private final PlaceSearchClient placeSearchClient;
    private final PlaceCandidateRepository placeCandidateRepository;

    /**
     * 검색어 앞에 여행지를 붙여 Google에서 찾고, 이 방에 이미 올라온 장소를 표시한다.
     *
     * @param query 검색어 (2~50자 검증은 controller에서 한다)
     * @param pageToken 다음 페이지 토큰. 첫 페이지면 null
     * @throws BusinessException 방이 없으면 ROOM_NOT_FOUND, 활동 멤버가 아니면 FORBIDDEN, 방이 PLANNING이 아니면
     *     INVALID_STATUS, Google 오류면 PLACES_API_ERROR
     */
    public PlaceSearchResponse search(Long roomId, Long userId, String query, String pageToken) {
        // 멤버 확인 쿼리가 방도 함께 가져와서, 트랜잭션 밖에서도 방 상태를 읽을 수 있다
        Room room = roomMemberService.getActiveMember(roomId, userId).getRoom();
        if (room.getStatus() != RoomStatus.PLANNING) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS);
        }

        // "시장"만 보내면 전국 결과가 섞이므로 여행지를 앞에 붙인다 ("서울 시장")
        PlaceSearchResult result =
                placeSearchClient.search(room.getDestination() + " " + query, pageToken);

        List<String> googlePlaceIds =
                result.places().stream().map(GooglePlace::googlePlaceId).toList();
        Set<String> registered =
                placeCandidateRepository
                        .findAllByRoom_IdAndGooglePlaceIdIn(roomId, googlePlaceIds)
                        .stream()
                        .map(PlaceCandidate::getGooglePlaceId)
                        .collect(Collectors.toSet());

        List<PlaceSearchItem> places =
                result.places().stream()
                        .map(place ->
                                new PlaceSearchItem(
                                        place.googlePlaceId(),
                                        place.name(),
                                        place.address(),
                                        place.placeType(),
                                        registered.contains(place.googlePlaceId())))
                        .toList();
        return new PlaceSearchResponse(places, result.nextPageToken());
    }
}
