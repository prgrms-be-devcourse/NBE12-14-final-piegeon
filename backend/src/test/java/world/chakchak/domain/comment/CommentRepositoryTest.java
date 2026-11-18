package world.chakchak.domain.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.comment.entity.Comment;
import world.chakchak.domain.comment.repository.CommentRepository;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

/** comment 테이블 저장·조회 테스트 (V18) */
@IntegrationTest
@Transactional
class CommentRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2027, 6, 5, 12, 0);

    @Autowired private CommentRepository commentRepository;
    @Autowired private AgendaItemRepository agendaItemRepository;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private Clock clock;

    private Room room;
    private RoomMember member;
    private AgendaItem agenda;
    private PlaceCandidate place;

    @BeforeEach
    void setUp() {
        // invite_code는 UNIQUE라 테스트마다 다른 값을 쓴다
        String inviteCode = UUID.randomUUID().toString().substring(0, 8);
        room =
                roomRepository.save(
                        Room.create(
                                "여름 여행",
                                "부산",
                                LocalDate.of(2027, 7, 1),
                                LocalDate.of(2027, 7, 3),
                                inviteCode,
                                RoomStatus.PLANNING,
                                clock));
        member = saveMember();
        agenda = saveAgenda(1);
        place = savePlace("place-1");
    }

    private RoomMember saveMember() {
        User user =
                userRepository.save(
                        User.create(UUID.randomUUID() + "@example.com", "지은", "hashed-password"));
        return roomMemberRepository.save(RoomMember.create(room, user, clock));
    }

    private AgendaItem saveAgenda(int round) {
        return agendaItemRepository.saveAndFlush(
                AgendaItem.create(
                        room, AgendaSubject.BUDGET, round, "예산을 정해주세요", 3, NOW, DEADLINE));
    }

    private PlaceCandidate savePlace(String googlePlaceId) {
        return placeCandidateRepository.saveAndFlush(
                PlaceCandidate.create(
                        room, googlePlaceId, "광장시장", "market", null, null, null, member));
    }

    private Comment save(Comment comment) {
        return commentRepository.saveAndFlush(comment);
    }

    private void insertRaw(Long agendaId, Long placeCandidateId) {
        jdbcTemplate.update(
                "INSERT INTO comment (plan_room_member_id, agenda_id, place_candidate_id, body,"
                        + " created_at, modified_at) VALUES (?, ?, ?, ?, ?, ?)",
                member.getId(),
                agendaId,
                placeCandidateId,
                "본문",
                NOW,
                NOW);
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("안건 코멘트를 저장한 뒤 조회하면 안건, 작성자, 본문이 유지되고 장소는 비어 있다")
            void savesAgendaComment() {
                Comment saved = save(Comment.onAgenda(agenda, member, "150은 좀 부담돼요"));

                Comment found = commentRepository.findById(saved.getId()).orElseThrow();

                assertThat(found.getAgendaItem().getId()).isEqualTo(agenda.getId());
                assertThat(found.getPlaceCandidate()).isNull();
                assertThat(found.getMember().getId()).isEqualTo(member.getId());
                assertThat(found.getBody()).isEqualTo("150은 좀 부담돼요");
                assertThat(found.getCreatedAt()).isNotNull();
            }

            @Test
            @DisplayName("장소 코멘트를 저장한 뒤 조회하면 장소가 유지되고 안건은 비어 있다")
            void savesPlaceComment() {
                Comment saved = save(Comment.onPlace(place, member, "줄이 길대요"));

                Comment found = commentRepository.findById(saved.getId()).orElseThrow();

                assertThat(found.getPlaceCandidate().getId()).isEqualTo(place.getId());
                assertThat(found.getAgendaItem()).isNull();
            }
        }

        // 엔티티 팩토리가 둘 중 하나만 채우므로, DB 제약은 SQL로 직접 넣어 확인한다
        @Nested
        class Exception {

            @Test
            @DisplayName("안건과 장소가 둘 다 있는 코멘트는 저장할 수 없다")
            void rejectsBothTargets() {
                assertThatThrownBy(() -> insertRaw(agenda.getId(), place.getId()))
                        .isInstanceOf(DataAccessException.class)
                        .hasMessageContaining("ck_comment_target");
            }

            @Test
            @DisplayName("안건과 장소가 둘 다 없는 코멘트는 저장할 수 없다")
            void rejectsNoTarget() {
                assertThatThrownBy(() -> insertRaw(null, null))
                        .isInstanceOf(DataAccessException.class)
                        .hasMessageContaining("ck_comment_target");
            }
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("안건 코멘트 목록은 그 안건의 코멘트만 오래된 순으로 돌려준다")
            void findsAgendaCommentsOldestFirst() {
                Comment first = save(Comment.onAgenda(agenda, member, "첫 번째"));
                Comment second = save(Comment.onAgenda(agenda, saveMember(), "두 번째"));
                save(Comment.onAgenda(saveAgenda(2), member, "다른 안건"));
                save(Comment.onPlace(place, member, "장소 코멘트"));

                assertThat(commentRepository.findAllByAgendaItemIdOrderByIdAsc(agenda.getId()))
                        .extracting(Comment::getId)
                        .containsExactly(first.getId(), second.getId());
            }

            @Test
            @DisplayName("장소 코멘트 목록은 그 장소의 코멘트만 오래된 순으로 돌려준다")
            void findsPlaceCommentsOldestFirst() {
                Comment first = save(Comment.onPlace(place, member, "첫 번째"));
                Comment second = save(Comment.onPlace(place, member, "두 번째"));
                save(Comment.onPlace(savePlace("place-2"), member, "다른 장소"));
                save(Comment.onAgenda(agenda, member, "안건 코멘트"));

                assertThat(commentRepository.findAllByPlaceCandidateIdOrderByIdAsc(place.getId()))
                        .extracting(Comment::getId)
                        .containsExactly(first.getId(), second.getId());
            }

            // 영속성 컨텍스트를 비워야 저장할 때 들고 있던 객체가 아니라 조회 결과로 로딩 여부를 확인할 수 있다
            @Test
            @DisplayName("코멘트 목록은 작성자 멤버와 사용자를 함께 읽어 온다")
            void fetchesWriterWithComments() {
                save(Comment.onAgenda(agenda, member, "안건 코멘트"));
                save(Comment.onPlace(place, member, "장소 코멘트"));
                entityManager.clear();
                PersistenceUnitUtil util =
                        entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

                List<Comment> comments =
                        List.of(
                                commentRepository
                                        .findAllByAgendaItemIdOrderByIdAsc(agenda.getId())
                                        .getFirst(),
                                commentRepository
                                        .findAllByPlaceCandidateIdOrderByIdAsc(place.getId())
                                        .getFirst());

                assertThat(comments)
                        .allSatisfy(comment -> {
                            assertThat(util.isLoaded(comment, "member")).isTrue();
                            assertThat(util.isLoaded(comment.getMember(), "user")).isTrue();
                            assertThat(comment.getMember().getUser().getName()).isEqualTo("지은");
                        });
            }

            @Test
            @DisplayName("장소 코멘트 수는 그 장소의 코멘트만 센다")
            void countsPlaceComments() {
                save(Comment.onPlace(place, member, "첫 번째"));
                save(Comment.onPlace(place, member, "두 번째"));
                save(Comment.onPlace(savePlace("place-2"), member, "다른 장소"));

                assertThat(commentRepository.countByPlaceCandidateId(place.getId())).isEqualTo(2);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("코멘트가 없는 안건의 목록은 비어 있다")
            void returnsEmptyWhenNoComment() {
                save(Comment.onAgenda(saveAgenda(2), member, "다른 안건"));

                assertThat(commentRepository.findAllByAgendaItemIdOrderByIdAsc(agenda.getId()))
                        .isEmpty();
            }
        }
    }

    @Nested
    class Delete {

        @Nested
        class Success {

            @Test
            @DisplayName("장소 기준으로 지우면 그 장소의 코멘트만 지워지고 다른 코멘트는 남는다")
            void deletesPlaceComments() {
                save(Comment.onPlace(place, member, "지워질 코멘트"));
                Comment otherPlace = save(Comment.onPlace(savePlace("place-2"), member, "다른 장소"));
                Comment agendaComment = save(Comment.onAgenda(agenda, member, "안건 코멘트"));

                commentRepository.deleteByPlaceCandidateId(place.getId());
                commentRepository.flush();

                assertThat(commentRepository.countByPlaceCandidateId(place.getId())).isZero();
                assertThat(commentRepository.findById(otherPlace.getId())).isPresent();
                assertThat(commentRepository.findById(agendaComment.getId())).isPresent();
            }
        }
    }
}
