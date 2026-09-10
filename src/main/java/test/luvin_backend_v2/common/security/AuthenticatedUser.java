package test.luvin_backend_v2.common.security;

public record AuthenticatedUser(
        Long userId,
        String email,
        String nickname
) {
}
