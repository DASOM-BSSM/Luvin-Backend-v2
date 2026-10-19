package com.luvin.auth.repository;

import com.luvin.auth.domain.AppleRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppleRefreshTokenRepository extends JpaRepository<AppleRefreshToken, Long> {
    Optional<AppleRefreshToken> findByUserId(Long userId);

    /** 회원 탈퇴 시 user_id NOT NULL FK 제약 때문에 User 삭제 전에 먼저 지워야 한다. */
    void deleteByUserId(Long userId);
}
