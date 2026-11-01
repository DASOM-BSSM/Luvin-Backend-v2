package com.luvin.diary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public class BalanceGameDto {

    /** 보기 고르기 요청. */
    @Schema(name = "BalanceGameAnswerRequest")
    public record AnswerRequest(
            @Schema(description = "고른 보기의 optionId. 없거나 이 질문의 보기가 아니면 400 DAILY_QUESTION_INVALID_OPTION")
            Long selectedOption
    ) {
    }

    @Schema(name = "BalanceGameOption")
    public record OptionItem(
            Long optionId,
            String content
    ) {
    }

    @Schema(name = "BalanceGameResult")
    public record ResultItem(
            Long optionId,
            String content,
            @Schema(description = "이 보기를 고른 사람 수")
            long voteCount,
            @Schema(description = "전체 중 비율(0~100, 소수 첫째 자리까지)", example = "62.5")
            double percentage
    ) {
    }

    /**
     * 오늘의 밸런스 게임. answered·selectedOptionId는 요청한 사용자 기준이다.
     * results·totalCount는 내가 고른 뒤에만 채운다(고르기 전에는 빈 목록·0).
     */
    @Schema(name = "BalanceGameView")
    public record View(
            Long questionId,
            @Schema(description = "질문 문장")
            String question,
            @Schema(description = "이 질문의 날짜(KST). 하루 1개, 모든 사용자 공통")
            LocalDate questionDate,
            @Schema(description = "보기 2개")
            List<OptionItem> options,
            @Schema(description = "내가 이미 골랐는지")
            boolean answered,
            @Schema(description = "내가 고른 optionId. 안 골랐으면 null", nullable = true)
            Long selectedOptionId,
            @Schema(description = "보기별 결과(options와 같은 순서). 고르기 전에는 빈 목록")
            List<ResultItem> results,
            @Schema(description = "전체 응답 수. 고르기 전에는 0")
            long totalCount
    ) {
    }
}
