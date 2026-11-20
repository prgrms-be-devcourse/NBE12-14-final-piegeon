package world.chakchak.domain.survey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.survey.errorcode.SurveyErrorCode;
import world.chakchak.global.entity.BaseEntity;
import world.chakchak.global.error.BusinessException;

@Entity
@Table(name = "survey_response")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyResponse extends BaseEntity {

    private static final long MIN_BUDGET = 1L;
    private static final long MAX_BUDGET = 99_999_999L;
    private static final int MIN_ACT_PREFS = 1;
    private static final int MAX_ACT_PREFS = 3;
    private static final int MAX_AVOID_NOTE_LENGTH = 100;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_room_member_id", nullable = false, unique = true)
    private RoomMember roomMember;

    private Long budget;

    @Enumerated(EnumType.STRING)
    @Column(name = "stay_type", length = 20)
    private StayType stayType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Pace pace;

    @Convert(converter = ActivityCodeConverter.class)
    @Column(name = "act_pref", nullable = false, length = 100)
    private List<ActivityCode> actPrefs;

    @Column(name = "avoid_note", length = 100)
    private String avoidNote;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    private SurveyResponse(
            RoomMember roomMember,
            Long budget,
            StayType stayType,
            Pace pace,
            List<ActivityCode> actPrefs,
            String avoidNote,
            Clock clock) {
        if (actPrefs == null || actPrefs.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(SurveyErrorCode.ACTIVITY_COUNT_INVALID);
        }
        List<ActivityCode> distinctActPrefs = actPrefs.stream().distinct().toList();
        if (distinctActPrefs.size() < MIN_ACT_PREFS || distinctActPrefs.size() > MAX_ACT_PREFS) {
            throw new BusinessException(SurveyErrorCode.ACTIVITY_COUNT_INVALID);
        }
        if (budget != null && (budget < MIN_BUDGET || budget > MAX_BUDGET)) {
            throw new BusinessException(SurveyErrorCode.BUDGET_INVALID);
        }
        if (avoidNote == null || avoidNote.isBlank()) {
            throw new BusinessException(SurveyErrorCode.AVOID_NOTE_REQUIRED);
        }
        if (avoidNote != null && avoidNote.length() > MAX_AVOID_NOTE_LENGTH) {
            throw new BusinessException(SurveyErrorCode.AVOID_NOTE_TOO_LONG);
        }

        this.roomMember = roomMember;
        this.budget = budget;
        this.stayType = stayType;
        this.pace = pace;
        this.actPrefs = List.copyOf(distinctActPrefs);
        this.avoidNote = avoidNote;
        this.submittedAt = LocalDateTime.now(clock);
    }

    public static SurveyResponse createForSurveying(
            RoomMember roomMember,
            Long budget,
            StayType stayType,
            Pace pace,
            List<ActivityCode> actPrefs,
            String avoidNote,
            Clock clock) {
        if (budget == null) {
            throw new BusinessException(SurveyErrorCode.BUDGET_REQUIRED);
        }
        if (stayType == null) {
            throw new BusinessException(SurveyErrorCode.STAY_TYPE_REQUIRED);
        }
        if (pace == null) {
            throw new BusinessException(SurveyErrorCode.PACE_REQUIRED);
        }

        return new SurveyResponse(roomMember, budget, stayType, pace, actPrefs, avoidNote, clock);
    }

    public static SurveyResponse createForPlanning(
            RoomMember roomMember, List<ActivityCode> actPrefs, String avoidNote, Clock clock) {
        return new SurveyResponse(roomMember, null, null, null, actPrefs, avoidNote, clock);
    }
}
