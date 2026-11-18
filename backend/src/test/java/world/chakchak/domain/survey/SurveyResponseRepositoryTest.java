package world.chakchak.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

@IntegrationTest
class SurveyResponseRepositoryTest {

    @Autowired private SurveyResponseRepository surveyResponseRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    @Nested
    class Create {

        @Test
        @DisplayName("PLANNING 응답은 설문 항목을 null로 저장하고 연관 멤버와 복수 선택을 조회한다")
        void savesAndFindsPlanningResponse() {
            RoomMember member = saveRoomMember();
            SurveyResponse saved =
                    surveyResponseRepository.saveAndFlush(
                            SurveyResponse.createForPlanning(
                                    member,
                                    List.of(ActivityCode.FOOD, ActivityCode.NATURE),
                                    "긴 도보 일정",
                                    clock));

            SurveyResponse found = surveyResponseRepository.findById(saved.getId()).orElseThrow();

            assertThat(found.getRoomMember().getId()).isEqualTo(member.getId());
            assertThat(found.getBudget()).isNull();
            assertThat(found.getStayType()).isNull();
            assertThat(found.getPace()).isNull();
            assertThat(found.getActPrefs()).containsExactly(ActivityCode.FOOD, ActivityCode.NATURE);
            assertThat(found.getAvoidNote()).isEqualTo("긴 도보 일정");
            assertThat(found.getSubmittedAt()).isNotNull();
        }

        @Test
        @DisplayName("같은 멤버에게 두 번째 설문 응답을 저장할 수 없다")
        void rejectsDuplicateResponseForMember() {
            RoomMember member = saveRoomMember();
            surveyResponseRepository.saveAndFlush(createResponse(member));

            assertThatThrownBy(() -> surveyResponseRepository.saveAndFlush(createResponse(member)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class Read {

        @Test
        @DisplayName("RoomMember ID와 여러 RoomMember ID 및 방 ID로 응답을 조회한다")
        void findsResponsesByMemberIdsAndRoomId() {
            Room room = saveRoom();
            RoomMember firstMember = saveRoomMember(room);
            RoomMember secondMember = saveRoomMember(room);
            SurveyResponse first =
                    surveyResponseRepository.saveAndFlush(createResponse(firstMember));
            SurveyResponse second =
                    surveyResponseRepository.saveAndFlush(createResponse(secondMember));

            assertThat(surveyResponseRepository.findByRoomMember_Id(firstMember.getId()))
                    .map(SurveyResponse::getId)
                    .contains(first.getId());
            assertThat(
                            surveyResponseRepository.findAllByRoomMember_IdIn(
                                    List.of(firstMember.getId(), secondMember.getId())))
                    .extracting(SurveyResponse::getId)
                    .containsExactlyInAnyOrder(first.getId(), second.getId());
            assertThat(surveyResponseRepository.findAllByRoomMember_Room_Id(room.getId()))
                    .extracting(SurveyResponse::getId)
                    .containsExactlyInAnyOrder(first.getId(), second.getId());
        }

        @Test
        @DisplayName("탈퇴한 멤버를 제외하고 방의 활동 중인 멤버 응답만 조회한다")
        void findsResponsesOnlyForActiveRoomMembers() {
            Room room = saveRoom();
            RoomMember activeMember = saveRoomMember(room);
            RoomMember departedMember = saveRoomMember(room);
            SurveyResponse activeResponse =
                    surveyResponseRepository.saveAndFlush(createResponse(activeMember));
            surveyResponseRepository.saveAndFlush(createResponse(departedMember));

            jdbcTemplate.update(
                    "UPDATE plan_room_member SET left_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                    departedMember.getId());

            assertThat(
                            surveyResponseRepository
                                    .findAllByRoomMember_Room_IdAndRoomMember_LeftAtIsNull(
                                            room.getId()))
                    .extracting(SurveyResponse::getId)
                    .containsExactly(activeResponse.getId());
        }
    }

    private SurveyResponse createResponse(RoomMember member) {
        return SurveyResponse.createForSurveying(
                member,
                100_000L,
                StayType.HOTEL,
                Pace.NORMAL,
                List.of(ActivityCode.CULTURE),
                "긴 도보 일정",
                clock);
    }

    private RoomMember saveRoomMember() {
        return saveRoomMember(saveRoom());
    }

    private RoomMember saveRoomMember(Room room) {
        User user =
                userRepository.saveAndFlush(
                        User.create(UUID.randomUUID() + "@example.com", "테스터", "hashed-password"));
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }

    private Room saveRoom() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return roomRepository.saveAndFlush(
                Room.create(
                        "여행 " + suffix,
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        suffix,
                        RoomStatus.SURVEYING,
                        clock));
    }
}
