package world.chakchak.domain.place.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.room.entity.Room;

public interface PlaceCandidateRepository extends JpaRepository<PlaceCandidate, Long> {

    /**
     * 방의 후보 전부. (P-03)
     *
     * <p>카드에 올린 사람 이름을 쓰므로 등록한 멤버와 그 사용자까지 JOIN으로 한 번에 가져온다. 연관관계가 LAZY라서 이게 없으면 후보마다 멤버·사용자
     * SELECT가 따로 나간다(N+1).
     */
    @EntityGraph(attributePaths = {"suggestedBy", "suggestedBy.user"})
    List<PlaceCandidate> findAllByRoom(Room room);

    /** 방의 후보 개수. 등록 한도(여행 일수 x 10) 확인용 (P-02) */
    long countByRoom(Room room);

    /** 같은 방에 이미 올라온 장소. 있으면 PLACE_ALREADY_REGISTERED의 data.placeId로 쓴다 (P-02) */
    Optional<PlaceCandidate> findByRoomAndGooglePlaceId(Room room, String googlePlaceId);

    /** 검색 결과 중 이 방에 이미 올라온 후보들. 결과 20개를 한 번의 IN 쿼리로 확인한다 (P-01) */
    List<PlaceCandidate> findAllByRoom_IdAndGooglePlaceIdIn(
            Long roomId, Collection<String> googlePlaceIds);

    /**
     * 주어진 id 중 아직 남아 있는 후보의 id. 비접속 요약에서 그 사이 지워진 장소를 빼는 데 쓴다. (H-02)
     *
     * <p>id만 필요해서 엔티티를 읽지 않고 IN 쿼리 한 번으로 확인한다.
     */
    @Query("select candidate.id from PlaceCandidate candidate where candidate.id in :ids")
    List<Long> findExistingIds(@Param("ids") Collection<Long> ids);

    /** 이 방의 후보. 없거나 다른 방의 후보면 빈 값이라 PLACE_CANDIDATE_NOT_FOUND로 막는다 (P-08, P-09) */
    Optional<PlaceCandidate> findByIdAndRoom_Id(Long id, Long roomId);

    /**
     * 후보 행을 잠근다 (SELECT ... FOR UPDATE). 지우는 동안 이 후보에 코멘트가 저장되지 못하게 한다. (P-07)
     *
     * <p>코멘트를 저장할 때 DB가 외래 키를 확인하려고 후보 행에 공유 잠금을 건다. 그래서 이 잠금과 코멘트 저장은 한 줄로 선다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select candidate from PlaceCandidate candidate where candidate.id = :id")
    Optional<PlaceCandidate> findByIdForUpdate(@Param("id") Long id);
}
