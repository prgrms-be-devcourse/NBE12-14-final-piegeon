package world.chakchak.domain.changelog.event;

public record RoomEvent(Long roomId, Long changeLogId, String name, Object data) {

    public RoomEvent {
        // Only persisted activity events participate in the SSE resume cursor.
        if (!"activity".equals(name)) {
            changeLogId = null;
        }
    }
}
