package world.chakchak.domain.place.event;

import world.chakchak.domain.place.dto.PlaceStage;

/**
 * SSE {@code place.agreement_changed}의 data. 누군가 가고 싶어요·별로예요를 누르거나 취소하면 보낸다.
 *
 * <p>변경 기록에는 남기지 않는다(칸이 바뀐 경우만 PLACE_STAGE_CHANGED로 남는다). 받는 화면은 보드를 다시 불러온다.
 */
public record PlaceAgreementChangedEvent(Long placeId, PlaceStage stage, long agreeCount) {}
