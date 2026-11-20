package world.chakchak.domain.survey.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.survey.entity.SurveyResponse;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    Optional<SurveyResponse> findByRoomMember_Id(Long roomMemberId);

    List<SurveyResponse> findAllByRoomMember_IdIn(Collection<Long> roomMemberIds);

    List<SurveyResponse> findAllByRoomMember_Room_Id(Long roomId);

    List<SurveyResponse> findAllByRoomMember_Room_IdAndRoomMember_LeftAtIsNull(Long roomId);

    long countByRoomMember_Room_IdAndRoomMember_LeftAtIsNull(Long roomId);

    void deleteByRoomMember_Id(Long roomMemberId);
}
