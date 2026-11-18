package world.chakchak.domain.room.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;

public record RoomCreatedResponse(
        Long roomId,
        String name,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        String inviteCode,
        RoomStatus status,
        LocalDateTime opinionDeadlineAt,
        LocalDateTime surveyClosedAt,
        LocalDateTime finalizedAt,
        boolean readOnly,
        List<Member> members) {

    public static RoomCreatedResponse from(Room room, RoomMember member) {
        return new RoomCreatedResponse(
                room.getId(),
                room.getName(),
                room.getDestination(),
                room.getStartDate(),
                room.getEndDate(),
                room.getInviteCode(),
                room.getStatus(),
                room.getOpinionDeadlineAt(),
                room.getSurveyClosedAt(),
                room.getFinalizedAt(),
                false,
                List.of(
                        new Member(
                                member.getId(),
                                member.getUser().getName(),
                                member.getJoinedAt(),
                                false)));
    }

    public record Member(
            Long memberId, String name, LocalDateTime joinedAt, boolean surveySubmitted) {}
}
