package world.chakchak.domain.comment.dto;

import java.time.LocalDateTime;
import world.chakchak.domain.comment.entity.Comment;

/** 코멘트 한 개. 이름은 작성자의 user.name이다. 안건 코멘트(V-05, V-06)와 장소 코멘트(P-08, P-09)가 같이 쓴다. */
public record CommentResponse(
        Long commentId, Long memberId, String name, String body, LocalDateTime createdAt) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getMember().getId(),
                comment.getMember().getUser().getName(),
                comment.getBody(),
                comment.getCreatedAt());
    }
}
