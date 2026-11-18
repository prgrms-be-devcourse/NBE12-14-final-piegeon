package world.chakchak.domain.place.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 장소 후보에 대한 멤버의 반응. (P-04, P-10)
 *
 * <p>명세 DTO에서는 이 enum을 PlaceReaction이라 부르지만, ERD 엔티티 이름(PlaceReaction)과 겹쳐서 PlaceReactionType으로 한다.
 */
@Getter
@RequiredArgsConstructor
public enum PlaceReactionType {
    WANT("가고 싶어요"),
    NOPE("별로예요");

    /** 화면에 보여줄 이름 */
    private final String label;
}
