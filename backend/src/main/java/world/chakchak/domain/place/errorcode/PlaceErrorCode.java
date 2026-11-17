package world.chakchak.domain.place.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import world.chakchak.global.error.ErrorCode;

/**
 * 장소 후보(PlaceCandidate)와 동의(PlaceAgreement) 관련 에러 코드.
 *
 * <p>상수 이름이 곧 응답의 code다. 노션 명세(API 명세서와 Mock Server)의 P-01~P-09 에러 표에 적힌 이름을 그대로 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum PlaceErrorCode implements ErrorCode {
    // 없거나 다른 방의 장소 후보 (P-04~P-09, B-02)
    PLACE_CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 후보를 찾을 수 없습니다."),
    // Google에 없는 장소 (P-02)
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "Google에 없는 장소입니다."),
    // 같은 방에 이미 올라온 장소 (P-02)
    PLACE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "같은 방에 이미 올라온 장소입니다."),
    // 후보 수가 (여행 일수 × 10)개를 넘음 (P-02)
    PLACE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "장소 후보는 (여행 일수 x 10)개까지 올릴 수 있습니다."),
    // 이미 확정된 장소에 동의·취소·삭제 (P-04, P-05, P-07)
    PLACE_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확정된 장소입니다."),
    // 활동 멤버 전원이 동의하지 않았는데 확정하기 (P-06)
    NOT_FULLY_AGREED(HttpStatus.CONFLICT, "활동 멤버 전원이 동의하지 않았습니다."),
    // 등록자가 아닌데 후보 삭제 (P-07)
    NOT_PLACE_OWNER(HttpStatus.FORBIDDEN, "장소를 등록한 사람만 삭제할 수 있습니다."),
    // 확정 안 된 장소를 일정 블록에 배치 (B-02, 장소 규칙이라 여기에 둠)
    PLACE_NOT_CONFIRMED(HttpStatus.CONFLICT, "확정이 되지 않은 장소는 일정에 넣을 수 없습니다."),
    // Google Places 호출 실패 (P-01, P-02)
    PLACES_API_ERROR(HttpStatus.BAD_GATEWAY, "장소 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
