package world.chakchak.domain.agenda.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 최종 확정(FINAL_PLAN) 안건의 두 선지. 이름이 곧 agenda_option.value다. (C-01) */
@Getter
@RequiredArgsConstructor
public enum FinalPlanChoice {
    AGREE("동의"),
    REJECT("반대");

    private final String label;
}
