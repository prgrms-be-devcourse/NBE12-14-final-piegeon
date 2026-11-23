package world.chakchak.domain.room.service;

import java.util.List;
import world.chakchak.domain.room.dto.SummaryResponse.UnvotedAgenda;

public interface UnvotedAgendaProvider {

    List<UnvotedAgenda> findUnvotedAgendas(Long roomId, Long memberId);
}
