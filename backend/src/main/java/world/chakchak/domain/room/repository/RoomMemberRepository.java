package world.chakchak.domain.room.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import world.chakchak.domain.room.entity.RoomMember;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    Optional<RoomMember> findByRoom_IdAndUser_Id(Long roomId, Long userId);

    boolean existsByRoom_IdAndIdAndLeftAtIsNull(Long roomId, Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update RoomMember member
            set member.lastSeenAt = :seenUntil,
                member.modifiedAt = :modifiedAt
            where member.id = :memberId
                and member.leftAt is null
                and member.lastSeenAt < :seenUntil
            """)
    int advanceLastSeenAt(
            @Param("memberId") Long memberId,
            @Param("seenUntil") LocalDateTime seenUntil,
            @Param("modifiedAt") LocalDateTime modifiedAt);

    List<RoomMember> findAllByRoom_IdAndLeftAtIsNull(Long roomId);

    List<RoomMember> findAllByUser_IdAndLeftAtIsNull(Long userId);

    long countByRoom_IdAndLeftAtIsNull(Long roomId);

    @Query(
            """
            select
                member.id as memberId,
                user.name as name,
                member.joinedAt as joinedAt,
                case when surveyResponse.id is null then false else true end as surveySubmitted,
                surveyResponse.submittedAt as submittedAt
            from RoomMember member
            join member.user user
            left join SurveyResponse surveyResponse
                on surveyResponse.roomMember.id = member.id
            where member.room.id = :roomId
                and member.leftAt is null
            order by member.joinedAt asc, member.id asc
            """)
    List<ActiveRoomMemberSummary> findAllActiveMemberSummariesByRoomId(
            @Param("roomId") Long roomId);

    interface ActiveRoomMemberSummary {

        Long getMemberId();

        String getName();

        LocalDateTime getJoinedAt();

        boolean getSurveySubmitted();

        LocalDateTime getSubmittedAt();
    }
}
