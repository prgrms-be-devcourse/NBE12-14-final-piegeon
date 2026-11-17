package world.chakchak.domain.agenda.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;

public interface AgendaItemRepository extends JpaRepository<AgendaItem, Long> {

    /** 이 방의 안건만 찾는다. 다른 방의 안건이면 비어 있다(→ 404 AGENDA_NOT_FOUND). */
    Optional<AgendaItem> findByIdAndRoomId(Long id, Long roomId);

    /** 주제별 가장 최근 회차 안건 */
    Optional<AgendaItem> findTopByRoomIdAndSubjectOrderByRoundDesc(
            Long roomId, AgendaSubject subject);

    //방 나가기(R-06)에서 VOTING 안건만 골라 removeVoter를 부르기 위해 추가
    List<AgendaItem> findAllByRoomIdAndStatus(Long roomId, AgendaStatus status);
}
