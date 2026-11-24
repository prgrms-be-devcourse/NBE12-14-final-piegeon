package world.chakchak.domain.survey.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;

@Converter
public class ActivityCodeConverter implements AttributeConverter<List<ActivityCode>, String> {

    @Override
    public String convertToDatabaseColumn(List<ActivityCode> activityCodes) {
        if (activityCodes == null) {
            return null;
        }
        return activityCodes.stream()
                .distinct()
                .map(Enum::name)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    @Override
    public List<ActivityCode> convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",", -1)).map(ActivityCode::valueOf).distinct().toList();
    }
}
