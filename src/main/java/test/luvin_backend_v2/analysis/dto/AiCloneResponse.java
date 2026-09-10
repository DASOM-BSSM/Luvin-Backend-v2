package test.luvin_backend_v2.analysis.dto;

public record AiCloneResponse(
        Long cloneId,
        String cloneName,
        String speakingStyle,
        String datingStyle
) {
}
