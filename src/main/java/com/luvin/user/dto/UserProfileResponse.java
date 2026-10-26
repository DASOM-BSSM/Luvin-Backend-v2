package com.luvin.user.dto;

import com.luvin.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public class UserProfileResponse {

    private final Long memberId;
    private final String name;
    private final String nickname;
    @Schema(allowableValues = {"MALE", "FEMALE"}, description = "MALE | FEMALE. 입력 전이면 null")
    private final String gender;
    private final Integer age;
    private final String job;
    private final String bio;
    private final String personalityType;
    private final boolean surveyCompleted;
    private final UUID latestSurveyResultId;
    @Schema(description = "감정일기 학습 동의. 끄면 일기·댓글·그룹 답을 내 빵 학습에 쓰지 않는다")
    private final boolean learningEnabled;

    public UserProfileResponse(Long memberId, String name, String nickname, String gender,
                               Integer age, String job, String bio,
                               String personalityType, boolean surveyCompleted, UUID latestSurveyResultId,
                               boolean learningEnabled) {
        this.memberId = memberId;
        this.name = name;
        this.nickname = nickname;
        this.gender = gender;
        this.age = age;
        this.job = job;
        this.bio = bio;
        this.personalityType = personalityType;
        this.surveyCompleted = surveyCompleted;
        this.latestSurveyResultId = latestSurveyResultId;
        this.learningEnabled = learningEnabled;
    }

    public static UserProfileResponse from(User user, boolean surveyCompleted) {
        return new UserProfileResponse(
                user.getId(), user.getName(), user.getNickname(),
                user.getGender(), user.getAge(), user.getJob(), user.getBio(),
                surveyCompleted ? user.getPersonalityType() : null,
                surveyCompleted, user.getLatestSurveyResultId(), user.isLearningEnabled());
    }

    public Long getMemberId() { return memberId; }
    public String getName() { return name; }
    public String getNickname() { return nickname; }
    public String getGender() { return gender; }
    public Integer getAge() { return age; }
    public String getJob() { return job; }
    public String getBio() { return bio; }
    public String getPersonalityType() { return personalityType; }
    public boolean isSurveyCompleted() { return surveyCompleted; }
    public UUID getLatestSurveyResultId() { return latestSurveyResultId; }
    public boolean isLearningEnabled() { return learningEnabled; }
}