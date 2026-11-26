package world.chakchak.domain.survey.dto;

import java.util.List;

public record SurveyOptionsResponse(
        List<Option> stayTypes, List<Option> paces, List<Option> actPrefs, Limits limits) {

    public record Option(String value, String label) {}

    public record Limits(
            long budgetMin,
            long budgetMax,
            long actPrefsMin,
            long actPrefsMax,
            long avoidNoteMaxLength) {}
}
