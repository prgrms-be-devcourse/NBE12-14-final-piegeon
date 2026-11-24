package world.chakchak.domain.comment.dto;

/**
 * SSE comment.created의 data. 안건 코멘트면 agendaId, 장소 코멘트면 placeId에 값이 있고 다른 하나는 null이다.
 *
 * <p>변경 기록에는 남기지 않는다. 받는 화면은 코멘트 목록을 다시 불러온다.
 */
public record CommentCreatedEvent(Long agendaId, Long placeId, CommentResponse comment) {}
