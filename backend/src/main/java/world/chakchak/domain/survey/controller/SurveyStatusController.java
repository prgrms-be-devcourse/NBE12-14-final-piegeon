package world.chakchak.domain.survey.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.survey.dto.SurveyStatusResponse;
import world.chakchak.domain.survey.service.SurveyStatusService;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@RestController
@RequestMapping("/api/rooms/{roomId}/surveys/status")
@RequiredArgsConstructor
@Tag(name = "설문 상태", description = "여행방 설문 진행 상태 조회 API")
public class SurveyStatusController {

    private final SurveyStatusService surveyStatusService;

    @Operation(
            summary = "설문 진행 상태 조회",
            description = "해당 여행방의 설문 진행 상태를 조회한다. " + "로그인한 사용자의 설문 제출 여부와 설문 진행 관련 상태 정보를 반환한다.")
    @GetMapping
    public ResponseEntity<ApiResponse<SurveyStatusResponse>> getStatus(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable Long roomId) {
        SurveyStatusResponse response = surveyStatusService.getStatus(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("SURVEY_STATUS_FETCHED", response));
    }
}
