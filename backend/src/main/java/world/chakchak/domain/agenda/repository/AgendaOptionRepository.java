package world.chakchak.domain.agenda.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.agenda.entity.AgendaOption;

public interface AgendaOptionRepository extends JpaRepository<AgendaOption, Long> {

    /** 안건의 선지를 화면 순서대로 (V-02) */
    List<AgendaOption> findAllByAgendaItemIdOrderByDisplayIndexAsc(Long agendaId);

    /** 이 안건의 선지만 찾는다. 다른 안건의 선지면 비어 있다(→ OPTION_NOT_IN_AGENDA / OPTION_NOT_FOUND). */
    Optional<AgendaOption> findByIdAndAgendaItemId(Long id, Long agendaId);

    /** 같은 값의 선지가 이미 있는지. 있으면 409 OPTION_ALREADY_EXISTS의 data.optionId로 쓴다. (V-07) */
    Optional<AgendaOption> findByAgendaItemIdAndValue(Long agendaId, String value);

    /** 이 회차에 내가 추가한 선지가 있는지. 회차마다 1인 1개다. (V-07) */
    boolean existsByAgendaItemIdAndProposedById(Long agendaId, Long memberId);

    /** 멤버가 직접 추가한 선지. 다음 회차에 그대로 옮긴다. (V-04) */
    List<AgendaOption> findAllByAgendaItemIdAndProposedByIsNotNull(Long agendaId);
}
