package world.chakchak.domain.agenda.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 투표·표 바꾸기 요청 (V-03)
 *
 * @param optionId 이 안건의 선택지
 * @param comment 최종 확정에 반대할 때만 필수(1~500자). 비었는지는 service가 확인한다(REJECT_COMMENT_REQUIRED). 반대가 아니면
 *     무시한다.
 */
public record VoteRequest(@NotNull Long optionId, @Size(max = 500) String comment) {}
