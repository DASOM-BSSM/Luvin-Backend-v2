package com.luvin.user.service;

import com.luvin.analysis.repository.AnalysisResultRepository;
import com.luvin.common.exception.UserNotFoundException;
import com.luvin.simulation.repository.AiCloneRepository;
import com.luvin.user.domain.User;
import com.luvin.user.dto.UserProfileResponse;
import com.luvin.user.dto.UserProfileUpdateRequest;
import com.luvin.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AiCloneRepository aiCloneRepository;
    private final AnalysisResultRepository analysisResultRepository;

    public UserServiceImpl(UserRepository userRepository,
                            AiCloneRepository aiCloneRepository,
                            AnalysisResultRepository analysisResultRepository) {
        this.userRepository = userRepository;
        this.aiCloneRepository = aiCloneRepository;
        this.analysisResultRepository = analysisResultRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long memberId) {
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> new UserNotFoundException(memberId));
        // surveyCompleted는 본인의 유효한 설문 v2 결과가 연결돼 있을 때만 true다 — 기존 답변 1개만
        // 있거나 legacy 미분류 응답만 있는 경우는 false로 취급해 새 설문을 안내한다.
        return UserProfileResponse.from(user, user.hasCompletedSurveyV2());
    }

    @Override
    @Transactional
    public void updateProfile(Long memberId, UserProfileUpdateRequest request) {
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> new UserNotFoundException(memberId));
        user.updateProfile(request.getNickname(), request.getGender(), request.getJob(), request.getBio());
        // JPA 변경 감지(dirty checking)로 트랜잭션 종료 시 자동 반영 -> save() 호출 불필요
    }

    @Override
    @Transactional
    public void deleteAccount(Long memberId) {
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> new UserNotFoundException(memberId));
        // AiClone.user_id는 NOT NULL FK, AnalysisResult.user_id는 nullable FK라 User 삭제 전에
        // 먼저 지워야 한다(그 외 도메인은 user_id가 FK 없는 plain 컬럼이라 User 삭제에 영향받지 않는
        // 기존 프로젝트 전반의 의도된 decoupling 패턴을 따른다).
        aiCloneRepository.deleteByUserId(memberId);
        analysisResultRepository.deleteByUserId(memberId);
        userRepository.delete(user);
    }
}
