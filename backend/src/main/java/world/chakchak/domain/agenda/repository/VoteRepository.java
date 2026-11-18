package world.chakchak.domain.agenda.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.agenda.entity.Vote;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    /** 이 안건에 낸 내 표 (V-03 투표·표 바꾸기, V-01 myOptionId) */
    Optional<Vote> findByAgendaItemIdAndMemberId(Long agendaId, Long memberId);

    /** 안건의 모든 표 (7-1 만장일치 판정, V-02 선지별 투표자) */
    List<Vote> findAllByAgendaItemId(Long agendaId);

    /** 안건에 들어온 표 수 (votedCount) */
    long countByAgendaItemId(Long agendaId);

    /** 이 선지에 다른 멤버가 낸 표 수. 1 이상이면 선지를 지울 수 없다. (V-08 OPTION_HAS_VOTES의 data.voteCount) */
    long countByAgendaOptionIdAndMemberIdNot(Long optionId, Long memberId);

    /** 선지를 지우기 전에 그 선지에 있는 내 표를 지운다. (V-08) */
    void deleteByAgendaOptionIdAndMemberId(Long optionId, Long memberId);

    /** 방을 나갈 때 투표 중인 안건에서 내 표를 지운다. (R-06) */
    void deleteByAgendaItemIdAndMemberId(Long agendaId, Long memberId);
}
