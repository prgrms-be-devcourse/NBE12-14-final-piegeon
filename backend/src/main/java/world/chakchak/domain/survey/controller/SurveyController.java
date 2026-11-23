package world.chakchak.domain.survey.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.survey.dto.SubmitSurveyRequest;
import world.chakchak.domain.survey.dto.SubmitSurveyResponse;
import world.chakchak.domain.survey.dto.SurveyMeResponse;
import world.chakchak.domain.survey.service.SurveyResponseService;
import world.chakchak.domain.survey.service.SurveySubmitService;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@RestController
@RequestMapping("/api/rooms/{roomId}/survey")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveySubmitService surveySubmitService;
    private final SurveyResponseService surveyResponseService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SurveyMeResponse>> getMine(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable Long roomId) {
        SurveyMeResponse result = surveyResponseService.getMine(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("SURVEY_FETCHED", result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubmitSurveyResponse>> submit(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long roomId,
            @Valid @RequestBody SubmitSurveyRequest request) {
        SubmitSurveyResponse response =
                surveySubmitService.submit(roomId, loginUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("SURVEY_SUBMITTED", response));
    }
}
