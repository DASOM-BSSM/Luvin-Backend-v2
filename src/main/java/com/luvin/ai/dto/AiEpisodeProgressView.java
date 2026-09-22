package com.luvin.ai.dto;

import java.util.UUID;

public record AiEpisodeProgressView(
        Integer episodeNumber,
        UUID jobId,
        String jobStatus,
        UUID versionId,
        String errorCode
) {
}
