import type { PlaceCategory } from "@/lib/types";

// 장소 종류를 나타내는 선 아이콘. 크기와 선 굵기는 디자인 v4의 일정 화면을 따른다
const PATHS: Record<PlaceCategory | "ETC", string> = {
  FOOD: "M7 3v6a2 2 0 0 0 4 0V3M9 11v10M17 3c-1.7 0-3 2.2-3 5.5V13h3zM17 13v8",
  NATURE: "M3 20l6-11 4 7 2.5-4L21 20z",
  ACTIVITY: "M4 9a2 2 0 0 0 2-2h12a2 2 0 0 0 2 2v6a2 2 0 0 0-2 2H6a2 2 0 0 0-2-2zM14 7v2M14 11v2M14 15v2",
  SHOPPING: "M5 8h14l1 13H4zM9 8V6a3 3 0 0 1 6 0v2",
  CULTURE: "M3 21h18M5 21V10M19 21V10M9.5 21V10M14.5 21V10M3 10h18L12 4z",
  // 종류를 모르는 곳은 지도 핀
  ETC: "M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z",
};

/** 장소 종류 아이콘 칸. 꾸밈이라 스크린 리더에는 읽히지 않는다 */
export function CategoryIcon({ category }: { category: PlaceCategory | null | undefined }) {
  return (
    <span className="cat" aria-hidden="true">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d={PATHS[category ?? "ETC"]} />
      </svg>
    </span>
  );
}
