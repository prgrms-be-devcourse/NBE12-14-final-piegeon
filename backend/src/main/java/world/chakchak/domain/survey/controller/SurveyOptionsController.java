package world.chakchak.domain.survey.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "설문 옵션", description = "여행 설문에서 사용하는 선택 옵션 조회 API")
public class SurveyOptionsController {

    private final SurveyOptionsService surveyOptionsService;

    @Operation(
            summary = "설문 옵션 조회",
            description = "여행 설문 작성에 필요한 선택 옵션 목록을 조회한다. " + "설문 항목별 선택 가능한 옵션 정보를 반환한다.")
    @GetMapping
    public ResponseEntity<ApiResponse<SurveyOptionsResponse>> getOptions() {
        return ResponseEntity.ok(
                ApiResponse.success("SURVEY_OPTIONS_FETCHED", surveyOptionsService.getOptions()));
    }
}
