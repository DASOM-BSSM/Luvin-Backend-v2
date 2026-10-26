package com.luvin.user.service;

import com.luvin.analysis.repository.AnalysisResultRepository;
import com.luvin.auth.repository.AppleRefreshTokenRepository;
import com.luvin.auth.service.AppleOAuthClient;
import com.luvin.common.exception.BusinessException;
import com.luvin.common.exception.ErrorCode;
import com.luvin.common.exception.UserNotFoundException;
import com.luvin.diary.service.DiaryRoomService;
import com.luvin.learning.service.LearningSignalService;
import com.luvin.simulation.repository.AiCloneRepository;
import com.luvin.user.domain.User;
import com.luvin.user.dto.UserProfileResponse;
import com.luvin.user.dto.UserProfileUpdateRequest;
import com.luvin.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final int MIN_AGE = 1;
    private static final int MAX_AGE = 120;
    /** 러빈지옥 출연진 성별(친구는 같게, 나머지는 반대로)을 정하려면 둘 중 하나여야 한다. "선택 안 함"은 없다. */
    private static final java.util.Set<String> GENDERS = java.util.Set.of("MALE", "FEMALE");

    private final UserRepository userRepository;
    private final AiCloneRepository aiCloneRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AppleRefreshTokenRepository appleRefreshTokenRepository;
    private final AppleOAuthClient appleOAuthClient;
    private final DiaryRoomService diaryRoomService;
    private final LearningSignalService learningSignalService;
    private final TransactionTemplate transactionTemplate;

    public UserServiceImpl(UserRepository userRepository,
                            AiCloneRepository aiCloneRepository,
                            AnalysisResultRepository analysisResultRepository,
                            AppleRefreshTokenRepository appleRefreshTokenRepository,
                            AppleOAuthClient appleOAuthClient,
                            DiaryRoomService diaryRoomService,
                            LearningSignalService learningSignalService,
                            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.aiCloneRepository = aiCloneRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.appleRefreshTokenRepository = appleRefreshTokenRepository;
        this.appleOAuthClient = appleOAuthClient;
        this.diaryRoomService = diaryRoomService;
        this.learningSignalService = learningSignalService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
        if (request.getAge() != null && (request.getAge() < MIN_AGE || request.getAge() > MAX_AGE)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        user.updateProfile(request.getNickname(), normalizeGender(request.getGender()), request.getAge(),
                request.getJob(), request.getBio());
        if (request.getLearningEnabled() != null) {
            user.changeLearningEnabled(request.getLearningEnabled());
        }
        // JPA 변경 감지(dirty checking)로 트랜잭션 종료 시 자동 반영 -> save() 호출 불필요
    }

    /** 성별은 MALE/FEMALE만 받고(대소문자 무관) 대문자로 저장한다. 보내지 않으면(null) 기존 값을 유지한다. */
    private static String normalizeGender(String gender) {
        if (gender == null) {
            return null;
        }
        String normalized = gender.trim().toUpperCase();
        if (!GENDERS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    /**
     * 애플 토큰 해지(외부 HTTP 호출)는 일부러 @Transactional 밖에서 먼저 시도한다 — DB 커넥션을
     * 쥔 채로 애플 서버 응답(특히 타임아웃)을 기다리면 트래픽이 몰릴 때 커넥션 풀을 불필요하게
     * 오래 점유한다. 또한 revoke가 네트워크 오류 등으로 실패해도 탈퇴 자체는 막지 않는다 —
     * 사용자의 계정 삭제 요청이 애플이라는 제3자 API의 그 순간 가용성에 좌우되어서는 안 된다고
     * 판단했다(App Store 요구사항은 "해지를 시도한다"이지 "반드시 성공해야만 탈퇴 가능하다"가
     * 아니다). 실패는 ERROR로 남겨 운영에서 필요하면 별도로 재시도/추적할 수 있게 한다.
     *
     * 이 기능 도입 전에 가입한 애플 로그인 회원은 저장된 refresh token이 없어 해지 자체를
     * 시도하지 못한다 — findByUserId가 비어 있으면 그냥 건너뛴다. 꼭 해지가 필요하다면 탈퇴
     * 확인 화면에서 클라이언트가 애플 재인증을 한 번 더 받아 authorization code를 함께 보내도록
     * 하는 방법이 있다(대안으로만 제시, 이번 구현 범위에는 포함하지 않음).
     */
    @Override
    public void deleteAccount(Long memberId) {
        appleRefreshTokenRepository.findByUserId(memberId).ifPresent(token -> {
            try {
                appleOAuthClient.revoke(token.getRefreshToken());
            } catch (RuntimeException e) {
                log.error("Apple refresh token revoke 실패, 탈퇴는 계속 진행한다: userId={}, cause={}",
                        memberId, e.getMessage());
            }
        });

        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findById(memberId)
                    .orElseThrow(() -> new UserNotFoundException(memberId));
            // AiClone.user_id는 NOT NULL FK, AnalysisResult.user_id는 nullable FK,
            // AppleRefreshToken.user_id와 DiaryRoomMember.user_id도 FK라 User 삭제 전에 먼저 지워야 한다(그 외
            // 도메인은 user_id가 FK 없는 plain 컬럼이라 User 삭제에 영향받지 않는 기존 프로젝트
            // 전반의 의도된 decoupling 패턴을 따른다).
            diaryRoomService.removeUserFromAllRooms(memberId);
            // 학습 신호·말투 요약·성격 보정은 FK가 없어 users를 지워도 남으므로 직접 지운다(내가 쓴 글에서 나온 개인 데이터).
            learningSignalService.removeAllForUser(memberId);
            aiCloneRepository.deleteByUserId(memberId);
            analysisResultRepository.deleteByUserId(memberId);
            appleRefreshTokenRepository.deleteByUserId(memberId);
            userRepository.delete(user);
        });
    }
}
