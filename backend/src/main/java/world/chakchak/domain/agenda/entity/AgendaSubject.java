package world.chakchak.domain.agenda.entity;

/** 안건 주제. 설문 충돌 세 가지(BUDGET, STAY, PACE)와 최종 확정(FINAL_PLAN)이다. */
public enum AgendaSubject {
    BUDGET,
    STAY,
    PACE,
    FINAL_PLAN;

    /** 설문에서 나온 안건인지. 다음 회차(V-04)는 설문 안건만 열 수 있다. */
    public boolean isSurvey() {
        return this != FINAL_PLAN;
    }
}
