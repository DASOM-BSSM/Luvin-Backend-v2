package com.luvin.auth.domain;

import com.luvin.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 애플 refresh token은 만료가 없어, 발급받은 뒤 계속 보관하다가 회원 탈퇴 시 해지(revoke)
 * 요청에 사용한다(App Store 심사 기준 — Sign in with Apple 사용 앱은 탈퇴 시 토큰을 해지해야
 * 한다). 회원 1명당 최대 1개(1:1) — 재로그인으로 새 code가 들어오면 기존 값을 갱신한다.
 *
 * 민감 정보이므로 운영 반입 전 DB 컬럼 레벨 암호화(AttributeConverter + KMS 관리 키, 또는 최소
 * RDS 저장소 암호화)를 적용하는 걸 권장한다 — 이번 구현에서는 평문 컬럼으로 두되 반드시
 * 후속 작업으로 남겨야 한다 (요구사항 설명 참고).
 */
@Entity
@Table(name = "apple_refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppleRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "refresh_token", nullable = false, length = 1000)
    private String refreshToken;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AppleRefreshToken(User user, String refreshToken) {
        this.user = user;
        this.refreshToken = refreshToken;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
        this.updatedAt = LocalDateTime.now();
    }
}
