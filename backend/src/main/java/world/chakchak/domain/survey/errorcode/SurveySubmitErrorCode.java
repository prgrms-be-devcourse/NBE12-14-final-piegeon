package world.chakchak.domain.survey.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import world.chakchak.global.error.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum SurveySubmitErrorCode implements ErrorCode {
    INVALID_ACTIVITY_COUNT(HttpStatus.BAD_REQUEST, "선호 활동은 중복 없이 1개 이상 3개 이하로 선택해야 합니다."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 설문을 제출했습니다.");

    private final HttpStatus status;
    private final String message;
}
