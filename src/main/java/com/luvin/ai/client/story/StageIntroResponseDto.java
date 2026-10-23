package com.luvin.ai.client.story;

import java.util.List;
import java.util.UUID;

public record StageIntroResponseDto(List<CastIntro> cast) {

    public record CastIntro(UUID breadId, String role, String description) {
    }
}
