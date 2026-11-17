-- 장소 상세(P-10)의 "지도에서 보기" 링크와 사진 프록시(P-11)에 쓰는 Google 사진 이름.
-- 둘 다 등록(P-02) 때 Google 장소 상세 결과로 채우고, 값이 없으면 NULL이다.
ALTER TABLE place_candidate
    ADD COLUMN google_maps_url VARCHAR(500) AFTER address,
    ADD COLUMN photo_name      VARCHAR(500) AFTER google_maps_url;
