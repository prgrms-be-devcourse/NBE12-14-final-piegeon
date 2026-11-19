package world.chakchak.domain.room.dto;

public record JoinRoomResponse(Long roomId, Long memberId, boolean surveyRequired) {}
