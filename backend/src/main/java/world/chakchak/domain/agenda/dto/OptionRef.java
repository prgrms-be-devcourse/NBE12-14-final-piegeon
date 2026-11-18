package world.chakchak.domain.agenda.dto;

import world.chakchak.domain.agenda.entity.AgendaOption;

/** 결정된 선택지 (V-01 resolvedOption) */
public record OptionRef(Long optionId, String label, String value) {

    public static OptionRef from(AgendaOption option) {
        return new OptionRef(option.getId(), option.getLabel(), option.getValue());
    }
}
