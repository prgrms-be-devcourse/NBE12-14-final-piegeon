package world.chakchak.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.survey.dto.SurveyReportResponse;
import world.chakchak.domain.survey.dto.SurveyReportResponse.ActPrefCount;
import world.chakchak.domain.survey.dto.SurveyReportResponse.MemberAnswer;
import world.chakchak.domain.survey.dto.SurveyReportResponse.ReportItem;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.errorcode.SurveyFetchErrorCode;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;
import world.chakchak.domain.survey.service.SurveyReportService;
import world.chakchak.domain.user.entity.User;
import world.chakchak.global.error.BusinessException;

@ExtendWith(MockitoExtension.class)
class SurveyReportServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime CLOSED_AT = LocalDateTime.of(2027, 6, 4, 12, 0);
    private static final LocalDateTime BEFORE_CLOSE = CLOSED_AT.minusHours(1);
    private static final LocalDateTime AFTER_CLOSE = CLOSED_AT.plusMinutes(1);
    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 10L;

    @Mock private RoomMemberService roomMemberService;
    @Mock private SurveyResponseRepository surveyResponseRepository;

    @InjectMocks private SurveyReportService surveyReportService;

    private Room room;
    private RoomMember me;
    private RoomMember jieun;
    private RoomMember late;
    private long nextId = 100L;

    @BeforeEach
    void setUp() {
        room =
                Room.create(
                        "여름 여행",
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        "ABCD1234",
                        RoomStatus.SURVEYING,
                        clockAt(CLOSED_AT.minusDays(3)));
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        me = member("민수");
        jieun = member("지은");
        late = member("늦은멤버");
        given(roomMemberService.getActiveMember(ROOM_ID, USER_ID)).willReturn(me);
    }

    private static Clock clockAt(LocalDateTime time) {
        return Clock.fixed(time.atZone(SEOUL).toInstant(), SEOUL);
    }

    private RoomMember member(String name) {
        RoomMember member =
                RoomMember.create(
                        room,
                        User.create(name + "@example.com", name, "hash"),
                        clockAt(CLOSED_AT.minusDays(2)));
        ReflectionTestUtils.setField(member, "id", nextId++);
        return member;
    }

    private void surveyClosed() {
        ReflectionTestUtils.setField(room, "surveyClosedAt", CLOSED_AT);
    }

    private static SurveyResponse surveying(
            RoomMember member, LocalDateTime submittedAt, long budget, StayType stay, Pace pace) {
        return SurveyResponse.createForSurveying(
                member, budget, stay, pace, List.of(ActivityCode.FOOD), "없음", clockAt(submittedAt));
    }

    private static SurveyResponse withPreferences(
            RoomMember member,
            LocalDateTime submittedAt,
            List<ActivityCode> actPrefs,
            String avoidNote) {
        return SurveyResponse.createForSurveying(
                member,
                600_000L,
                StayType.HOTEL,
                Pace.NORMAL,
                actPrefs,
                avoidNote,
                clockAt(submittedAt));
    }

    private void givenAnswers(SurveyResponse... answers) {
        given(surveyResponseRepository.findAllActiveWithMemberByRoomId(ROOM_ID))
                .willReturn(List.of(answers));
    }

    private static MemberAnswer answerOf(RoomMember member, String value) {
        return new MemberAnswer(member.getId(), member.getUser().getName(), value);
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("마감 뒤에 낸 설문은 빼고 마감 시각까지 낸 설문으로 항목별 실명 분포와 만장일치를 정한다")
            void reportsItemsFromAnswersBeforeClose() {
                surveyClosed();
                givenAnswers(
                        surveying(me, BEFORE_CLOSE, 600_000L, StayType.HOTEL, Pace.RELAXED),
                        // 마지막 제출이 마감을 일으키면 제출 시각과 마감 시각이 같다
                        surveying(jieun, CLOSED_AT, 1_000_000L, StayType.HOTEL, Pace.NORMAL),
                        surveying(late, AFTER_CLOSE, 2_000_000L, StayType.PERSONAL, Pace.HURRY));

                SurveyReportResponse report = surveyReportService.getReport(ROOM_ID, USER_ID);

                assertThat(report.closedAt()).isEqualTo(CLOSED_AT);
                assertThat(report.respondentCount()).isEqualTo(2);
                assertThat(report.items())
                        .containsExactly(
                                new ReportItem(
                                        AgendaSubject.BUDGET,
                                        false,
                                        List.of(
                                                answerOf(me, "600000"),
                                                answerOf(jieun, "1000000"))),
                                new ReportItem(
                                        AgendaSubject.STAY,
                                        true,
                                        List.of(answerOf(me, "HOTEL"), answerOf(jieun, "HOTEL"))),
                                new ReportItem(
                                        AgendaSubject.PACE,
                                        false,
                                        List.of(
                                                answerOf(me, "RELAXED"),
                                                answerOf(jieun, "NORMAL"))));
            }

            @Test
            @DisplayName("예산은 만원 단위로 반올림해 같은 선지가 되면 만장일치로 보고 분포에는 입력한 금액을 그대로 보여준다")
            void comparesBudgetsAfterRounding() {
                surveyClosed();
                givenAnswers(
                        surveying(me, BEFORE_CLOSE, 1_226_000L, StayType.HOTEL, Pace.NORMAL),
                        surveying(jieun, BEFORE_CLOSE, 1_234_000L, StayType.HOTEL, Pace.NORMAL));

                ReportItem budget =
                        surveyReportService.getReport(ROOM_ID, USER_ID).items().getFirst();

                assertThat(budget.unanimous()).isTrue();
                assertThat(budget.distribution())
                        .containsExactly(answerOf(me, "1226000"), answerOf(jieun, "1234000"));
            }

            @Test
            @DisplayName("선호 행동은 늦게 낸 설문까지 세어 고른 인원이 많은 순으로, 같으면 정해진 순서로 보여준다")
            void countsActPrefsIncludingLateAnswers() {
                surveyClosed();
                givenAnswers(
                        withPreferences(
                                me,
                                BEFORE_CLOSE,
                                List.of(ActivityCode.CULTURE, ActivityCode.FOOD),
                                "없음"),
                        withPreferences(
                                jieun,
                                BEFORE_CLOSE,
                                List.of(ActivityCode.SHOPPING, ActivityCode.FOOD),
                                "없음"),
                        SurveyResponse.createForPlanning(
                                late,
                                List.of(ActivityCode.CULTURE, ActivityCode.NATURE),
                                "없음",
                                clockAt(AFTER_CLOSE)));

                List<ActPrefCount> actPrefs =
                        surveyReportService.getReport(ROOM_ID, USER_ID).actPrefs();

                assertThat(actPrefs)
                        .containsExactly(
                                new ActPrefCount("FOOD", "맛집", 2),
                                new ActPrefCount("CULTURE", "명소·문화탐방", 2),
                                new ActPrefCount("NATURE", "자연·힐링", 1),
                                new ActPrefCount("SHOPPING", "쇼핑·투어", 1));
            }

            @Test
            @DisplayName("피해야 할 것은 늦게 낸 설문까지 내용이 있는 것만 실명으로 보여준다")
            void listsAvoidNotesIncludingLateAnswers() {
                surveyClosed();
                givenAnswers(
                        withPreferences(me, BEFORE_CLOSE, List.of(ActivityCode.FOOD), "해산물 알레르기"),
                        withPreferences(jieun, BEFORE_CLOSE, List.of(ActivityCode.FOOD), "  "),
                        SurveyResponse.createForPlanning(
                                late,
                                List.of(ActivityCode.FOOD),
                                "높은 곳은 싫어요",
                                clockAt(AFTER_CLOSE)));

                List<MemberAnswer> avoidNotes =
                        surveyReportService.getReport(ROOM_ID, USER_ID).avoidNotes();

                assertThat(avoidNotes)
                        .containsExactly(answerOf(me, "해산물 알레르기"), answerOf(late, "높은 곳은 싫어요"));
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("설문이 아직 마감되지 않았으면 SURVEY_NOT_CLOSED를 던지고 응답을 조회하지 않는다")
            void rejectsBeforeSurveyCloses() {
                assertThatThrownBy(() -> surveyReportService.getReport(ROOM_ID, USER_ID))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode")
                        .isEqualTo(SurveyFetchErrorCode.SURVEY_NOT_CLOSED);
                verifyNoInteractions(surveyResponseRepository);
            }
        }
    }
}
