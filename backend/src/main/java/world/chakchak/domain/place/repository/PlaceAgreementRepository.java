package world.chakchak.domain.place.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.place.entity.PlaceAgreement;

public interface PlaceAgreementRepository extends JpaRepository<PlaceAgreement, Long> {

    // TODO 후보별 동의 수 - 칸반 칸 계산용 (P-03)
    // TODO 후보 + 멤버로 동의 조회 - 동의 취소용
    // TODO 후보의 동의 전부 삭제 - 후보 삭제 전에 먼저 지운다
}
