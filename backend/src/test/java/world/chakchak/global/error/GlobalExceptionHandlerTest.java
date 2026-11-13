package world.chakchak.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  @DisplayName("Spring이 4xx로 정한 예외는 500으로 바꾸지 않고 그 상태 코드로 응답한다")
  void keepsClientErrorStatus() {
    ResponseEntity<ErrorResponse> response =
        handler.handleUnexpected(new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    assertThat(response.getBody().code()).isEqualTo("COMMON_MALFORMED_REQUEST");
  }

  @Test
  @DisplayName("Spring이 404로 정한 예외는 COMMON_NOT_FOUND로 응답한다")
  void mapsNotFound() {
    ResponseEntity<ErrorResponse> response =
        handler.handleUnexpected(new ResponseStatusException(HttpStatus.NOT_FOUND));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("COMMON_NOT_FOUND");
  }

  @Test
  @DisplayName("예상하지 못한 예외는 500과 COMMON_INTERNAL_ERROR로 응답한다")
  void unexpectedIsInternalError() {
    ResponseEntity<ErrorResponse> response =
        handler.handleUnexpected(new IllegalStateException("boom"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().code()).isEqualTo("COMMON_INTERNAL_ERROR");
  }

  @Test
  @DisplayName("Spring이 5xx로 정한 예외는 500과 COMMON_INTERNAL_ERROR로 응답한다")
  void springServerErrorIsInternalError() {
    ResponseEntity<ErrorResponse> response =
        handler.handleUnexpected(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().code()).isEqualTo("COMMON_INTERNAL_ERROR");
  }
}
