package world.chakchak.domain.comment.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import world.chakchak.domain.comment.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 안건 코멘트를 오래된 순으로. (V-05)
     *
     * <p>응답에 작성자 이름(user.name)이 들어가므로 멤버와 사용자를 함께 읽어 코멘트마다 따로 조회하지 않게 한다.
     */
    @EntityGraph(attributePaths = {"member", "member.user"})
    List<Comment> findAllByAgendaItemIdOrderByIdAsc(Long agendaId);

    /** 장소 후보 코멘트를 오래된 순으로. 작성자도 함께 읽는다. (P-08) */
    @EntityGraph(attributePaths = {"member", "member.user"})
    List<Comment> findAllByPlaceCandidateIdOrderByIdAsc(Long placeCandidateId);

    /** 장소 후보의 코멘트 수 (P-10 commentCount) */
    long countByPlaceCandidateId(Long placeCandidateId);

    /**
     * 장소 후보를 지우기 전에 그 코멘트를 먼저 지운다. (P-07)
     *
     * <p>DELETE 쿼리 한 번으로 지운다. 목록을 먼저 읽고 하나씩 지우면 트랜잭션 처음 시점의 데이터를 읽어서, 그 사이 방금 저장된 코멘트가 빠질 수 있다. 쿼리로
     * 지우면 실행하는 순간 DB에 있는 코멘트를 모두 지운다.
     */
    @Modifying
    @Query("delete from Comment comment where comment.placeCandidate.id = :placeCandidateId")
    int deleteByPlaceCandidateId(@Param("placeCandidateId") Long placeCandidateId);
}
