package world.chakchak.domain.agenda.event;

import java.util.List;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse;

/**
 * SSE {@code vote.updated}의 data (명세 11-1). 투표(V-03), 예산 선지 추가(V-07)·삭제(V-08) 뒤의 표 수다.
 *
 * <p>선지 목록을 통째로 담아, 선지가 늘거나 줄어든 것도 이 이벤트로 알 수 있다. 누가 어디에 투표했는지는 담지 않는다. 필요하면 V-02로 다시 읽는다.
 */
public record VoteUpdatedEvent(Long agendaId, long votedCount, List<OptionVoteCount> options) {

    public record OptionVoteCount(Long optionId, long voteCount) {}

    /** 방금 만든 상세 응답에서 꺼낸다. 표를 다시 읽지 않는다. */
    public static VoteUpdatedEvent from(AgendaDetailResponse detail) {
        return new VoteUpdatedEvent(
                detail.agendaId(),
                detail.votedCount(),
                detail.options().stream()
                        .map(option -> new OptionVoteCount(option.optionId(), option.voteCount()))
                        .toList());
    }
}
