package test.luvin_backend_v2.auth.dto;

public record AuthLoginResponse(
        Long userId,
        String nickname,
        boolean isNewUser,
        String accessToken
) {
}
