package world.chakchak.domain.comment.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.changelog.event.RoomEvent;
import world.chakchak.domain.comment.dto.CommentResponse;
import world.chakchak.domain.comment.entity.Comment;
import world.chakchak.domain.comment.event.CommentCreatedEvent;
import world.chakchak.domain.comment.repository.CommentRepository;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.room.entity.RoomMember;

/**
 * 코멘트 저장과 조회.
 *
 * <p>다른 도메인의 service를 주입받지 않는다. 안건·장소가 이 방의 것인지는 부르는 쪽(AgendaService, PlaceCandidateService)이 먼저
 * 확인하고 넘긴다. 그래야 그 service들이 이 service를 써도 서로를 주입받는 순환이 생기지 않는다.
 *
 * <p>코멘트를 남기면 같은 방에 SSE {@code comment.created}를 보낸다(커밋 뒤). 코멘트를 저장하는 길(V-03 반대 이유, V-06, P-09)이 모두
 * 여기를 지나므로 부르는 쪽은 따로 보내지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    static final String COMMENT_CREATED = "comment.created";

    private final CommentRepository commentRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 안건 코멘트를 오래된 순으로 돌려준다. (V-05) */
    public List<CommentResponse> getOnAgenda(Long agendaId) {
        return commentRepository.findAllByAgendaItemIdOrderByIdAsc(agendaId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    /** 안건 코멘트를 남긴다. (V-03 최종 확정 반대 이유, V-06) */
    @Transactional
    public Comment addOnAgenda(AgendaItem agenda, RoomMember member, String body) {
        Comment saved = commentRepository.save(Comment.onAgenda(agenda, member, body));
        publishCreated(
                member, CommentCreatedEvent.onAgenda(agenda.getId(), CommentResponse.from(saved)));
        return saved;
    }

    /** 장소 후보 코멘트를 오래된 순으로 돌려준다. (P-08) */
    public List<CommentResponse> getOnPlace(Long placeCandidateId) {
        return commentRepository.findAllByPlaceCandidateIdOrderByIdAsc(placeCandidateId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    /** 장소 후보의 코멘트 수. (P-10 commentCount) */
    public long countOnPlace(Long placeCandidateId) {
        return commentRepository.countByPlaceCandidateId(placeCandidateId);
    }

    /** 장소 후보 코멘트를 남긴다. (P-09) */
    @Transactional
    public Comment addOnPlace(PlaceCandidate placeCandidate, RoomMember member, String body) {
        Comment saved = commentRepository.save(Comment.onPlace(placeCandidate, member, body));
        publishCreated(
                member,
                CommentCreatedEvent.onPlace(placeCandidate.getId(), CommentResponse.from(saved)));
        return saved;
    }

    /** 장소 후보의 코멘트를 모두 지운다. 후보를 지우기 전에 먼저 부른다. (P-07) */
    @Transactional
    public void deleteOnPlace(Long placeCandidateId) {
        commentRepository.deleteByPlaceCandidateId(placeCandidateId);
    }

    // 작성자는 부르는 쪽이 이 방의 활동 멤버인지 확인했으므로 작성자의 방이 코멘트의 방이다. 코멘트는 기록하지 않아 id 없이 보낸다
    private void publishCreated(RoomMember author, CommentCreatedEvent event) {
        eventPublisher.publishEvent(
                new RoomEvent(author.getRoom().getId(), null, COMMENT_CREATED, event));
    }
}
