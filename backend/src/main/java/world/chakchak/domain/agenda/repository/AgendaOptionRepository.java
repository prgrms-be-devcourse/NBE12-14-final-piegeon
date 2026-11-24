package world.chakchak.domain.agenda.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import world.chakchak.domain.agenda.entity.AgendaOption;

public interface AgendaOptionRepository extends JpaRepository<AgendaOption, Long> {

    /** 안건의 선지를 화면 순서대로 (V-02). 선지를 추가한 멤버의 이름도 함께 읽는다. */
    @EntityGraph(attributePaths = {"proposedBy", "proposedBy.user"})
    List<AgendaOption> findAllByAgendaItemIdOrderByDisplayIndexAsc(Long agendaId);

    /** 이 안건의 선지만 찾는다. 다른 안건의 선지면 비어 있다(→ OPTION_NOT_IN_AGENDA / OPTION_NOT_FOUND). */
    Optional<AgendaOption> findByIdAndAgendaItemId(Long id, Long agendaId);

    /** 같은 값의 선지가 이미 있는지. 있으면 409 OPTION_ALREADY_EXISTS의 data.optionId로 쓴다. (V-07) */
    Optional<AgendaOption> findByAgendaItemIdAndValue(Long agendaId, String value);

    /** 이 회차에 내가 추가한 선지가 있는지. 회차마다 1인 1개다. (V-07) */
    boolean existsByAgendaItemIdAndProposedById(Long agendaId, Long memberId);

    /**
     * 활동 중인 멤버가 직접 추가한 선지를 화면 순서대로. 다음 회차에 그대로 옮긴다. (V-04)
     *
     * <p>설문에서 나온 선지(proposed_by NULL)는 join에서 빠진다. 나간 멤버의 선지도 뺀다. 떠난 사람의 선호가 선택지에 섞이지 않게 하려는 것으로,
     * 나갈 때 설문 응답을 지우는 R-06과 같은 이유다.
     */
    @Query(
            """
            select o from AgendaOption o
            join o.proposedBy m
            where o.agendaItem.id = :agendaId and m.leftAt is null
            order by o.displayIndex
            """)
    List<AgendaOption> findAllProposedByActiveMembers(@Param("agendaId") Long agendaId);
}
