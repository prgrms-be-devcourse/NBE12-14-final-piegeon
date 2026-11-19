package world.chakchak.domain.room.dto;

import java.util.List;

public record RoomListResponse(List<RoomSummaryResponse> rooms) {

    public RoomListResponse {
        rooms = List.copyOf(rooms);
    }
}
