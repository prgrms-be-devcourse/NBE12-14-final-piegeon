# global — 공통 설정과 규칙

모든 도메인이 함께 쓰는 설정, 보안, 응답 형식, 에러 처리, 호출 제한, 공통 엔티티를 둔다. 특정 도메인의 업무 규칙은 여기에 두지 않는다.

## 하위 패키지

| 패키지 | 들어 있는 것 |
| --- | --- |
| `config` | 보안(`SecurityConfig`), 시각(`ClockConfig`), JPA 감사, 스케줄링, CORS, 호출 제한 등록 |
| `security` | 세션 로그인(`SessionAuthenticator`, `LoginUser`), 401·403 응답 |
| `response` | 모든 API의 응답 본문 `ApiResponse` |
| `error` | `BusinessException`, `ErrorCode`, `GlobalExceptionHandler` |
| `ratelimit` | `@RateLimit`과 토큰 버킷 인터셉터 |
| `entity` | 모든 엔티티가 상속하는 `BaseEntity` (`createdAt`, `modifiedAt`) |

## 알아 둘 규칙과 설계

### 응답은 성공과 실패가 같은 모양이다

[`ApiResponse`](response/ApiResponse.java)는 `success`, `code`, `message`, `data`를 담는다. `code`는 `ROOM_CREATED`, `ROOM_NOT_FOUND`처럼 이름으로 쓰고, 값이 없는 `message`와 `data`는 응답에서 빠진다. HTTP 상태 코드는 `ResponseEntity`로 정한다.

### 에러는 예외 하나로 던지고 한 곳에서 바꾼다

service와 엔티티는 [`BusinessException`](error/BusinessException.java)만 던진다. 에러 코드는 도메인별로 [`ErrorCode`](error/ErrorCode.java)를 구현한 enum에 두고(survey는 셋으로 나눴다), enum 상수 이름이 곧 응답의 `code`가 된다. 모든 API에 공통인 코드는 `CommonErrorCode`에 있다. 응답으로 바꾸는 일은 [`GlobalExceptionHandler`](error/GlobalExceptionHandler.java)가 맡는다. 요청 본문 검증이 실패하면 어느 필드가 왜 틀렸는지 `data`에 담아 돌려준다. `BusinessException`과 입력 오류는 응답 코드와 상관없이 스택트레이스 없이 warn으로 남기고, 처리하지 못한 예외만 스택트레이스와 함께 error로 남긴다. 로그인 없이 보호된 API를 부르면 controller 앞의 필터 단계에서 막힌다. 그래서 `GlobalExceptionHandler`가 자동으로 잡지 못하는데, [`ApiAuthenticationEntryPoint`](security/ApiAuthenticationEntryPoint.java)가 같은 처리로 넘겨 응답 모양을 맞춘다.

### 보안 설정

[`SecurityConfig`](config/SecurityConfig.java)는 `formLogin`, `httpBasic`, `logout`을 끄고, 로그인·로그아웃은 auth의 JSON API가 직접 처리하게 한다. 로그인 없이 열린 경로는 가입, 로그인, 로그아웃, 초대 미리보기, `/actuator/health`·`/actuator/prometheus`, Swagger뿐이다. CSRF 토큰 대신 `SameSite=Lax` 쿠키와 CORS 허용 주소 제한으로 막는다. 401을 낼 때 원래 요청을 세션에 저장하지 않도록 `NullRequestCache`를 쓴다. 저장하면 로그인하지 않은 요청마다 빈 세션이 만들어져 `SPRING_SESSION`에 행이 쌓이기 때문이다.

### 현재 시각은 `Clock`으로만 구한다

[`ClockConfig`](config/ClockConfig.java)가 `Asia/Seoul` 시간대의 `Clock`을 빈으로 만든다. 코드는 인자 없는 `LocalDateTime.now()`를 쓰지 않고, 이 `Clock`을 주입받아 `LocalDateTime.now(clock)`을 쓴다. 테스트에서는 `Clock.fixed(...)`로 바꿔 "3일 뒤"나 "출발일 00:00" 같은 시점을 만든다. DB에도 한국 시각이 그대로 저장된다.

### 호출 제한

controller 메서드에 `@RateLimit(key, limit, perSeconds)`를 붙이면 [`RateLimitInterceptor`](ratelimit/RateLimitInterceptor.java)가 Security 필터 다음, controller 앞에서 횟수를 센다. 로그인 전 API는 IP로, 로그인 후 API는 사용자로 센다. 넘으면 429와 `Retry-After` 헤더를 돌려준다. 버킷([`RateLimiter`](ratelimit/RateLimiter.java))은 Caffeine 캐시에 담아 서버 메모리에 둔다. 서버가 한 대라 이걸로 충분하지만, 서버를 늘리면 Redis 같은 공유 저장소로 바꿔야 한다. 로컬과 테스트 프로필에서는 꺼져 있다.

### 지연 로딩은 트랜잭션 안에서 끝낸다

`open-in-view: false`라서 트랜잭션이 끝나면 지연 로딩된 연관 엔티티를 더는 읽을 수 없다. 그래서 조회 service는 트랜잭션 안에서 DTO로 다 바꾼 뒤 돌려준다.
