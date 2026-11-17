package world.chakchak.domain.survey.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActivityCode {
    FOOD("맛집"),
    NATURE("자연·힐링"),
    ACTIVITY("액티비티·체험"),
    SHOPPING("쇼핑·투어"),
    CULTURE("명소·문화탐방");

    private final String label;
}
