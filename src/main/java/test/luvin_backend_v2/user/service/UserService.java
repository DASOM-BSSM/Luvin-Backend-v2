package com.luvin.user.service;

import com.luvin.user.dto.UserProfileResponse;
import com.luvin.user.dto.UserProfileUpdateRequest;

public interface UserService {
    UserProfileResponse getProfile(Long memberId);
    void updateProfile(Long memberId, UserProfileUpdateRequest request);
}
