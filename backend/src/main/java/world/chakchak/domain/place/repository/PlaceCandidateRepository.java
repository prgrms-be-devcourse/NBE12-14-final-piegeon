package world.chakchak.domain.place.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.place.entity.PlaceCandidate;

public interface PlaceCandidateRepository extends JpaRepository<PlaceCandidate, Long> {

    // TODO 방의 후보 목록 조회 (P-03)
    // TODO 방의 후보 개수 - 등록 제한(여행 일수 × 10) 확인용 (P-02)
    // TODO 방 + googlePlaceId로 이미 등록됐는지 확인 (P-02, 중복이면 에러)
}
