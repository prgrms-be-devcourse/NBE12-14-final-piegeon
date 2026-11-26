# auth — 회원가입과 로그인

가입, 로그인, 로그아웃을 맡는다. 토큰을 쓰지 않고 세션 쿠키로 로그인 상태를 유지한다. 세션은 Spring Session JDBC로 DB(`SPRING_SESSION` 테이블)에 저장되고, 14일 동안 유지된다.

## API

| 메서드 | 경로 | 하는 일 | 호출 제한 |
| --- | --- | --- | --- |
| POST | `/api/auth/signup` | 회원가입 | IP당 1시간에 50번 |
| POST | `/api/auth/login` | 로그인 | IP당 1분에 60번 |
| POST | `/api/auth/logout` | 로그아웃 (세션이 만료됐어도 성공) | 없음 |

## 주요 클래스

| 클래스 | 하는 일 |
| --- | --- |
| [`AuthController`](controller/AuthController.java) | 요청을 받고, 세션 저장과 삭제를 `SessionAuthenticator`에 맡긴다 |
| [`AuthService`](service/AuthService.java) | 가입과 비밀번호 검증. 세션은 다루지 않는다 |
| [`AuthUserDetailsService`](service/AuthUserDetailsService.java), [`AuthUserDetails`](service/AuthUserDetails.java) | Spring Security가 이메일로 사용자를 찾고 비밀번호 해시를 비교할 때만 쓴다 |
| [`SessionAuthenticator`](../../global/security/SessionAuthenticator.java) | 세션에 로그인 정보를 저장하고 지운다 (global 패키지) |

## 알아 둘 규칙과 설계

### service는 웹을 모른다

`AuthService`는 `HttpServletRequest`나 세션을 전혀 다루지 않는다. 비밀번호가 맞는지만 확인하고 사용자 정보를 돌려준다. 세션에 저장하는 일은 controller가 `SessionAuthenticator`로 한다. 로그인할 때는 세션 ID를 새로 발급해서, 로그인 전부터 쓰던 세션 ID를 이어 쓰지 않게 한다(세션 고정 공격 방어). 그래서 service 테스트에 웹 환경이 필요 없다.

### 세션에는 바뀌지 않는 값만 넣는다

세션에는 `LoginUser`(`userId`, `email`)만 저장한다. 비밀번호 해시가 들어 있는 `AuthUserDetails`는 저장하지 않는다. 이름처럼 바뀔 수 있는 값은 필요할 때 `userId`로 DB에서 다시 읽는다.

### 로그인 실패 이유를 나누지 않는다

이메일이 없는 경우와 비밀번호가 틀린 경우 모두 `LOGIN_FAILED`다. 어느 이메일이 가입돼 있는지 알려 주지 않으려는 것이다. 다만 DB 장애 같은 다른 인증 예외까지 잡지는 않는다. 잡으면 장애가 "비밀번호 틀림"으로 보이고 error 로그도 남지 않는다.

### 로그아웃하면 실시간 연결도 끊는다

로그아웃하면 그 사용자의 SSE 연결을 `closeConnectionsForUser`로 모두 닫는다. 세션이 없어진 뒤에도 소식이 계속 가는 일을 막는다.

## 다른 패키지와의 관계

가입은 user의 `UserService.register`로 사용자를 만든다. 로그인 경로 설정, 401·403 응답 형식은 [global](../../global/README.md)의 `SecurityConfig`와 `security` 패키지에 있다.
