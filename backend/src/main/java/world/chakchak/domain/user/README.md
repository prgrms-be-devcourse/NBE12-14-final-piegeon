# user — 사용자

사용자 계정(이메일, 이름, 비밀번호 해시)을 저장하고 조회한다. 가입과 로그인 흐름은 [auth](../auth/README.md)가 맡고, user는 계정 데이터만 다룬다.

## API

| 메서드 | 경로 | 하는 일 |
| --- | --- | --- |
| GET | `/api/users/me` | 로그인한 내 정보 |

## 주요 클래스

| 클래스 | 하는 일 |
| --- | --- |
| [`User`](entity/User.java) | 사용자 계정 |
| [`UserService`](service/UserService.java) | 가입 저장(`register`), 조회, 로그인 검증용 자격 정보 조회 |

## 알아 둘 규칙과 설계

### 이메일 중복은 두 번 막는다

가입할 때 먼저 같은 이메일이 있는지 확인한다. 그런데 같은 이메일의 가입 요청 두 개가 동시에 들어오면 둘 다 이 확인을 통과할 수 있다. 그 경우는 DB 유니크 제약(`uk_users_email`)이 막고, `UserService`가 그 예외를 이메일 중복 에러로 바꿔 돌려준다.

### 비밀번호 검증과 해시는 auth가 한다

비밀번호는 8~64자이고 영문, 숫자, 특수문자만 쓸 수 있다. 이 검증은 auth의 `SignupRequest`가 하고, 해시도 auth의 `AuthService`가 `PasswordEncoder`로 만든 뒤 user에 넘긴다. user는 해시만 저장한다.

## 다른 패키지와의 관계

auth가 가입과 로그인에서 user를 부르고, room은 멤버의 이름을 보여 줄 때 user를 읽는다.
