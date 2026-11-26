package world.chakchak.domain.survey.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import world.chakchak.domain.survey.entity.SurveyResponse;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    Optional<SurveyResponse> findByRoomMember_Id(Long roomMemberId);

    List<SurveyResponse> findAllByRoomMember_IdIn(Collection<Long> roomMemberIds);

    List<SurveyResponse> findAllByRoomMember_Room_Id(Long roomId);

    List<SurveyResponse> findAllByRoomMember_Room_IdAndRoomMember_LeftAtIsNull(Long roomId);

    /** 활동 멤버의 응답을 멤버 이름까지 한 번에 읽는다 (S-05 설문 리포트). 멤버 id 순이다. */
    @Query(
            """
            select response from SurveyResponse response
            join fetch response.roomMember member
            join fetch member.user
            where member.room.id = :roomId
              and member.leftAt is null
            order by member.id
            """)
    List<SurveyResponse> findAllActiveWithMemberByRoomId(@Param("roomId") Long roomId);

    long countByRoomMember_Room_IdAndRoomMember_LeftAtIsNull(Long roomId);

    void deleteByRoomMember_Id(Long roomMemberId);
}
