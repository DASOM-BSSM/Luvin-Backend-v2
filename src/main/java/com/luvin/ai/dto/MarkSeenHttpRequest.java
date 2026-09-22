package com.luvin.ai.dto;

import jakarta.validation.constraints.Min;

public record MarkSeenHttpRequest(
        @Min(0) int sequence
) {
}
