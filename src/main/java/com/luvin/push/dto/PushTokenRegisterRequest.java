package com.luvin.push.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/push-tokens body.
 *
 * @param token    Expo 푸시 토큰(ExponentPushToken[...] 또는 ExpoPushToken[...])
 * @param platform ios | android
 */
public record PushTokenRegisterRequest(
        @NotBlank @Size(max = 255)
        @Pattern(regexp = PushTokenFormat.EXPO_TOKEN, message = "Expo 푸시 토큰(ExponentPushToken[...])이 아닙니다.")
        @Schema(example = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]") String token,
        @NotBlank @Pattern(regexp = "(?i)ios|android", message = "platform은 ios 또는 android입니다.")
        @Schema(allowableValues = {"ios", "android"}) String platform
) {
}
