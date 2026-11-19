package world.chakchak.domain.changelog.dto;

import java.util.List;

public record ChangeLogPageResponse(List<ChangeLogItemResponse> items, Long nextCursor) {}
