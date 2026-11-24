package world.chakchak.domain.survey.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import world.chakchak.global.error.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum SurveyFetchErrorCode implements ErrorCode {
    SURVEY_NOT_FOUND(HttpStatus.NOT_FOUND, "설문 응답을 찾을 수 없습니다."),
    SURVEY_NOT_CLOSED(HttpStatus.CONFLICT, "설문이 아직 마감되지 않았습니다.");

    private final HttpStatus status;
    private final String message;
}
