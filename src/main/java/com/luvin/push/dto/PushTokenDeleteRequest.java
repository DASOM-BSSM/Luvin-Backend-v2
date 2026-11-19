package com.luvin.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DELETE /api/push-tokens body. 내 토큰이 아니거나 없으면 아무것도 하지 않는다. */
public record PushTokenDeleteRequest(@NotBlank @Size(max = 255) String token) {
}
