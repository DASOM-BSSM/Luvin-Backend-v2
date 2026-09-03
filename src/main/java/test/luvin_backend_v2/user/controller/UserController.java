package com.luvin.user.controller;

import com.luvin.common.response.MessageResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.user.dto.UserProfileResponse;
import com.luvin.user.dto.UserProfileUpdateRequest;
import com.luvin.user.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse getMyProfile() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return userService.getProfile(memberId);
    }

    @PutMapping("/me")
    public MessageResponse updateMyProfile(@RequestBody UserProfileUpdateRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        userService.updateProfile(memberId, request);
        return new MessageResponse("수정 완료");
    }
}
