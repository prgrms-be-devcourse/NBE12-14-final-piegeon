package world.chakchak.domain.agenda.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;

public interface AgendaItemRepository extends JpaRepository<AgendaItem, Long> {

    /** 이 방의 안건만 찾는다. 다른 방의 안건이면 비어 있다(→ 404 AGENDA_NOT_FOUND). */
    Optional<AgendaItem> findByIdAndRoomId(Long id, Long roomId);

    /**
     * 이 상태의 안건을 마감 임박 순으로 (V-01 앞부분, status = VOTING)
     *
     * <p>마감 시각이 같으면 id 순이다. 같은 시각에 열린 설문 안건들도 매번 같은 순서로 나온다.
     */
    List<AgendaItem> findAllByRoomIdAndStatusOrderByDeadlineAtAscIdAsc(
            Long roomId, AgendaStatus status);

    /** 이 상태가 아닌 안건을 최근 순으로 (V-01 뒷부분, status = VOTING을 넘겨 끝난 안건만). 생성 시각이 같으면 id 역순이다. */
    List<AgendaItem> findAllByRoomIdAndStatusNotOrderByCreatedAtDescIdDesc(
            Long roomId, AgendaStatus status);

    /** 주제별 가장 최근 회차 안건 */
    Optional<AgendaItem> findTopByRoomIdAndSubjectOrderByRoundDesc(
            Long roomId, AgendaSubject subject);

    // 방 나가기(R-06)에서 VOTING 안건만 골라 removeVoter를 부르기 위해 추가
    List<AgendaItem> findAllByRoomIdAndStatus(Long roomId, AgendaStatus status);

    /**
     * 마감 시각이 지난 안건이 있는 방 (13-2 마감 스케줄러).
     *
     * <p>출발일이 내일보다 뒤인 방만 찾는다. 출발 전날 00:00(D-1)이 된 방은 최종 집계가 처리한다.
     */
    @Query(
            """
            select distinct a.room.id from AgendaItem a
            where a.status = :status
              and a.deadlineAt <= :now
              and a.room.startDate > :tomorrow
            """)
    List<Long> findRoomIdsWithOverdueAgendas(
            @Param("status") AgendaStatus status,
            @Param("now") LocalDateTime now,
            @Param("tomorrow") LocalDate tomorrow);
}
