package world.chakchak.domain.survey.service;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import world.chakchak.domain.survey.dto.SurveyOptionsResponse;
import world.chakchak.domain.survey.dto.SurveyOptionsResponse.Limits;
import world.chakchak.domain.survey.dto.SurveyOptionsResponse.Option;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;

@Service
public class SurveyOptionsService {

    public SurveyOptionsResponse getOptions() {
        return new SurveyOptionsResponse(
                options(StayType.values()),
                options(Pace.values()),
                options(ActivityCode.values()),
                new Limits(1, 99_999_999, 1, 3, 100));
    }

    private static <E extends Enum<E>> List<Option> options(E[] values) {
        return Arrays.stream(values).map(SurveyOptionsService::option).toList();
    }

    private static Option option(Enum<?> value) {
        String label =
                switch (value) {
                    case StayType stayType -> stayType.getLabel();
                    case Pace pace -> pace.getLabel();
                    case ActivityCode activityCode -> activityCode.getLabel();
                    default -> throw new IllegalArgumentException("지원하지 않는 선택지입니다.");
                };
        return new Option(value.name(), label);
    }
}
