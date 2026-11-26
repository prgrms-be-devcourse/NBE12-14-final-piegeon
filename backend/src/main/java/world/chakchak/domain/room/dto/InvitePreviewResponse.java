package world.chakchak.domain.room.dto;

import java.time.LocalDate;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;

public record InvitePreviewResponse(
        Long roomId,
        String name,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        RoomStatus status,
        long memberCount,
        boolean joinable,
        NotJoinableReason notJoinableReason,
        boolean surveyRequired) {

    public static InvitePreviewResponse from(
            Room room, long memberCount, NotJoinableReason notJoinableReason) {
        return new InvitePreviewResponse(
                room.getId(),
                room.getName(),
                room.getDestination(),
                room.getStartDate(),
                room.getEndDate(),
                room.getStatus(),
                memberCount,
                notJoinableReason == null,
                notJoinableReason,
                room.getStatus() != RoomStatus.FINALIZED);
    }
}
