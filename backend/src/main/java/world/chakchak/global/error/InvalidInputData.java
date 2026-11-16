package world.chakchak.global.error;

import java.util.List;

/** 입력 검증 실패 응답의 data. 어느 필드가 왜 틀렸는지 담는다. */
public record InvalidInputData(List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {}
}
