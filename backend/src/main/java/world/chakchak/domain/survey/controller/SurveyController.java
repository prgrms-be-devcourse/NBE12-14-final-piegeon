package world.chakchak.domain.survey.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import world.chakchak.domain.survey.dto.SurveyReportResponse;
import world.chakchak.domain.survey.service.SurveyReportService;
import world.chakchak.domain.survey.service.SurveyResponseService;
import world.chakchak.domain.survey.service.SurveySubmitService;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@RestController
@RequestMapping("/api/rooms/{roomId}/survey")
@RequiredArgsConstructor
@Tag(name = "여행 설문", description = "여행방 설문 응답 조회 및 제출 API")
public class SurveyController {

    private final SurveySubmitService surveySubmitService;
    private final SurveyResponseService surveyResponseService;
    private final SurveyReportService surveyReportService;

    @Operation(
            summary = "내 설문 응답 조회",
            description =
                    "해당 여행방에서 로그인한 사용자가 제출한 설문 응답을 조회한다. " + "아직 설문을 제출하지 않은 경우에도 조회 결과를 확인할 수 있다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SurveyMeResponse>> getMine(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable Long roomId) {
        SurveyMeResponse result = surveyResponseService.getMine(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("SURVEY_FETCHED", result));
    }

    @GetMapping("/report")
    public ResponseEntity<ApiResponse<SurveyReportResponse>> getReport(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable Long roomId) {
        SurveyReportResponse response = surveyReportService.getReport(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("SURVEY_REPORT_FETCHED", response));
    }

    @Operation(
            summary = "여행 설문 제출",
            description =
                    "해당 여행방의 여행 설문 응답을 제출한다. "
                            + "요청 본문에 설문 응답 정보를 전달하며, "
                            + "제출이 완료되면 생성된 설문 응답 정보를 반환한다.")
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
