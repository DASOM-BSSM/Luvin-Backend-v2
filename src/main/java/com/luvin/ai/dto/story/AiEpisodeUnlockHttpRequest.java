package com.luvin.ai.dto.story;

import com.luvin.token.domain.TokenKind;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * POST /episodes/{order}/unlock body. body나 payWith를 생략하면 SALT(이전 앱 호환).
 *
 * @param payWith SALT(소금빵, 대기가 끝났을 때만) | BREAD(식빵, 대기와 상관없이 바로)
 */
public record AiEpisodeUnlockHttpRequest(
        @Schema(allowableValues = {"SALT", "BREAD"}, defaultValue = "SALT") TokenKind payWith
) {

    public TokenKind payWithOrDefault() {
        return payWith == null ? TokenKind.SALT : payWith;
    }
}
