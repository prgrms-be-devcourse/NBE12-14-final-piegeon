package world.chakchak.global.ratelimit;

/** 호출 횟수를 누구 기준으로 셀지. */
public enum RateLimitKey {
    /** 요청한 IP. 로그인 전에 부르는 API(로그인, 가입, 초대 미리보기)에 쓴다. */
    IP,
    /** 로그인한 사용자. 로그인해야 부를 수 있는 API에 쓴다. 같은 IP를 쓰는 여러 사람이 서로 막지 않는다. */
    USER
}
