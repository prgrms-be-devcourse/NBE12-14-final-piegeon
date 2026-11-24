package world.chakchak.domain.room.service;

import java.util.List;
import org.springframework.stereotype.Component;
import world.chakchak.domain.room.dto.SummaryResponse.UnvotedAgenda;

@Component
public class DefaultUnvotedAgendaProvider implements UnvotedAgendaProvider {

    @Override
    public List<UnvotedAgenda> findUnvotedAgendas(Long roomId, Long memberId) {
        // TODO: B 담당 투표 도메인 구현체로 교체한다.
        return List.of();
    }
}
