package world.chakchak.domain.schedule.dto;

/** 블록을 고친 뒤 막지는 않고 알려만 주는 경고. */
public enum ScheduleWarning {
    // 블록 시간이 앞·뒤 블록과 순서가 어긋남 (블록 수정). 화면 순서는 순서값이 기준이다
    TIME_ORDER_MISMATCH
}
