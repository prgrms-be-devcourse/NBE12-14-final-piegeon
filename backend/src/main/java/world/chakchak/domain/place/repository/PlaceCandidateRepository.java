package world.chakchak.domain.place.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.room.entity.Room;

public interface PlaceCandidateRepository extends JpaRepository<PlaceCandidate, Long> {

    // TODO 방의 후보 목록 조회 (P-03)

    /** 방의 후보 개수. 등록 한도(여행 일수 x 10) 확인용 (P-02) */
    long countByRoom(Room room);

    /** 같은 방에 이미 올라온 장소. 있으면 PLACE_ALREADY_REGISTERED의 data.placeId로 쓴다 (P-02) */
    Optional<PlaceCandidate> findByRoomAndGooglePlaceId(Room room, String googlePlaceId);
}
