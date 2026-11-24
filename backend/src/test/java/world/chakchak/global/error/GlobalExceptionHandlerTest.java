package world.chakchak.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.web.server.ResponseStatusException;
import world.chakchak.global.response.ApiResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Spring이 4xx로 정한 예외는 500으로 바꾸지 않고 그 상태 코드로 응답한다")
    void keepsClientErrorStatus() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody().code()).isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("Spring이 404로 정한 예외는 NOT_FOUND로 응답한다")
    void mapsNotFound() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500과 INTERNAL_ERROR로 응답한다")
    void unexpectedIsInternalError() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Spring이 5xx로 정한 예외는 500과 INTERNAL_ERROR로 응답한다")
    void springServerErrorIsInternalError() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(
                        new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("로그인 검증 중 서버 쪽 오류(DB 장애 등)는 401이 아니라 500으로 응답한다")
    void authenticationServiceErrorIsInternalError() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAuthenticationFailure(
                        new InternalAuthenticationServiceException("DB 연결 실패"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("data를 담은 업무 예외는 에러 코드의 상태 코드와 code에 그 data를 함께 담아 응답한다")
    void businessExceptionWithData() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleBusiness(
                        new BusinessException(
                                CommonErrorCode.VERSION_CONFLICT, Map.of("placeId", 9L)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo("VERSION_CONFLICT");
        assertThat(response.getBody().message())
                .isEqualTo(CommonErrorCode.VERSION_CONFLICT.getMessage());
        assertThat(response.getBody().data()).isEqualTo(Map.of("placeId", 9L));
    }

    @Test
    @DisplayName("data 없이 만든 업무 예외는 data가 null이라 응답 JSON에서 data가 빠진다")
    void businessExceptionWithoutData() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleBusiness(new BusinessException(CommonErrorCode.INVALID_INPUT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_INPUT");
        assertThat(response.getBody().data()).isNull();
    }
}
