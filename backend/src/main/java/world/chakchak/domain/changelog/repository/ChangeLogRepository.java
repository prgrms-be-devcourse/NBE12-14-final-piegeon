package world.chakchak.domain.changelog.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import world.chakchak.domain.changelog.entity.ChangeLog;

public interface ChangeLogRepository extends Repository<ChangeLog, Long> {

    <S extends ChangeLog> S save(S changeLog);

    Optional<ChangeLog> findById(Long id);

    Optional<ChangeLog> findTopByRoom_IdOrderByIdDesc(Long roomId);

    Slice<ChangeLog> findAllByRoom_IdOrderByIdDesc(Long roomId, Pageable pageable);

    Slice<ChangeLog> findAllByRoom_IdAndIdLessThanOrderByIdDesc(
            Long roomId, Long cursor, Pageable pageable);

    @EntityGraph(attributePaths = {"room", "actor.user"})
    @Query(
            """
            select changeLog
            from ChangeLog changeLog
            where changeLog.room.id = :roomId
                and (:cursor is null or changeLog.id < :cursor)
            order by changeLog.id desc
            """)
    Slice<ChangeLog> findTimelinePage(
            @Param("roomId") Long roomId, @Param("cursor") Long cursor, Pageable pageable);

    @EntityGraph(attributePaths = {"actor.user"})
    List<ChangeLog> findTop201ByRoom_IdAndIdGreaterThanOrderByIdAsc(Long roomId, Long lastEventId);

    @EntityGraph(attributePaths = {"actor.user"})
    @Query(
            """
            select changeLog
            from ChangeLog changeLog
            where changeLog.room.id = :roomId
                and changeLog.createdAt > :since
                and (changeLog.actor is null or changeLog.actor.id <> :memberId)
            order by changeLog.id asc
            """)
    List<ChangeLog> findAfterSeenExcludingActor(
            @Param("roomId") Long roomId,
            @Param("memberId") Long memberId,
            @Param("since") LocalDateTime since);
}
