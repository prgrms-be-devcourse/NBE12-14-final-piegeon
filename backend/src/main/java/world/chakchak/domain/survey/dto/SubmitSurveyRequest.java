package world.chakchak.domain.survey.dto;

import java.util.List;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;

public record SubmitSurveyRequest(
        Long budget, StayType stayType, Pace pace, List<ActivityCode> actPrefs, String avoidNote) {}
