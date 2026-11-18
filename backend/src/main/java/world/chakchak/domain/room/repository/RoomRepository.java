package world.chakchak.domain.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.room.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByInviteCode(String inviteCode);
}
