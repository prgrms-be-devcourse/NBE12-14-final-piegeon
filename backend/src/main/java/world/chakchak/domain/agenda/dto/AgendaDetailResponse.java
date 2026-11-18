package world.chakchak.domain.agenda.dto;

import java.time.LocalDateTime;
import java.util.List;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;

/** 안건 상세와 선택지별 투표자 (V-02). V-03 투표, V-04 다음 회차의 응답도 같은 모양이다. */
public record AgendaDetailResponse(
        Long agendaId,
        AgendaSubject subject,
        long round,
        String title,
        AgendaStatus status,
        LocalDateTime deadlineAt,
        long totalVoter,
        long votedCount,
        boolean eligible,
        Long myOptionId,
        List<OptionResult> options) {

    /** 선택지 하나와 그 선택지에 투표한 멤버 */
    public record OptionResult(
            Long optionId,
            String label,
            String value,
            long voteCount,
            List<MemberRef> voters,
            // 멤버가 직접 추가한 예산 선지면 그 멤버, 설문에서 나온 선지면 null
            MemberRef proposedBy) {}

    public static AgendaDetailResponse of(
            AgendaItem agenda,
            long votedCount,
            boolean eligible,
            Long myOptionId,
            List<OptionResult> options) {
        return new AgendaDetailResponse(
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
                options);
    }
}
