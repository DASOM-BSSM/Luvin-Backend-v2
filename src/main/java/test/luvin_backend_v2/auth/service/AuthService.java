package test.luvin_backend_v2.auth.service;

import com.luvin_backend_v2.auth.dto.AuthLoginRequest;
import com.luvin_backend_v2.auth.dto.AuthLoginResponse;
import com.luvin_backend_v2.common.exception.BusinessException;
import com.luvin_backend_v2.common.exception.ErrorCode;
import com.luvin_backend_v2.common.security.AuthenticatedUser;
import com.luvin_backend_v2.common.security.JwtTokenProvider;
import com.luvin_backend_v2.common.security.TokenBlacklistService;
import com.luvin_backend_v2.user.domain.User;
import com.luvin_backend_v2.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public AuthLoginResponse login(AuthLoginRequest request) {
        boolean isNewUser;

        if (request.googleToken().isBlank() || request.googleToken().startsWith("invalid")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findByGoogleId(request.googleId()).orElse(null);
        if (user == null) {
            user = userRepository.save(User.builder()
                    .googleId(request.googleId())
                    .name(request.nickname())
                    .email(request.email())
                    .nickname(request.nickname())
                    .age(20)
                    .mbti("INFP")
                    .datingStyle("신중형")
                    .build());
            isNewUser = true;
        } else {
            isNewUser = false;
        }

        if (!request.nickname().equals(user.getNickname())) {
            user.updateOAuthProfile(request.nickname());
        }

        String accessToken = jwtTokenProvider.createAccessToken(
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getNickname())
        );

        return new AuthLoginResponse(
                user.getId(),
                user.getNickname(),
                isNewUser,
                accessToken
        );
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            tokenBlacklistService.blacklist(token);
        }
    }
}