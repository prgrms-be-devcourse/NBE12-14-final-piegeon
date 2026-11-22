package world.chakchak.domain.place.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 칸반 보드에서 장소 후보가 놓이는 칸. (명세 8-1)
 *
 * <p>DB에 저장하지 않고 조회할 때마다 계산한다. 멤버가 들어오거나 나가면 같은 후보라도 칸이 바뀔 수 있기 때문이다.
 */
@Getter
@RequiredArgsConstructor
public enum PlaceStage {
    // 여집합: 등록한 사람만 가고 싶어요
    COMPLEMENT("한 명만 원해요"),
    // 부분 교집합: 2명 이상 가고 싶어요
    PARTIAL("몇 명이 좋아해요"),
    // 전체 교집합: 활동 멤버 전원이 가고 싶어요 -> "확정하기" 버튼
    FULL("다 같이 가고 싶어요"),
    // 확정됨
    CONFIRMED("확정");

    /** 칸 제목. 변경 이력 문구에 쓴다 */
    private final String label;

    /**
     * 칸을 계산한다.
     *
     * @param confirmed 확정된 후보인지
     * @param wantCount 가고 싶어요(WANT) 반응 수
     * @param activeMemberCount 지금 방에 남아 있는 멤버 수
     */
    public static PlaceStage of(boolean confirmed, long wantCount, long activeMemberCount) {
        if (confirmed) {
            return CONFIRMED;
        }
        if (wantCount >= activeMemberCount) {
            return FULL;
        }
        if (wantCount >= 2) {
            return PARTIAL;
        }
        return COMPLEMENT;
    }
}
