package world.chakchak.domain.survey.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;

public record SubmitSurveyRequest(
        @Min(1) @Max(99_999_999) Long budget,
        StayType stayType,
        Pace pace,
        @NotNull @Size(min = 1) List<@NotNull ActivityCode> actPrefs,
        @NotBlank @Size(max = 100) String avoidNote) {}
