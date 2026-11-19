package world.chakchak.domain.room.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import world.chakchak.domain.room.dto.RoomSummaryResponse;
import world.chakchak.domain.room.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByInviteCode(String inviteCode);

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
}
