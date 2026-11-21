package world.chakchak.domain.place.dto;

/** NOT_FULLY_AGREED. 가고 싶어요를 누른 사람 수와 방에 남아 있는 사람 수 (예: 3명 중 2명) */
public record NotFullyAgreedData(long agreeCount, long activeMemberCount) {}
