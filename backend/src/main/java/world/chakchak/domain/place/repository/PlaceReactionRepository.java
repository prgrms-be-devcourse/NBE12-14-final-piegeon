package world.chakchak.domain.place.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.place.entity.PlaceReaction;

public interface PlaceReactionRepository extends JpaRepository<PlaceReaction, Long> {

    // TODO 후보별 WANT 반응 수 - 칸반 칸 계산용 (P-03)
    // TODO 후보 + 멤버로 반응 조회 - 반응 바꾸기·취소용 (P-04, P-05)
    // TODO 후보의 반응 전부 삭제 - 후보 삭제 전에 먼저 지운다 (P-07)
}
