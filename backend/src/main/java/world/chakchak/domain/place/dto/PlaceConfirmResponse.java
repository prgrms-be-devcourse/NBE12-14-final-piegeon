package world.chakchak.domain.place.dto;

import java.time.LocalDateTime;

/** 확정하기 응답. 확정된 장소와 확정한 시각 */
public record PlaceConfirmResponse(Long placeId, LocalDateTime confirmedAt) {}
