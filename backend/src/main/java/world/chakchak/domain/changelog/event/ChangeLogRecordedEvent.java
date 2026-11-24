package world.chakchak.domain.changelog.event;

import world.chakchak.domain.changelog.dto.ChangeLogItemResponse;

public record ChangeLogRecordedEvent(ChangeLogItemResponse activity) {}
