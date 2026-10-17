package com.luvin.ai.dto;

import java.util.UUID;

public record AiRerollView(
        UUID jobId,
        String jobStatus,
        String errorCode
) {
}
