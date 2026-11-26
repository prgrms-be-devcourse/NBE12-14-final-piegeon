package world.chakchak.domain.agenda.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.room.dto.SummaryResponse.UnvotedAgenda;
import world.chakchak.domain.room.service.UnvotedAgendaProvider;

/**
 * 비접속 요약(H-02)의 "투표 안 한 안건"을 찾는다. 지금 투표할 수 있는데 아직 표를 내지 않은 안건만 마감 임박 순으로 돌려준다.
 *
 * <p>멤버 확인은 부르는 쪽(비접속 요약)이 이미 했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnvotedAgendaFinder implements UnvotedAgendaProvider {

    private final AgendaItemRepository agendaItemRepository;
    private final Clock clock;

    @Override
    public List<UnvotedAgenda> findUnvotedAgendas(Long roomId, Long memberId) {
        return agendaItemRepository
                .findUnvotedOpenAgendas(
                        roomId, memberId, AgendaStatus.VOTING, LocalDateTime.now(clock))
                .stream()
                .map(agenda ->
                        new UnvotedAgenda(
                                agenda.getId(), agenda.getTitle(), agenda.getDeadlineAt()))
                .toList();
    }
}
