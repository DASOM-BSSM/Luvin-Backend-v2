package com.luvin.auth.service;

import com.luvin.auth.dto.AuthLoginRequest;
import com.luvin.auth.dto.AuthLoginResponse;
import com.luvin.common.security.AuthenticatedUser;
import com.luvin.common.security.JwtTokenProvider;
import com.luvin.common.security.TokenBlacklistService;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final GoogleAuthorizationCodeExchanger googleAuthorizationCodeExchanger;

    @Transactional
    public AuthLoginResponse login(AuthLoginRequest request) {
        return loginWithGoogleUser(googleTokenVerifier.verify(request.idToken()));
    }

    /** 웹 OAuth 리디렉션(authorization code) 플로우 콜백에서 사용한다. */
    @Transactional
    public AuthLoginResponse loginWithAuthorizationCode(String code) {
        String idToken = googleAuthorizationCodeExchanger.exchangeForIdToken(code);
        return loginWithGoogleUser(googleTokenVerifier.verify(idToken));
    }

    private AuthLoginResponse loginWithGoogleUser(GoogleUserInfo googleUser) {
        User user = userRepository.findByGoogleId(googleUser.googleId()).orElse(null);
        boolean isNewUser = user == null;

        if (isNewUser) {
            user = userRepository.save(User.builder()
                    .googleId(googleUser.googleId())
                    .name(googleUser.name())
                    .email(googleUser.email())
                    .nickname(googleUser.name())
                    .age(20)
                    .mbti("INFP")
                    .datingStyle("신중형")
                    .build());
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
        if (token != null && !token.isBlank() && jwtTokenProvider.isValid(token)) {
            tokenBlacklistService.blacklist(token, jwtTokenProvider.getExpiration(token));
        }
    }
}
