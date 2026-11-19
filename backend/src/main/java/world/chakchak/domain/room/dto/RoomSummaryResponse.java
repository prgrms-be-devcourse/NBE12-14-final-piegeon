package world.chakchak.domain.room.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import world.chakchak.domain.room.entity.RoomStatus;

public record RoomSummaryResponse(
        Long roomId,
        String name,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        RoomStatus status,
        int memberCount,
        LocalDateTime opinionDeadlineAt,
        boolean mySurveySubmitted) {

    public RoomSummaryResponse(
            Long roomId,
            String name,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            RoomStatus status,
            Long memberCount,
            Long surveyResponseCount,
            LocalDateTime opinionDeadlineAt) {
        this(
                roomId,
                name,
                destination,
                startDate,
                endDate,
                status,
                Math.toIntExact(memberCount),
                opinionDeadlineAt,
                surveyResponseCount > 0);
    }
}
