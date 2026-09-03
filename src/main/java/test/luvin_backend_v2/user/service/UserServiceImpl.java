package com.luvin.user.service;

import com.luvin.common.exception.UserNotFoundException;
import com.luvin.user.domain.User;
import com.luvin.user.dto.UserProfileResponse;
import com.luvin.user.dto.UserProfileUpdateRequest;
import com.luvin.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long memberId) {
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> new UserNotFoundException(memberId));
        boolean surveyCompleted = surveyAnswerRepository.existsByUserId(memberId);
        return UserProfileResponse.from(user, surveyCompleted);
    }

    @Override
    @Transactional
    public void updateProfile(Long memberId, UserProfileUpdateRequest request) {
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> new UserNotFoundException(memberId));
        user.updateProfile(request.getNickname(), request.getJob(), request.getBio());
        // JPA 변경 감지(dirty checking)로 트랜잭션 종료 시 자동 반영 -> save() 호출 불필요
    }
}
