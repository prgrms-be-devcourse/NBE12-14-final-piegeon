package world.chakchak.global.error;

import org.springframework.http.HttpStatus;

/** 에러 코드의 공통 형태. 도메인마다 이 인터페이스를 구현한 enum을 하나씩 둔다. enum 상수 이름이 곧 응답의 code다. */
public interface ErrorCode {

  /** enum이 이미 제공하는 메서드. 응답의 code로 쓴다. */
  String name();

  HttpStatus getStatus();

  String getMessage();
}
