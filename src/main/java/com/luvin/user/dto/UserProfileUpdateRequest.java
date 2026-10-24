package com.luvin.user.dto;

public class UserProfileUpdateRequest {

    private String nickname;
    /** MALE | FEMALE(대소문자 무관). 러빈지옥은 나이와 성별을 함께 정해야 시작할 수 있다. */
    private String gender;
    /** 만 나이. 보내지 않으면(null) 기존 값을 유지한다. */
    private Integer age;
    private String job;
    private String bio;

    public UserProfileUpdateRequest() {
    }

    public String getNickname() { return nickname; }
    public String getGender() { return gender; }
    public Integer getAge() { return age; }
    public String getJob() { return job; }
    public String getBio() { return bio; }

    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setGender(String gender) { this.gender = gender; }
    public void setAge(Integer age) { this.age = age; }
    public void setJob(String job) { this.job = job; }
    public void setBio(String bio) { this.bio = bio; }
}