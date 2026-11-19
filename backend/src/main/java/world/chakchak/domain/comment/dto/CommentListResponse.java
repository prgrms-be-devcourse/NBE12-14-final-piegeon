package world.chakchak.domain.comment.dto;

import java.util.List;

/** 코멘트 목록. 오래된 순이다. (V-05, P-08) 어차피 단건 DTO는 없으니 CommentResponse와 따로 두기로 했다.  */
public record CommentListResponse(List<CommentResponse> comments) {}
