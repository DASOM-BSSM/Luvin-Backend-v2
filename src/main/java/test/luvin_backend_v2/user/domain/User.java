package com.luvin.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String googleId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(length = 50)
    private String nickname;

    @Column(length = 10)
    private String gender;

    private Integer age;

    @Column(length = 10)
    private String mbti;

    @Column(length = 50)
    private String datingStyle;

    @Column(length = 100)
    private String job;

    @Column(length = 500)
    private String bio;

    @Column(name = "personality_type", length = 50)
    private String personalityType; // 빵 타입 (설문 결과로 계산되어 저장됨)

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public User(String googleId, String name, String email, String nickname, String gender,
                Integer age, String mbti, String datingStyle, String job, String bio,
                String personalityType) {
        this.googleId = googleId;
        this.name = name;
        this.email = email;
        this.nickname = nickname;
        this.gender = gender;
        this.age = age;
        this.mbti = mbti;
        this.datingStyle = datingStyle;
        this.job = job;
        this.bio = bio;
        this.personalityType = personalityType;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateProfile(String nickname, String job, String bio) {
        if (nickname != null) this.nickname = nickname;
        if (job != null) this.job = job;
        if (bio != null) this.bio = bio;
    }

    public void updateOAuthProfile(String nickname) {
        if (nickname != null) {
            this.nickname = nickname;
        }
    }

    // 설문 완료 후 빵 타입 계산 결과를 저장할 때 사용
    public void updatePersonalityType(String personalityType) {
        this.personalityType = personalityType;
    }
}