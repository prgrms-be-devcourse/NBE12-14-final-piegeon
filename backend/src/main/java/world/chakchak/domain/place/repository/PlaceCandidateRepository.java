package world.chakchak.domain.place.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
