package com.luvin.simulation.dto;

public record AiCloneDetailResponse(
        Long cloneId,
        String cloneName,
        String speakingStyle,
        String datingStyle,
        String personaSummary
) {
}