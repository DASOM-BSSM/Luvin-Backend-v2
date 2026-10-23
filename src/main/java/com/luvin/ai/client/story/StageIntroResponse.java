package com.luvin.ai.client.story;

import java.util.List;

/** 5-1 응답. role 10자 이내, description 25자 이내(me는 null). */
public record StageIntroResponse(List<Cast> cast) {

    public record Cast(String breadId, String role, String description) {
    }
}
