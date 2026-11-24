package world.chakchak.domain.survey.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SurveyMeResponse(
        Long surveyId,
        Long budget,
        Option stayType,
        Option pace,
        List<Option> actPrefs,
        String avoidNote,
        LocalDateTime submittedAt) {

    public record Option(String value, String label) {}
}
