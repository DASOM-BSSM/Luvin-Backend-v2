package com.luvin.push.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 기기 푸시 토큰(Expo 푸시 토큰, ExponentPushToken[...]). 한 사용자가 여러 기기를 쓸 수 있고, 토큰은 기기마다 하나라
 * token이 unique다 — 같은 기기에서 다른 계정으로 로그인하면 그 계정으로 옮긴다.
 */
@Entity
@Table(name = "push_tokens", indexes = @Index(name = "idx_push_tokens_member_id", columnList = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    private PushPlatform platform;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PushToken(Long memberId, String token, PushPlatform platform) {
        this.memberId = memberId;
        this.token = token;
        this.platform = platform;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** 같은 토큰을 다시 등록하면 덮어쓴다(다른 계정이 등록했으면 이 계정으로 옮긴다). */
    public void reassign(Long memberId, PushPlatform platform) {
        this.memberId = memberId;
        this.platform = platform;
        this.updatedAt = LocalDateTime.now();
    }
}
