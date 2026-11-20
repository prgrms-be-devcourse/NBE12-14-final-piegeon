package world.chakchak.domain.place.dto;

import java.util.List;

public record PlaceBoardResponse(
        // 지금 방에 남아 있는 멤버 수. 칸(FULL) 계산의 기준
        long activeMemberCount,
        // 올릴 수 있는 후보 수 (여행 일수 x 10)
        long limit,
        List<PlaceCandidateItem> candidates) {}
