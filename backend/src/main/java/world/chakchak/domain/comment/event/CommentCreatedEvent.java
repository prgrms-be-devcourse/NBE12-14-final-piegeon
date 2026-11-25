package world.chakchak.domain.comment.event;

import world.chakchak.domain.comment.dto.CommentResponse;

/**
 * SSE {@code comment.created}의 data (명세 11-1). 안건 코멘트면 agendaId, 장소 코멘트면 placeId만 값이 있다.
 *
 * <p>코멘트는 방 활동 기록에 남기지 않으므로 id 없이 보내고, 재연결 때 다시 보내지 않는다.
 */
public record CommentCreatedEvent(Long agendaId, Long placeId, CommentResponse comment) {

    public static CommentCreatedEvent onAgenda(Long agendaId, CommentResponse comment) {
        return new CommentCreatedEvent(agendaId, null, comment);
    }

    public static CommentCreatedEvent onPlace(Long placeId, CommentResponse comment) {
        return new CommentCreatedEvent(null, placeId, comment);
    }
}
