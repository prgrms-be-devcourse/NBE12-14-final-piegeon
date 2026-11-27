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

    /** 주제별 가장 최근 회차 안건. 제목이 주제마다 하나로 고정인 설문 안건과 최종 확정에 쓴다. */
    Optional<AgendaItem> findTopByRoomIdAndSubjectOrderByRoundDesc(
            Long roomId, AgendaSubject subject);

    /** 같은 주제·제목의 가장 최근 회차 안건. 자유 안건은 subject가 모두 CUSTOM이라 제목까지 봐야 한 줄로 이어진다 (V-04). */
    Optional<AgendaItem> findTopByRoomIdAndSubjectAndTitleOrderByRoundDesc(
            Long roomId, AgendaSubject subject, String title);

    // 방 나가기(R-06)에서 VOTING 안건만 골라 removeVoter를 부르기 위해 추가
    List<AgendaItem> findAllByRoomIdAndStatus(Long roomId, AgendaStatus status);

    /**
     * 이 멤버가 지금 투표할 수 있는데 아직 표를 내지 않은 안건 (H-02 비접속 요약의 해야 할 일). 마감 임박 순이고, 같으면 id 순이다.
     *
     * <p>조건은 투표(V-03)가 받는 조건과 같다. status = VOTING이고 마감 전이어야 하고({@link AgendaItem#isOpenAt}), 안건이
     * 열리기 전에 합류한 멤버여야 한다({@link AgendaItem#isEligible}).
     */
    @Query(
            """
            select a from AgendaItem a
            where a.room.id = :roomId
              and a.status = :status
              and a.deadlineAt > :now
              and a.createdAt >= (select m.joinedAt from RoomMember m where m.id = :memberId)
              and not exists (
                  select v.id from Vote v
                  where v.agendaItem.id = a.id and v.member.id = :memberId)
            order by a.deadlineAt asc, a.id asc
            """)
    List<AgendaItem> findUnvotedOpenAgendas(
            @Param("roomId") Long roomId,
            @Param("memberId") Long memberId,
            @Param("status") AgendaStatus status,
            @Param("now") LocalDateTime now);

    /**
     * 마감 시각이 지난 안건이 있는 방 (13-2 마감 스케줄러).
     *
     * <p>출발일이 오늘보다 뒤인 방만 찾는다. 출발일 00:00(D-DAY)이 된 방은 최종 집계가 처리한다. 자유 안건은 최종 집계가 없어 D-DAY가 지나도
     * 찾는다({@link AgendaItem#isExpirableAt}).
     */
    @Query(
            """
            select distinct a.room.id from AgendaItem a
            where a.status = :status
              and a.deadlineAt <= :now
              and (a.room.startDate > :today
                   or a.subject = world.chakchak.domain.agenda.entity.AgendaSubject.CUSTOM)
            """)
    List<Long> findRoomIdsWithOverdueAgendas(
            @Param("status") AgendaStatus status,
            @Param("now") LocalDateTime now,
            @Param("today") LocalDate today);
}
