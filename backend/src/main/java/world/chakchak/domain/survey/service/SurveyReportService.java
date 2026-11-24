package world.chakchak.domain.survey.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.survey.dto.SurveyReportResponse;
import world.chakchak.domain.survey.dto.SurveyReportResponse.ActPrefCount;
import world.chakchak.domain.survey.dto.SurveyReportResponse.MemberAnswer;
import world.chakchak.domain.survey.dto.SurveyReportResponse.ReportItem;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.errorcode.SurveyFetchErrorCode;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;
import world.chakchak.global.error.BusinessException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyReportService {

    private final RoomMemberService roomMemberService;
    private final SurveyResponseRepository surveyResponseRepository;

    /**
     * 설문 조율 리포트 (S-05). 저장하지 않고 조회할 때마다 활동 멤버의 설문으로 계산한다 (명세 F-05).
     *
     * <ul>
     *   <li>예산·숙소 타입·이동 강도의 분포와 만장일치는 마감 시각까지 낸 설문으로만 본다 (명세 6-1). 마지막 제출이 마감을 일으키면 제출 시각과 마감 시각이
     *       같으므로 같은 시각도 포함한다.
     *   <li>선호 행동과 피해야 할 것은 늦게 낸 설문까지 활동 멤버 전원의 것을 본다.
     * </ul>
     *
     * @throws BusinessException 설문이 아직 마감되지 않았으면 SURVEY_NOT_CLOSED
     */
    public SurveyReportResponse getReport(Long roomId, Long userId) {
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);
        LocalDateTime closedAt = me.getRoom().getSurveyClosedAt();
        if (closedAt == null) {
            throw new BusinessException(SurveyFetchErrorCode.SURVEY_NOT_CLOSED);
        }

        List<SurveyResponse> answers =
                surveyResponseRepository.findAllActiveWithMemberByRoomId(roomId);
        List<SurveyResponse> onTime =
                answers.stream()
                        .filter(answer -> !answer.getSubmittedAt().isAfter(closedAt))
                        .toList();

        List<ReportItem> items =
                List.of(
                        item(
                                AgendaSubject.BUDGET,
                                onTime,
                                SurveyResponse::getBudget,
                                AgendaOption::roundBudget),
                        item(
                                AgendaSubject.STAY,
                                onTime,
                                SurveyResponse::getStayType,
                                Function.identity()),
                        item(
                                AgendaSubject.PACE,
                                onTime,
                                SurveyResponse::getPace,
                                Function.identity()));
        return new SurveyReportResponse(
                closedAt, onTime.size(), items, actPrefs(answers), avoidNotes(answers));
    }

    /**
     * @param value 설문에서 이 항목의 값을 꺼낸다
     * @param sameOption 같은 선지가 되는 값끼리 같은 결과를 낸다. 예산은 만원 단위로 반올림해 선지를 만들므로, 금액이 달라도 반올림하면 같을 때 투표 없이
     *     결정된다. 그래서 반올림한 금액으로 비교한다
     */
    private static <T> ReportItem item(
            AgendaSubject subject,
            List<SurveyResponse> onTime,
            Function<SurveyResponse, T> value,
            Function<T, ?> sameOption) {
        List<SurveyResponse> answered =
                onTime.stream().filter(answer -> value.apply(answer) != null).toList();
        boolean unanimous = answered.stream().map(value).map(sameOption).distinct().count() == 1;
        List<MemberAnswer> distribution =
                answered.stream()
                        .map(answer -> memberAnswer(answer, String.valueOf(value.apply(answer))))
                        .toList();
        return new ReportItem(subject, unanimous, distribution);
    }

    // 고른 인원이 많은 순. 인원이 같으면 ActivityCode 선언 순이다
    private static List<ActPrefCount> actPrefs(List<SurveyResponse> answers) {
        Map<ActivityCode, Long> counts =
                answers.stream()
                        .flatMap(answer -> answer.getActPrefs().stream())
                        .collect(
                                Collectors.groupingBy(
                                        Function.identity(),
                                        () -> new EnumMap<>(ActivityCode.class),
                                        Collectors.counting()));
        Comparator<Map.Entry<ActivityCode, Long>> byCountDesc =
                Map.Entry.<ActivityCode, Long>comparingByValue().reversed();
        return counts.entrySet().stream()
                .sorted(byCountDesc.thenComparing(Map.Entry.comparingByKey()))
                .map(entry ->
                        new ActPrefCount(
                                entry.getKey().name(), entry.getKey().getLabel(), entry.getValue()))
                .toList();
    }

    private static List<MemberAnswer> avoidNotes(List<SurveyResponse> answers) {
        return answers.stream()
                .filter(answer -> answer.getAvoidNote() != null && !answer.getAvoidNote().isBlank())
                .map(answer -> memberAnswer(answer, answer.getAvoidNote()))
                .toList();
    }

    private static MemberAnswer memberAnswer(SurveyResponse answer, String value) {
        RoomMember member = answer.getRoomMember();
        return new MemberAnswer(member.getId(), member.getUser().getName(), value);
    }
}
