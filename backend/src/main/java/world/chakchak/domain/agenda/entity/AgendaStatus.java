package world.chakchak.domain.agenda.entity;

/** 안건 상태. 투표 중, 결정됨, 미결 마감(다음 회차 필요). */
public enum AgendaStatus {
    VOTING,
    RESOLVED,
    EXPIRED
}
