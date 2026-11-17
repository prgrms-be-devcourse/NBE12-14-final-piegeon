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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.global.entity.BaseEntity;

@Entity
@Table(name = "survey_response")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyResponse extends BaseEntity {

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
        this.roomMember = roomMember;
        this.budget = budget;
        this.stayType = stayType;
        this.pace = pace;
        this.actPrefs = actPrefs == null ? null : List.copyOf(actPrefs);
        this.avoidNote = avoidNote;
        this.submittedAt = LocalDateTime.now(clock);
    }

    public static SurveyResponse create(
            RoomMember roomMember,
            Long budget,
            StayType stayType,
            Pace pace,
            List<ActivityCode> actPrefs,
            String avoidNote,
            Clock clock) {
        return new SurveyResponse(roomMember, budget, stayType, pace, actPrefs, avoidNote, clock);
    }
}
