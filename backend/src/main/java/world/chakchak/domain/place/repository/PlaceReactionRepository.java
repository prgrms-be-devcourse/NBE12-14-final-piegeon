package world.chakchak.domain.place.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import world.chakchak.domain.place.entity.PlaceReaction;
import world.chakchak.domain.place.entity.PlaceReactionType;
import world.chakchak.domain.room.entity.Room;

public interface PlaceReactionRepository extends JpaRepository<PlaceReaction, Long> {

    /**
     * 방의 모든 후보에 달린 반응 중 한 종류(WANT)만. (P-03)
     *
     * <p>후보마다 따로 세지 않고 방 전체를 한 번에 가져와, service가 후보별로 묶어 agreeCount와 agreedBy를 만든다. 동의한 사람 이름을 쓰므로
     * 멤버와 그 사용자까지 JOIN으로 같이 가져온다(N+1 방지).
     */
    @EntityGraph(attributePaths = {"member", "member.user"})
    List<PlaceReaction> findAllByPlaceCandidate_RoomAndReaction(
            Room room, PlaceReactionType reaction);

    @Modifying
    @Query("delete from PlaceReaction reaction where reaction.member.id = :memberId")
    int deleteAllByMemberId(@Param("memberId") Long memberId);

    // TODO 후보 + 멤버로 반응 조회 - 반응 바꾸기·취소용 (P-04, P-05)
    // TODO 후보의 반응 전부 삭제 - 후보 삭제 전에 먼저 지운다 (P-07)
}
