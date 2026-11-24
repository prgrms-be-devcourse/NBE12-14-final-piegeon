package world.chakchak.domain.survey.dto;

import java.time.LocalDateTime;
import java.util.List;
import world.chakchak.domain.agenda.entity.AgendaSubject;

/**
 * 설문 조율 리포트 (S-05).
 *
 * @param respondentCount 마감 시각까지 설문을 낸 활동 멤버 수
 * @param items 예산·숙소 타입·이동 강도 순
 * @param actPrefs 늦게 낸 설문까지 센 선호 행동. 고른 인원이 많은 순
 * @param avoidNotes 늦게 낸 설문까지 포함한 피해야 할 것. 내용이 있는 것만
 */
public record SurveyReportResponse(
        LocalDateTime closedAt,
        long respondentCount,
        List<ReportItem> items,
        List<ActPrefCount> actPrefs,
        List<MemberAnswer> avoidNotes) {

    /**
     * @param distribution 마감 시각까지 낸 설문의 실명 분포
     */
    public record ReportItem(
            AgendaSubject subject, boolean unanimous, List<MemberAnswer> distribution) {}

    /**
     * @param value 예산은 안건 선지처럼 만원 단위로 반올림한 금액(원), 숙소 타입·이동 강도는 enum 이름, 피해야 할 것은 입력한 글
     */
    public record MemberAnswer(Long memberId, String name, String value) {}

    public record ActPrefCount(String value, String label, long count) {}
}
