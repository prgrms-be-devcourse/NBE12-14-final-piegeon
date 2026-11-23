package world.chakchak.domain.survey.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import world.chakchak.global.error.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum SurveyErrorCode implements ErrorCode {
    ACTIVITY_COUNT_INVALID(HttpStatus.BAD_REQUEST, "선호 활동은 중복 없이 1개 이상 3개 이하로 선택해야 합니다."),
    BUDGET_INVALID(HttpStatus.BAD_REQUEST, "예산은 1원 이상 99,999,999원 이하여야 합니다."),
    BUDGET_REQUIRED(HttpStatus.BAD_REQUEST, "예산은 필수입니다."),
    STAY_TYPE_REQUIRED(HttpStatus.BAD_REQUEST, "숙박 유형은 필수입니다."),
    PACE_REQUIRED(HttpStatus.BAD_REQUEST, "여행 페이스는 필수입니다.");

    private final HttpStatus status;
    private final String message;
}
