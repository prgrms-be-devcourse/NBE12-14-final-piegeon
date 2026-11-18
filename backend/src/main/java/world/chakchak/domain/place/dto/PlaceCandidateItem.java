package world.chakchak.domain.place.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 칸반 카드 하나, P-03 목록의 항목이고, P-02 등록 응답의 data로도 그대로 쓴다. */
public record PlaceCandidateItem(
        Long placeId,
        String googlePlaceId,
        String name,
        String address,
        String placeType,
        // 칸 (계산값, PlaceStage.of)
        PlaceStage stage,
        // 가고 싶어요 수
        long agreeCount,
        // 가고 싶어요를 누른 멤버
        List<MemberRef> agreedBy,
        // 후보를 올린 멤버
        MemberRef suggestedBy,
        // 확정 시각. 미확정이면 null
        LocalDateTime confirmedAt) {}
