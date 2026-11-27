package world.chakchak.domain.schedule.dto;

import java.util.List;

/** 두 날을 맞바꾼 결과. 바뀐 두 날의 모습(새 일차, 새 날짜, 새 version)을 일차 순서로 담는다. */
public record SwapDayResponse(List<DaySnapshot> days) {}
