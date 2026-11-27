package world.chakchak.domain.agenda.entity;

/**
 * 안건 주제. 설문 충돌 세 가지(BUDGET, STAY, PACE), 최종 확정(FINAL_PLAN), 멤버가 직접 연 자유 안건(CUSTOM)이다.
 *
 * <p>자유 안건은 주제 이름을 제목(title)에 담고, 같은 방에서 제목으로 서로를 구분한다 (V-09).
 */
public enum AgendaSubject {
    BUDGET,
    STAY,
    PACE,
    FINAL_PLAN,
    CUSTOM;

    /** 설문에서 나온 안건인지. 설문 조율 완료(5-2)와 D-DAY 최종 집계(13-3)는 이 안건들만 본다. */
    public boolean isSurvey() {
        return this == BUDGET || this == STAY || this == PACE;
    }

    /** 미결로 끝난 뒤 다음 회차(V-04)를 열 수 있는 주제인지. 최종 확정은 C-01로 다시 연다. */
    public boolean isReopenable() {
        return this != FINAL_PLAN;
    }
}
