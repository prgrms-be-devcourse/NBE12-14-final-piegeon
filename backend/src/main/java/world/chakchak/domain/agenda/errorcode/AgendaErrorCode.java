package world.chakchak.domain.agenda.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import world.chakchak.global.error.ErrorCode;

/**
 * 안건(AgendaItem), 선택지(AgendaOption), 투표(Vote) 관련 에러 코드.
 *
 * <p>상수 이름이 곧 응답의 code다. API 명세 V-01~V-08 에러 표에 적힌 이름을 그대로 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum AgendaErrorCode implements ErrorCode {
    // 없거나 다른 방의 안건 (V-02~V-08)
    AGENDA_NOT_FOUND(HttpStatus.NOT_FOUND, "안건을 찾을 수 없습니다."),
    // 이미 끝났거나 마감 시각이 지난 안건에 투표·선지 추가·삭제 (V-03, V-07, V-08)
    AGENDA_CLOSED(HttpStatus.CONFLICT, "이미 끝났거나 마감된 안건입니다."),
    // 최종 확정 안건이거나 EXPIRED가 아닌 안건의 다음 회차 열기 (V-04)
    AGENDA_NOT_REOPENABLE(HttpStatus.CONFLICT, "다음 회차를 열 수 없는 안건입니다."),
    // 출발일 00:00(D-DAY)이 됐거나 지나서 안건을 열 수 없음 (5-1 설문 마감, V-04, V-09, C-01)
    // 명세에는 "D-DAY 이후에는 열 수 없다(409)"만 있고 코드 이름이 없어 새로 정했다.
    AGENDA_DEADLINE_PASSED(HttpStatus.CONFLICT, "출발일이 되어 안건을 열 수 없습니다."),
    // 같은 방에 같은 제목의 자유 안건이 이미 있음 (V-09). 다시 정하려면 그 안건의 다음 회차(V-04)를 연다
    AGENDA_TITLE_DUPLICATED(HttpStatus.CONFLICT, "같은 제목의 안건이 이미 있습니다."),
    // 이미 다음 회차가 열림 (V-04)
    AGENDA_ALREADY_REOPENED(HttpStatus.CONFLICT, "이미 다음 회차가 열렸습니다."),
    // 안건이 열린 뒤 합류한 멤버 (V-03, V-07)
    NOT_ELIGIBLE_VOTER(HttpStatus.FORBIDDEN, "이 안건에 투표할 자격이 없습니다."),
    // 다른 안건의 선택지에 투표 (V-03)
    OPTION_NOT_IN_AGENDA(HttpStatus.BAD_REQUEST, "이 안건의 선택지가 아닙니다."),
    // 최종 확정 반대인데 코멘트 없음 (V-03)
    REJECT_COMMENT_REQUIRED(HttpStatus.BAD_REQUEST, "반대할 때는 이유를 남겨 주세요."),
    // 없거나 다른 안건의 선지 (V-08)
    OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "선택지를 찾을 수 없습니다."),
    // 예산 안건이 아닌데 선지 추가 (V-07)
    OPTION_NOT_ADDABLE(HttpStatus.CONFLICT, "예산 안건에만 선지를 추가할 수 있습니다."),
    // 이 회차에 이미 선지를 추가함 (V-07)
    OPTION_ADD_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "한 회차에 선지는 1개만 추가할 수 있습니다."),
    // 같은 금액의 선지가 이미 있음 (V-07)
    OPTION_ALREADY_EXISTS(HttpStatus.CONFLICT, "같은 금액의 선지가 이미 있습니다."),
    // 내가 추가한 선지가 아님 (V-08)
    NOT_OPTION_OWNER(HttpStatus.FORBIDDEN, "내가 추가한 선지만 삭제할 수 있습니다."),
    // 다른 사람의 표가 있는 선지 삭제 (V-08)
    OPTION_HAS_VOTES(HttpStatus.CONFLICT, "다른 멤버가 투표한 선지는 삭제할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
