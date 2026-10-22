package com.luvin.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    /** 구글로 가입한 사용자만 값이 있다. 애플로 가입한 사용자는 null이다. */
    @Column(unique = true, length = 100)
    private String googleId;

    /** 애플로 가입/연동한 사용자만 값이 있다. 애플 identityToken의 sub claim(안정적인 고유 ID). */
    @Column(name = "apple_id", unique = true, length = 100)
    private String appleId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(length = 50)
    private String nickname;

    @Column(length = 10)
    private String gender;

    /**
     * 마이페이지에서 사용자가 입력한 나이. 입력 전이면 null이다 — 예전에 가입 시 넣던 20 고정값은
     * V8 마이그레이션으로 null 처리했다. 러빈지옥 시즌 배경과 audience(minor/adult)를 이 값으로 정한다.
     */
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

    /**
     * 본인의 최신 유효 설문 v2 결과(survey_results.id). surveyCompleted는 이 값의 존재 여부로 판단하고,
     * 답변 1개만 있거나 legacy 미분류 응답만 있는 경우와 구분한다.
     */
    @Column(name = "latest_survey_result_id")
    private UUID latestSurveyResultId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public User(String googleId, String appleId, String name, String email, String nickname, String gender,
                Integer age, String mbti, String datingStyle, String job, String bio,
                String personalityType) {
        this.googleId = googleId;
        this.appleId = appleId;
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

    public void updateProfile(String nickname, String gender, Integer age, String job, String bio) {
        if (nickname != null) this.nickname = nickname;
        if (gender != null) this.gender = gender;
        if (age != null) this.age = age;
        if (job != null) this.job = job;
        if (bio != null) this.bio = bio;
    }

    /**
     * 구글로 먼저 가입한 계정에 애플 로그인을 연동할 때 사용한다. 이미 다른 애플 계정이 연동돼
     * 있으면 덮어쓰지 않는다(다른 사람의 애플 계정으로 기존 계정을 탈취하는 것을 막는다).
     */
    public void linkAppleId(String appleId) {
        if (this.appleId == null) {
            this.appleId = appleId;
        }
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

    /** 설문 v2 제출 transaction 안에서만 호출한다. 과거 결과를 가리키도록 되돌리지 않는다(호출부 책임). */
    public void applySurveyResult(UUID surveyResultId, String personalityType) {
        this.latestSurveyResultId = surveyResultId;
        this.personalityType = personalityType;
    }

    public boolean hasCompletedSurveyV2() {
        return latestSurveyResultId != null;
    }
}