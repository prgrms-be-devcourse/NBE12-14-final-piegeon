package world.chakchak.domain.survey.dto;

import java.time.LocalDateTime;

public record SubmitSurveyResponse(
        Long surveyId, LocalDateTime submittedAt, boolean surveyClosed) {}
