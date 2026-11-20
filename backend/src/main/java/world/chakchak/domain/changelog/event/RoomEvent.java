package world.chakchak.domain.changelog.event;

public record RoomEvent(Long roomId, Long changeLogId, String name, Object data) {}
