package world.chakchak.domain.survey.controller;

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
public class SurveyStatusController {

    private final SurveyStatusService surveyStatusService;

    @GetMapping
    public ResponseEntity<ApiResponse<SurveyStatusResponse>> getStatus(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable Long roomId) {
        SurveyStatusResponse response = surveyStatusService.getStatus(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("SURVEY_STATUS_FETCHED", response));
    }
}
