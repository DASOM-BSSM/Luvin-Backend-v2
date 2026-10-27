package com.luvin.diary.controller;

import com.luvin.common.response.ApiErrorView;
import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.diary.dto.RoomQuestionDto;
import com.luvin.diary.service.RoomQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "감정일기 그룹 질문", description = "공유방(그룹)마다 AI가 만드는 하루 1개 질문과 멤버들의 답")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diary-rooms/{roomId}/questions")
public class RoomQuestionController {

    private static final String COMMON_ERRORS = """
            **공통 오류**: 404 `ROOM_NOT_FOUND`(없는 그룹) / 403 `ROOM_FORBIDDEN`(그룹 멤버가 아님)
            """;

    // 오류 본문 예시. 모두 CodedApiException이라 code가 채워진다.
    private static final String JSON = "application/json";
    private static final String ROOM_FORBIDDEN_EXAMPLE = """
            {"success": false, "data": null, "message": "공유방 멤버만 이용할 수 있습니다.", "code": "ROOM_FORBIDDEN"}""";
    private static final String ROOM_NOT_FOUND_EXAMPLE = """
            {"success": false, "data": null, "message": "공유방을 찾을 수 없습니다.", "code": "ROOM_NOT_FOUND"}""";
    private static final String QUESTION_NOT_FOUND_EXAMPLE = """
            {"success": false, "data": null, "message": "질문을 찾을 수 없습니다.", "code": "ROOM_QUESTION_NOT_FOUND"}""";
    private static final String ANSWER_INVALID_EXAMPLE = """
            {"success": false, "data": null, "message": "답은 1~200자로 입력해주세요.", "code": "ROOM_ANSWER_INVALID"}""";
    private static final String QUESTION_NOT_READY_EXAMPLE = """
            {"success": false, "data": null, "message": "아직 질문이 준비되지 않았습니다.", "code": "ROOM_QUESTION_NOT_READY"}""";

    private final RoomQuestionService roomQuestionService;

