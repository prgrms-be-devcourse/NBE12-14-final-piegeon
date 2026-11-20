package world.chakchak.domain.agenda.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 예산 선지 추가 요청 (V-07)
 *
 * @param amount 금액(원). 범위는 설문 예산과 같다 (S-01 limits). 저장할 때 만원 단위로 반올림한다.
 */
public record AddOptionRequest(@NotNull @Min(1) @Max(99_999_999) Long amount) {}
