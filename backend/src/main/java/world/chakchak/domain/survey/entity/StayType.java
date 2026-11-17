package world.chakchak.domain.survey.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StayType {
    HOTEL("전문 숙박시설(호텔·리조트)"),
    PERSONAL("독채형(에어비앤비·레지던스)"),
    SOCIAL("교류형(호스텔·게스트하우스)"),
    CUSTOM("특수(전통·이색 숙소)");

    private final String label;
}
