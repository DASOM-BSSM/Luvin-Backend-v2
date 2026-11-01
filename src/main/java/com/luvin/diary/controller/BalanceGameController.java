package com.luvin.diary.controller;

import com.luvin.common.response.ApiErrorView;
import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.diary.dto.BalanceGameDto;
import com.luvin.diary.service.BalanceGameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "감정일기 밸런스 게임", description = "감정일기 탭 홈의 오늘의 밸런스 게임. 모든 사용자가 같은 질문에 보기 2개 중 하나를 고른다")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/daily_questions")
public class BalanceGameController {

    private static final String JSON = "application/json";
    private static final String NOT_FOUND_EXAMPLE = """
            {"success": false, "data": null, "message": "밸런스 게임 질문을 찾을 수 없습니다.", "code": "DAILY_QUESTION_NOT_FOUND"}""";
    private static final String INVALID_OPTION_EXAMPLE = """
            {"success": false, "data": null, "message": "이 질문의 보기가 아닙니다.", "code": "DAILY_QUESTION_INVALID_OPTION"}""";
    private static final String ALREADY_ANSWERED_EXAMPLE = """
            {"success": false, "data": null, "message": "이미 답한 질문입니다.", "code": "DAILY_QUESTION_ALREADY_ANSWERED"}""";

    private final BalanceGameService balanceGameService;

    // operationId는 그룹 질문의 answer·getCurrent 등과 겹쳐 기존 operationId가 _1로 밀리지 않도록 고정한다.

    @Operation(operationId = "getTodayBalanceGame", summary = "오늘의 밸런스 게임", description = """
            하루(KST 자정 기준) 1개, 모든 사용자에게 같은 질문이다. 미리 준비해 둔 목록에서 날짜별로 고른다.
            answered·selectedOptionId는 요청한 사용자 기준이다.
            **results·totalCount는 내가 고른 뒤에만 채워진다** — 고르기 전에는 `results: []`, `totalCount: 0`.
            오늘 질문이 없으면 `data: null`(200)이다. 이때는 섹션을 숨긴다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    @GetMapping("/today")
    public ApiResponse<BalanceGameDto.View> getToday() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(balanceGameService.getToday(memberId));
    }

    @Operation(operationId = "answerBalanceGame", summary = "밸런스 게임 보기 고르기", description = """
            body `{ selectedOption }` — 고른 보기의 optionId. 한 사람당 질문 하나에 1번이고 **바꿀 수 없다**.
            응답은 결과(results·totalCount)가 채워진 질문이다(GET today와 같은 형식).

            **400 code**: `DAILY_QUESTION_INVALID_OPTION`(selectedOption 누락, 없는 optionId, 다른 질문의 보기)
            **404 code**: `DAILY_QUESTION_NOT_FOUND`(없는 questionId)
            **409 code**: `DAILY_QUESTION_ALREADY_ANSWERED`(이미 고름)
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "code: DAILY_QUESTION_INVALID_OPTION(selectedOption 누락, 없는 optionId, 다른 질문의 보기)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = INVALID_OPTION_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "code: DAILY_QUESTION_NOT_FOUND(없는 questionId)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
            description = "code: DAILY_QUESTION_ALREADY_ANSWERED(이미 고름. 답은 바꿀 수 없다)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = ALREADY_ANSWERED_EXAMPLE)))
    @PostMapping("/{questionId}/answer")
    public ApiResponse<BalanceGameDto.View> answer(@PathVariable Long questionId,
                                                   @RequestBody BalanceGameDto.AnswerRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(balanceGameService.answer(memberId, questionId, request.selectedOption()));
    }
}
