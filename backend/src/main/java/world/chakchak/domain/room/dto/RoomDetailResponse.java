package world.chakchak.domain.room.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;

public record RoomDetailResponse(
        Long roomId,
        String name,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        String inviteCode,
        RoomStatus status,
        boolean readOnly,
        LocalDateTime opinionDeadlineAt,
        LocalDateTime surveyClosedAt,
        LocalDateTime finalizedAt,
        List<MemberItem> members) {

    public RoomDetailResponse {
        members = List.copyOf(members);
    }

    public static RoomDetailResponse from(Room room, List<MemberItem> members) {
        return new RoomDetailResponse(
                room.getId(),
                room.getName(),
                room.getDestination(),
                room.getStartDate(),
                room.getEndDate(),
                room.getInviteCode(),
                room.getStatus(),
                room.getStatus() == RoomStatus.FINALIZED,
                room.getOpinionDeadlineAt(),
                room.getSurveyClosedAt(),
                room.getFinalizedAt(),
                members);
    }

    public record MemberItem(
            Long memberId, String name, LocalDateTime joinedAt, boolean surveySubmitted) {}
}
