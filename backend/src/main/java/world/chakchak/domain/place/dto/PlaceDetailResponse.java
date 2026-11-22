package world.chakchak.domain.place.dto;

import java.time.LocalDateTime;
import java.util.List;
import world.chakchak.domain.place.entity.PlaceReactionType;

/** 카드를 눌렀을 때 펼쳐지는 장소 상세. 멤버 전원의 반응과 댓글 수를 함께 준다. (P-10) */
public record PlaceDetailResponse(
        Long placeId,
        // 프론트가 이 값으로 Google 사진(UI Kit)과 지도(Maps Embed)를 그린다
        String googlePlaceId,
        String name,
        // 장소 종류. 분류할 수 없는 종류면 null
        CategoryLabel category,
        String address,
        // "지도에서 열기" 버튼. 없으면 null
        String googleMapsUrl,
        PlaceStage stage,
        // 가고 싶어요 수 (남아 있는 멤버만)
        long agreeCount,
        // 지금 방에 남아 있는 멤버 수. "넷 다 가고 싶어해요" 같은 문구에 쓴다
        long activeMemberCount,
        MemberRef suggestedBy,
        LocalDateTime registeredAt,
        LocalDateTime confirmedAt,
        // 남아 있는 멤버 전원의 반응 (나도 포함). 프론트가 내 memberId로 "나는 여기"를, 나머지로 "친구들은"을 그린다
        List<MemberReaction> reactions,
        long commentCount) {

    /** 멤버 한 명의 반응. 아직 안 골랐으면 reaction이 null */
    public record MemberReaction(Long memberId, String name, ReactionLabel reaction) {}

    /** 장소 종류와 화면에 보여 줄 글자. 예: FOOD / "맛집" */
    public record CategoryLabel(PlaceCategory value, String label) {

        /** 카테고리가 없으면(null) null을 돌려준다 */
        public static CategoryLabel of(PlaceCategory category) {
            return category == null ? null : new CategoryLabel(category, category.getLabel());
        }
    }

    /** 반응 값과 화면에 보여 줄 글자. 예: WANT / "가고 싶어요" */
    public record ReactionLabel(PlaceReactionType value, String label) {

        public static ReactionLabel of(PlaceReactionType type) {
            return new ReactionLabel(type, type.getLabel());
        }
    }
}
