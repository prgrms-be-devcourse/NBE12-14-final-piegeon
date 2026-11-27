package world.chakchak.domain.agenda.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.event.RoomStatusChangedEvent;
import world.chakchak.domain.agenda.event.VoteUpdatedEvent;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.event.RoomEvent;
import world.chakchak.domain.changelog.service.ChangeLogService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;

/**
 * 안건 도메인의 방 활동 기록(change_log)과 일시 SSE 이벤트를 만든다.
 *
 * <ul>
 *   <li>안건이 열리고, 결정되고, 미결로 끝나고, 설문 마감·조율 완료·계획 확정이 되면 {@link ChangeLogService}로 한 줄을 남긴다. 그 기록이
 *       {@code activity} 이벤트(SSE id = 로그 id)로 나가고, 재연결 때 다시 보내진다.
 *   <li>표 수가 바뀐 것({@code vote.updated})과 D-DAY에 방이 만료된 것({@code room.status_changed})은 남기지 않는 일시
 *       이벤트라 id 없이 보낸다. 재연결 때 다시 보내지 않으므로 클라이언트가 다시 조회한다.
 * </ul>
 *
 * <p>둘 다 호출자의 트랜잭션 안에서 부르고, 실제 전송은 커밋 뒤에 한다({@code ChangeLogStreamListener}).
 *
 * <p>행위자는 그 일을 일으킨 요청의 멤버다. 설문 마감, 퇴장(R-06)의 결과, 스케줄러(13-2, 13-3)처럼 시스템이 한 일은 null이다.
 */
@Component
@RequiredArgsConstructor
public class AgendaChangePublisher {

    static final String VOTE_UPDATED = "vote.updated";
    static final String ROOM_STATUS_CHANGED = "room.status_changed";

    private final ChangeLogService changeLogService;
    private final ApplicationEventPublisher eventPublisher;

    /** 다음 회차(V-04)나 최종 확정 투표(C-01)가 열렸다. 예: "예산 2회차 투표가 열렸어요" */
    public void opened(RoomMember actor, AgendaItem agenda) {
        recordAgenda(
                actor,
                agenda,
                EventKind.AGENDA_OPENED,
                ActionStatus.CREATE,
                roundName(agenda) + " 투표가 열렸어요");
    }

    /**
     * 안건이 결정됐다. 예: "예산 1회차 결정: 30만원", "최종 확정 1회차 결정: 반대"
     *
     * <p>최종 확정이 동의로 끝난 경우에는 부르지 않는다. 그 결정이 곧 계획 확정이라 {@link #planFinalized} 한 줄만 남긴다.
     */
    public void resolved(RoomMember actor, AgendaItem agenda, AgendaOption option) {
        recordAgenda(
                actor,
                agenda,
                EventKind.AGENDA_RESOLVED,
                ActionStatus.CONFIRM,
                roundName(agenda) + " 결정: " + option.getLabel());
    }

    /** 안건이 정하지 못하고 미결(EXPIRED)로 끝났다. 예: "숙소 타입 1회차: 정하지 못하고 마감됐어요" */
    public void expired(RoomMember actor, AgendaItem agenda) {
        recordAgenda(
                actor,
                agenda,
                EventKind.AGENDA_EXPIRED,
                ActionStatus.UPDATE,
                roundName(agenda) + ": 정하지 못하고 마감됐어요");
    }

    /**
     * 설문이 마감됐다 (5-1). 시스템이 한 일이라 행위자가 없다. 만장일치로 바로 결정된 안건은 따로 남기지 않고 이 한 줄로 묶는다.
     *
     * @param votingCount 이번에 투표가 열린 안건 수. 0이면 열린 투표 없이 마감만 됐다
     */
    public void surveyClosed(Room room, int votingCount) {
        recordRoom(
                room,
                null,
                EventKind.SURVEY_CLOSED,
                ActionStatus.UPDATE,
                votingCount == 0 ? "설문이 마감됐어요" : "설문이 마감돼 투표 " + votingCount + "건이 열렸어요");
    }

    /** 예산·숙소 타입·이동 강도가 모두 결정돼 방이 계획 단계(PLANNING)로 넘어갔다 (5-2). */
    public void coordinationCompleted(Room room, RoomMember actor) {
        recordRoom(
                room,
                actor,
                EventKind.COORDINATION_COMPLETED,
                ActionStatus.UPDATE,
                "예산·숙소 타입·이동 강도가 모두 정해졌어요");
    }

    /** 최종 확정 투표가 동의로 끝나 방이 FINALIZED가 됐다 (7-1, 10-1). */
    public void planFinalized(Room room, RoomMember actor) {
        recordRoom(room, actor, EventKind.PLAN_FINALIZED, ActionStatus.CONFIRM, "여행 계획이 확정됐어요");
    }

    /** 표 수가 바뀌었다 (V-03, V-07, V-08). 방금 만든 상세 응답에서 표 수만 꺼내 보낸다. */
    public void voteUpdated(Long roomId, AgendaDetailResponse detail) {
        eventPublisher.publishEvent(
                new RoomEvent(roomId, null, VOTE_UPDATED, VoteUpdatedEvent.from(detail)));
    }

    /**
     * 방이 만료됐다. D-DAY 최종 집계에서 설문 조율을 끝내지 못했거나(13-3), 의견 마감까지 설문이 0건이었다(13-1). 명세의 기록 대상에 방 만료가 없어 기록
     * 없이 보낸다.
     *
     * <p>두 경우 모두 만료와 함께 남는 기록이 없을 수 있다. 이 이벤트가 없으면 접속 중인 멤버는 화면을 다시 불러오기 전까지 수정할 수 있는 방으로 보게 된다.
     */
    public void roomExpired(Room room) {
        eventPublisher.publishEvent(
                new RoomEvent(
                        room.getId(),
                        null,
                        ROOM_STATUS_CHANGED,
                        new RoomStatusChangedEvent(RoomStatus.EXPIRED)));
    }

    private void recordAgenda(
            RoomMember actor,
            AgendaItem agenda,
            EventKind eventKind,
            ActionStatus actionStatus,
            String summary) {
        changeLogService.record(
                agenda.getRoom(),
                actor,
                EntityType.AGENDA,
                agenda.getId(),
                eventKind,
                actionStatus,
                summary);
    }

    private void recordRoom(
            Room room,
            RoomMember actor,
            EventKind eventKind,
            ActionStatus actionStatus,
            String summary) {
        changeLogService.record(
                room, actor, EntityType.PLAN_ROOM, room.getId(), eventKind, actionStatus, summary);
    }

    // 예: "예산 1회차", "최종 확정 2회차", "'렌터카 빌릴까?' 1회차"
    private static String roundName(AgendaItem agenda) {
        return subjectName(agenda) + " " + agenda.getRound() + "회차";
    }

    private static String subjectName(AgendaItem agenda) {
        return switch (agenda.getSubject()) {
            case BUDGET -> "예산";
            case STAY -> "숙소 타입";
            case PACE -> "이동 강도";
            case FINAL_PLAN -> "최종 확정";
            // 자유 안건은 멤버가 정한 제목이 곧 주제 이름이다
            case CUSTOM -> "'" + agenda.getTitle() + "'";
        };
    }
}
