package world.chakchak.domain.changelog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ChangeLogConditionRequest(Long cursor, @Min(1) @Max(100) Long size) {

    public long sizeOrDefault() {
        return size == null ? 20L : size;
    }
}
