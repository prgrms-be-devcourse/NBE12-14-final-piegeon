package world.chakchak.domain.survey.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.survey.dto.SubmitSurveyRequest;
import world.chakchak.domain.survey.dto.SubmitSurveyResponse;
import world.chakchak.domain.survey.service.SurveyResponseService;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@RestController
@RequestMapping("/api/rooms/{roomId}/survey")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyResponseService surveyResponseService;

    @PostMapping
    public ResponseEntity<ApiResponse<SubmitSurveyResponse>> submit(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long roomId,
            @RequestBody SubmitSurveyRequest request) {
        SurveyResponseService.SubmitSurveyResult result =
                surveyResponseService.submit(roomId, loginUser.userId(), request);
        HttpStatus status = result.late() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .body(ApiResponse.success("SURVEY_SUBMITTED", result.data()));
    }
}
