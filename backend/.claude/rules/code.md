---
paths:
  - "src/main/java/**"
---

# 코드 규칙

- 호출 방향은 controller → service → repository다. controller에서 repository를 쓰지 않는다.
- service에서 HttpServletRequest, HttpSession, SecurityContextHolder를 쓰지 않는다.
  controller가 `@AuthenticationPrincipal LoginUser`에서 userId를 꺼내 인자로 넘긴다.
- 다른 도메인의 repository를 직접 주입하지 않는다. 그 도메인의 service를 쓴다.
- `global`에서 `domain`을 import하지 않는다.
- 상태 전이 규칙은 엔티티 메서드에 둔다.
- 현재 시각은 주입받은 `Clock`으로 구한다. `LocalDateTime.now(clock)`처럼 쓰고, `LocalDateTime.now()`를 인자 없이 부르지 않는다.
- 시각 타입은 `LocalDateTime`이고 서비스 시간대(Asia/Seoul) 기준이다. DB에도 한국 시각이 그대로 저장된다. `spring.jpa.properties.hibernate.jdbc.time_zone`은 설정하지 않는다. 설정하면 서버 시간대에 따라 저장되는 값이 달라진다.
- 요청·응답 DTO는 record로 만들고 이름은 `XxxRequest`, `XxxResponse`로 끝낸다. 엔티티를 응답으로 내보내지 않는다.
- 코드 포맷은 Google Java Format의 AOSP 스타일(들여쓰기 4칸, 한 줄 100자)이다. 손으로 맞추지 않는다. 로컬에서는 컴파일할 때(`./gradlew build`, IntelliJ 빌드·실행·테스트) `spotlessApply`가 자동으로 실행된다. CI는 고치지 않고 검사만 하므로, 포맷이 어긋난 채 push하면 CI가 실패한다.
- 의존성은 생성자 주입(`@RequiredArgsConstructor`)만 쓴다.
- 엔티티에 `@Setter`, `@Data`를 쓰지 않는다. 연관관계는 `FetchType.LAZY`로 둔다.
- JSON 라이브러리는 Jackson 3이다. ObjectMapper 등은 `tools.jackson.*`에서 import한다.
  `com.fasterxml.jackson.databind.*`는 쓰지 않는다. 어노테이션만 `com.fasterxml.jackson.annotation.*`를 쓴다.
- 방에 속한 API는 service 첫 줄에서 `RoomMemberService.getActiveMember(roomId, userId)`로 멤버인지 확인한다.
