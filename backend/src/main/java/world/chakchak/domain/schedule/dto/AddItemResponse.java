package world.chakchak.domain.schedule.dto;

/** 블록 추가 결과. 새 블록 id와, 블록이 들어간 날의 바뀐 모습(새 version). (B-02) */
public record AddItemResponse(Long itemId, DaySnapshot day) {}
