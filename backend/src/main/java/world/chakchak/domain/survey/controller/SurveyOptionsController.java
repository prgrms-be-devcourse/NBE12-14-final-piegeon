package world.chakchak.domain.survey.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.survey.dto.SurveyOptionsResponse;
import world.chakchak.domain.survey.service.SurveyOptionsService;
import world.chakchak.global.response.ApiResponse;

@RestController
@RequestMapping("/api/survey-options")
@RequiredArgsConstructor
public class SurveyOptionsController {

    private final SurveyOptionsService surveyOptionsService;

    @GetMapping
    public ResponseEntity<ApiResponse<SurveyOptionsResponse>> getOptions() {
        return ResponseEntity.ok(
                ApiResponse.success("SURVEY_OPTIONS_FETCHED", surveyOptionsService.getOptions()));
    }
}