    @Operation(summary = "그룹 질문 생성", description = """
            그룹 멤버면 누구나 누를 수 있다. 그룹당 하루(KST 자정 기준) 1개.
            **비동기**: 바로 202와 함께 `status: generating`인 질문을 돌려준다. 1~3초 간격으로 GET current를 다시 요청해서
            `ready`(content·answers 채워짐)나 `failed`를 확인한다. `failed`면 이 API를 다시 눌러 재시도할 수 있다.
            AI에는 그룹 이름·멤버 수·최근 질문만 넘기고 일기 본문은 넘기지 않는다.

            **409 code**
            - `ROOM_QUESTION_ALREADY_EXISTS`: 오늘 질문이 이미 있다(생성 중 포함). data에 그 질문(QuestionView)이 담긴다
            """ + COMMON_ERRORS)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", useReturnTypeSchema = true,
            description = "생성 시작. data.status = generating")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
            description = "code: ROOM_QUESTION_ALREADY_EXISTS. 오늘 질문이 이미 있다(생성 중 포함). data에 그 질문",
            content = @Content(mediaType = JSON,
                    schema = @Schema(implementation = RoomQuestionDto.AlreadyExistsError.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "그룹 멤버가 아님. code: ROOM_FORBIDDEN",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = ROOM_FORBIDDEN_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 그룹. code: ROOM_NOT_FOUND",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = ROOM_NOT_FOUND_EXAMPLE)))
    @PostMapping
    public ResponseEntity<ApiResponse<RoomQuestionDto.QuestionView>> create(@PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(roomQuestionService.create(memberId, roomId)));
    }

    @Operation(summary = "지금 그룹 질문", description = """
            가장 최근 날짜의 질문. 질문이 한 번도 없으면 `data: null`.
            content·myAnswer·answers는 `status: ready`일 때만 채워진다. answers는 내 답을 포함한 멤버 답(작성순)이다.
            """ + COMMON_ERRORS)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "그룹 멤버가 아님. code: ROOM_FORBIDDEN",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = ROOM_FORBIDDEN_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 그룹. code: ROOM_NOT_FOUND",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = ROOM_NOT_FOUND_EXAMPLE)))
    @GetMapping("/current")
    public ApiResponse<RoomQuestionDto.QuestionView> getCurrent(@PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(roomQuestionService.getCurrent(memberId, roomId));
    }

    @Operation(summary = "그룹 질문 답하기", description = """
            body `{ content }` — 앞뒤 공백 제외 1~200자. 한 사람당 1개이고 다시 보내면 수정된다. 응답은 답이 반영된 질문.
            지난 질문(ready)에도 답할 수 있다.

            **400 code**: `ROOM_ANSWER_INVALID`(1~200자가 아님)
            **404 code**: `ROOM_QUESTION_NOT_FOUND`(이 그룹의 질문이 아님)
            **409 code**: `ROOM_QUESTION_NOT_READY`(generating·failed인 질문)
            """ + COMMON_ERRORS)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "code: ROOM_ANSWER_INVALID(앞뒤 공백 제외 1~200자가 아님, content 누락 포함)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = ANSWER_INVALID_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "그룹 멤버가 아님. code: ROOM_FORBIDDEN",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = ROOM_FORBIDDEN_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "code: ROOM_NOT_FOUND(없는 그룹) 또는 ROOM_QUESTION_NOT_FOUND(이 그룹의 질문이 아님)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = {
                            @ExampleObject(name = "ROOM_NOT_FOUND", value = ROOM_NOT_FOUND_EXAMPLE),
                            @ExampleObject(name = "ROOM_QUESTION_NOT_FOUND", value = QUESTION_NOT_FOUND_EXAMPLE)
                    }))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
            description = "code: ROOM_QUESTION_NOT_READY(generating·failed인 질문)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = QUESTION_NOT_READY_EXAMPLE)))
    @PostMapping("/{questionId}/answers")
    public ApiResponse<RoomQuestionDto.QuestionView> answer(@PathVariable Long roomId,
                                                            @PathVariable Long questionId,
                                                            @RequestBody RoomQuestionDto.AnswerRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(roomQuestionService.answer(memberId, roomId, questionId, request.content()));
    }

    @Operation(summary = "내 그룹 질문 답 삭제", description = """
            내 답을 지운다(답이 없어도 200). 그 답은 학습에서도 빠진다. 응답은 갱신된 질문.

            **404 code**: `ROOM_QUESTION_NOT_FOUND`
            """ + COMMON_ERRORS)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "그룹 멤버가 아님. code: ROOM_FORBIDDEN",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = ROOM_FORBIDDEN_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "code: ROOM_NOT_FOUND(없는 그룹) 또는 ROOM_QUESTION_NOT_FOUND(이 그룹의 질문이 아님)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = {
                            @ExampleObject(name = "ROOM_NOT_FOUND", value = ROOM_NOT_FOUND_EXAMPLE),
                            @ExampleObject(name = "ROOM_QUESTION_NOT_FOUND", value = QUESTION_NOT_FOUND_EXAMPLE)
                    }))
    @DeleteMapping("/{questionId}/answers/me")
    public ApiResponse<RoomQuestionDto.QuestionView> deleteMyAnswer(@PathVariable Long roomId,
                                                                    @PathVariable Long questionId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(roomQuestionService.deleteMyAnswer(memberId, roomId, questionId));
    }

    // 메서드 이름이 TokenController.getHistory와 같아서, 기존 토큰 API의 operationId(getHistory)가 _1로 밀리지 않도록 고정한다.
    @Operation(operationId = "getRoomQuestionHistory", summary = "지난 그룹 질문 목록", description = """
            최신순. page는 0부터, size 기본 20·최대 50. 답 수(answerCount) 포함 — 답 내용은 GET current처럼 상세가 필요하면
            따로 요청한다(지금은 current만 상세를 준다).
            """ + COMMON_ERRORS)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "그룹 멤버가 아님. code: ROOM_FORBIDDEN",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = ROOM_FORBIDDEN_EXAMPLE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 그룹. code: ROOM_NOT_FOUND",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                    examples = @ExampleObject(value = ROOM_NOT_FOUND_EXAMPLE)))
    @GetMapping
    public ApiResponse<List<RoomQuestionDto.HistoryItem>> getHistory(@PathVariable Long roomId,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(roomQuestionService.getHistory(memberId, roomId, page, size));
    }
}
