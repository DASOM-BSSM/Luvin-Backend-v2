package com.luvin.user.dto;

public class UserProfileUpdateRequest {

    private String nickname;
    private String gender;
    private String job;
    private String bio;

    public UserProfileUpdateRequest() {
    }

    public String getNickname() { return nickname; }
    public String getGender() { return gender; }
    public String getJob() { return job; }
    public String getBio() { return bio; }

    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setGender(String gender) { this.gender = gender; }
    public void setJob(String job) { this.job = job; }
    public void setBio(String bio) { this.bio = bio; }
}