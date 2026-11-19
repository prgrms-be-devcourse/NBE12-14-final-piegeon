package world.chakchak.domain.agenda.dto;

import java.time.LocalDateTime;
import java.util.List;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;

/** 안건 목록 (V-01). 목록 옆에 붙일 정보가 생기면 여기에 필드를 추가한다. */
public record AgendaListResponse(List<Item> agendas) {

    /** 안건 목록의 한 줄 */
    public record Item(
            Long agendaId,
            AgendaSubject subject,
            long round,
            String title,
            AgendaStatus status,
            LocalDateTime deadlineAt,
            long totalVoter,
            long votedCount,
            // 투표 자격이 있는지. 프론트가 투표 버튼을 켜고 끄는 데 쓴다.
            boolean eligible,
            // 내가 고른 선지. 아직 안 냈으면 null
            Long myOptionId,
            // 결정된 선지. RESOLVED가 아니면 null
            OptionRef resolvedOption) {

        public static Item of(
                AgendaItem agenda,
                long votedCount,
                boolean eligible,
                Long myOptionId,
                OptionRef resolvedOption) {
            return new Item(
                    agenda.getId(),
                    agenda.getSubject(),
                    agenda.getRound(),
                    agenda.getTitle(),
                    agenda.getStatus(),
                    agenda.getDeadlineAt(),
                    agenda.getTotalVoter(),
                    votedCount,
                    eligible,
                    myOptionId,
                    resolvedOption);
        }
    }
}
