package com.luvin.user.dto;

import com.luvin.user.domain.User;

public class UserProfileResponse {

    private final Long memberId;
    private final String name;
    private final String nickname;
    private final String gender;
    private final Integer age;
    private final String job;
    private final String bio;
    private final String personalityType;
    private final boolean surveyCompleted;

    public UserProfileResponse(Long memberId, String name, String nickname, String gender,
                               Integer age, String job, String bio,
                               String personalityType, boolean surveyCompleted) {
        this.memberId = memberId;
        this.name = name;
        this.nickname = nickname;
        this.gender = gender;
        this.age = age;
        this.job = job;
        this.bio = bio;
        this.personalityType = personalityType;
        this.surveyCompleted = surveyCompleted;
    }

    public static UserProfileResponse from(User user, boolean surveyCompleted) {
        return new UserProfileResponse(
                user.getId(), user.getName(), user.getNickname(),
                user.getGender(), user.getAge(), user.getJob(), user.getBio(),
                user.getPersonalityType(), surveyCompleted);
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
}