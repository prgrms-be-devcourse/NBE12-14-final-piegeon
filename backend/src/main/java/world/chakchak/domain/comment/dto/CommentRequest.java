package world.chakchak.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 코멘트 작성 요청. 안건 코멘트(V-06)와 장소 코멘트(P-09)가 같이 쓴다. */
public record CommentRequest(@NotBlank @Size(max = 500) String body) {}
