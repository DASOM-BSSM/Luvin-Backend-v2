package com.luvin.common.security;

import com.luvin.common.exception.UnauthenticatedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthenticatedException("인증된 사용자 정보를 찾을 수 없습니다.");
        }
        return user;
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().userId();
    }
}
