package com.luvin.minigame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class MinigamePlayRequest {

    @NotBlank
    @Pattern(regexp = "^(성공|실패)$", message = "success 값은 '성공' 또는 '실패'여야 합니다.")
    private String success;

    protected MinigamePlayRequest() {
    }

    public MinigamePlayRequest(String success) {
        this.success = success;
    }

    public String getSuccess() { return success; }

    public boolean isSuccess() { return "성공".equals(success); }
}
