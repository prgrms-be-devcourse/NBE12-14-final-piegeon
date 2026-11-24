---
paths:
  - "src/main/java/**/controller/**"
  - "src/main/java/**/dto/**"
  - "src/main/java/**/errorcode/**"
  - "src/main/java/**/global/error/**"
---

# API 규칙

- 경로는 `/api`로 시작하고, 자원 이름은 복수형 kebab-case를 쓴다.
- controller 메서드는 `ResponseEntity<ApiResponse<XxxResponse>>`를 반환하고 상태 코드를 여기서 정한다. (`ResponseEntity.ok(...)`, `ResponseEntity.status(HttpStatus.CREATED).body(...)`, 본문이 없으면 `ResponseEntity.noContent().build()`) `@ResponseStatus`는 쓰지 않는다.
- 응답 본문은 성공과 실패 모두 `ApiResponse`(`success`, `code`, `message`, `data`)로 감싼다. 성공은 `ApiResponse.success(...)`, 실패는 `GlobalExceptionHandler`가 `ApiResponse.error(...)`로 만든다.
- 실패는 `BusinessException(ErrorCode)`를 던진다. controller에서 try-catch로 에러 응답을 만들지 않는다.
- DB unique 제약 위반(`DataIntegrityViolationException`)은 전역에서 처리하지 않는다. 중복이 예상되는 곳의 service에서 잡아 해당 도메인 에러 코드로 바꾼다. 잡지 않은 위반은 코드 버그이므로 500이 된다.
- 에러 코드는 도메인마다 `domain.<도메인>.errorcode` 패키지의 `XxxErrorCode` enum에 둔다. enum 상수 이름이 응답의 `code`이고, API 명세의 이름을 그대로 쓴다. 여러 도메인이 함께 쓰는 코드(`UNAUTHORIZED`, `INVALID_INPUT` 등)만 `global.error.CommonErrorCode`에 둔다.
- 성공 응답의 `code`는 `{대상}_{결과}` 형태다. (예: `USER_CREATED`, `ROOM_FETCHED`)
- 요청과 응답의 시각은 오프셋 없는 `LocalDateTime`이고 한국 시간(Asia/Seoul) 기준이다. (예: `2026-11-12T21:30:00` = 한국 시각 21:30) 프론트는 사용자가 해외에 있어도 받은 값을 그대로 한국 시간으로 표시한다.
- 입력 형식 검증은 요청 DTO의 Bean Validation으로, 업무 규칙 검증은 service와 엔티티에서 한다.
