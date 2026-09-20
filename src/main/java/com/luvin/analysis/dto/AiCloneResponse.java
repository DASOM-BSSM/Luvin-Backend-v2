package com.luvin.analysis.dto;

public record AiCloneResponse(
        Long cloneId,
        String cloneName,
        String speakingStyle,
        String datingStyle
) {
}
