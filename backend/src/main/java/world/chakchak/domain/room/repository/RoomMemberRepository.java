package world.chakchak.domain.room.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.room.entity.RoomMember;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    Optional<RoomMember> findByRoom_IdAndUser_Id(Long roomId, Long userId);

    List<RoomMember> findAllByRoom_IdAndLeftAtIsNull(Long roomId);

    List<RoomMember> findAllByUser_IdAndLeftAtIsNull(Long userId);

    long countByRoom_IdAndLeftAtIsNull(Long roomId);
}
