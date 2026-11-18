package world.chakchak.domain.place.dto;

/** 응답에 멤버를 담을 때 쓰는 최소 정보. (P-02, P-03의 agreedBy, suggestedBy) */
public record MemberRef(Long memberId, String name) {}
