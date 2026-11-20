package world.chakchak.domain.comment.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.comment.dto.CommentResponse;
import world.chakchak.domain.comment.entity.Comment;
import world.chakchak.domain.comment.repository.CommentRepository;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.room.entity.RoomMember;

/**
 * 코멘트 저장과 조회.
 *
 * <p>다른 도메인의 service를 주입받지 않는다. 안건·장소가 이 방의 것인지는 부르는 쪽(AgendaService, PlaceCandidateService)이 먼저
 * 확인하고 넘긴다. 그래야 그 service들이 이 service를 써도 서로를 주입받는 순환이 생기지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;

    /** 안건 코멘트를 오래된 순으로 돌려준다. (V-05) */
    public List<CommentResponse> getOnAgenda(Long agendaId) {
        return commentRepository.findAllByAgendaItemIdOrderByIdAsc(agendaId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    /** 안건 코멘트를 남긴다. (V-03 최종 확정 반대 이유, V-06) */
    @Transactional
    public Comment addOnAgenda(AgendaItem agenda, RoomMember member, String body) {
        return commentRepository.save(Comment.onAgenda(agenda, member, body));
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
        return commentRepository.save(Comment.onPlace(placeCandidate, member, body));
    }
}
