package world.chakchak.domain.changelog.event;

public record RoomMemberLeftEvent(Long roomId, Long memberId) {}
