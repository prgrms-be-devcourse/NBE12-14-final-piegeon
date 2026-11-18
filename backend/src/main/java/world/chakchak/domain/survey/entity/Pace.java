package world.chakchak.domain.survey.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Pace {
    RELAXED("여유롭게(하루 1~3개)"),
    NORMAL("적당히(하루 4~7개)"),
    HURRY("바쁘게(하루 8~10개)");

    private final String label;
}
