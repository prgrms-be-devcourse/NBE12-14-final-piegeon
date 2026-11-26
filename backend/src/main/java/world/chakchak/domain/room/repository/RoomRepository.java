package world.chakchak.domain.room.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import world.chakchak.domain.room.dto.RoomSummaryResponse;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByInviteCode(String inviteCode);

    Optional<Room> findByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from Room room where room.inviteCode = :inviteCode")
    Optional<Room> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);

    /**
     * 방 행을 잠그고 읽는다 (SELECT ... FOR UPDATE). 같은 방의 쓰기 API를 한 줄로 세운다. (명세 1-4)
     *
     * <p>잠금은 이 방 행 하나에만 걸리고 트랜잭션이 끝날 때 풀린다. 다른 방의 요청과 잠금 없는 조회는 기다리지 않는다. 같은 방의 쓰기가 직렬화되는 것은 모든 쓰기
     * API가 이 메서드를 부르기로 약속했기 때문이므로, 쓰기 트랜잭션의 첫 쿼리로 부른다. 이미 영속성 컨텍스트에 읽어 둔 방은 잠금만 걸리고 값은 새로 읽지 않아, 앞서
     * 읽은 낡은 상태로 판단하게 된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    @Query(
            """
            select room.id from Room room
            where (room.opinionDeadlineAt is not null
                   and room.opinionDeadlineAt <= :opinionCutoff
                   and not exists (
                       select response.id from SurveyResponse response
                       where response.roomMember.room.id = room.id
                   ))
               or room.endDate < :endDateCutoff
            """)
    List<Long> findRoomIdsExpiredForCleanup(
            @Param("opinionCutoff") LocalDateTime opinionCutoff,
            @Param("endDateCutoff") LocalDate endDateCutoff);

    /**
     * 의견 마감이 지났는데 설문이 아직 마감되지 않은 방 (13-1).
     *
     * <p>출발일이 내일보다 뒤인 방만 찾는다. 출발 전날 00:00(D-1)이 된 방은 최종 집계가 처리한다.
     */
    @Query(
            """
            select r.id from Room r
            where r.status = :status
              and r.surveyClosedAt is null
              and r.opinionDeadlineAt <= :now
              and r.startDate > :tomorrow
            """)
    List<Long> findRoomIdsPastOpinionDeadline(
            @Param("status") RoomStatus status,
            @Param("now") LocalDateTime now,
            @Param("tomorrow") LocalDate tomorrow);

    /** 출발 전날 00:00(D-1)이 됐는데 아직 조율이나 최종 투표가 끝나지 않은 방 (D-1 최종 집계). */
    @Query("select r.id from Room r where r.status in :statuses and r.startDate <= :tomorrow")
    List<Long> findRoomIdsAtFinalTally(
            @Param("statuses") Collection<RoomStatus> statuses,
            @Param("tomorrow") LocalDate tomorrow);

    @Query(
            """
            select new world.chakchak.domain.room.dto.RoomSummaryResponse(
                room.id,
                room.name,
                room.destination,
                room.startDate,
                room.endDate,
                room.status,
                count(activeMember.id),
                count(surveyResponse.id),
                room.opinionDeadlineAt
            )
            from RoomMember currentMember
            join currentMember.room room
            left join RoomMember activeMember
                on activeMember.room.id = room.id and activeMember.leftAt is null
            left join SurveyResponse surveyResponse
                on surveyResponse.roomMember.id = currentMember.id
            where currentMember.user.id = :userId
                and currentMember.leftAt is null
            group by
                room.id,
                room.name,
                room.destination,
                room.startDate,
                room.endDate,
                room.status,
                room.opinionDeadlineAt
            order by room.startDate asc, room.id asc
            """)
    List<RoomSummaryResponse> findMyRoomSummaries(@Param("userId") Long userId);

    @Query(
            """
            select room as room, activeMember as activeMember
            from Room room
            left join RoomMember activeMember
                on activeMember.room.id = room.id
                and activeMember.user.id = :userId
                and activeMember.leftAt is null
            where room.id = :roomId
            """)
    Optional<RoomMemberAccessProjection> findRoomAndMemberForAccess(
            @Param("roomId") Long roomId, @Param("userId") Long userId);

    interface RoomMemberAccessProjection {

        Room getRoom();

        RoomMember getActiveMember();
    }
}
