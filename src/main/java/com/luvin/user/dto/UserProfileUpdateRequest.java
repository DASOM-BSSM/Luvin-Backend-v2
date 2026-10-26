package com.luvin.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class UserProfileUpdateRequest {

    private String nickname;
    /** MALE | FEMALE(대소문자 무관, 대문자로 저장). 러빈지옥은 나이와 성별을 함께 정해야 시작할 수 있다. */
    @Schema(allowableValues = {"MALE", "FEMALE"},
            description = "MALE | FEMALE. 대소문자는 무관하고 대문자로 저장된다. 그 외 값은 400")
    private String gender;
    /** 만 나이. 보내지 않으면(null) 기존 값을 유지한다. */
    private Integer age;
    private String job;
    private String bio;
    /** 감정일기 학습 동의. 보내지 않으면(null) 기존 값을 유지한다. */
    @Schema(description = "감정일기 학습 동의. 보내지 않으면 기존 값 유지")
    private Boolean learningEnabled;

    public UserProfileUpdateRequest() {
    }

    public String getNickname() { return nickname; }
    public String getGender() { return gender; }
    public Integer getAge() { return age; }
    public String getJob() { return job; }
    public String getBio() { return bio; }
    public Boolean getLearningEnabled() { return learningEnabled; }

    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setGender(String gender) { this.gender = gender; }
    public void setAge(Integer age) { this.age = age; }
    public void setJob(String job) { this.job = job; }
    public void setBio(String bio) { this.bio = bio; }
    public void setLearningEnabled(Boolean learningEnabled) { this.learningEnabled = learningEnabled; }
}