package com.luvin.auth.service;

import com.luvin.auth.domain.AppleRefreshToken;
import com.luvin.auth.dto.AppleLoginRequest;
import com.luvin.auth.dto.AuthLoginRequest;
import com.luvin.auth.dto.AuthLoginResponse;
import com.luvin.auth.repository.AppleRefreshTokenRepository;
import com.luvin.common.exception.InvalidAppleTokenException;
import com.luvin.common.security.AuthenticatedUser;
import com.luvin.common.security.JwtTokenProvider;
import com.luvin.common.security.TokenBlacklistService;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final GoogleAuthorizationCodeExchanger googleAuthorizationCodeExchanger;
    private final AppleTokenVerifier appleTokenVerifier;
    private final AppleOAuthClient appleOAuthClient;
    private final AppleRefreshTokenRepository appleRefreshTokenRepository;

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

    @Transactional
    public AuthLoginResponse loginWithApple(AppleLoginRequest request) {
        AppleUserInfo appleUser = appleTokenVerifier.verify(request.identityToken());

        User user = userRepository.findByAppleId(appleUser.appleId()).orElse(null);
        boolean isNewUser = false;

        if (user == null) {
            if (appleUser.email() == null || appleUser.email().isBlank()) {
                // 애플은 email scope를 요청한 앱에는 매 로그인마다 identityToken에 email을 넣어준다.
                // 비어 있다면 클라이언트가 email scope를 요청하지 않았을 가능성이 높다.
                throw new InvalidAppleTokenException(
                        "애플 토큰에 이메일이 없습니다. 클라이언트에서 email scope를 요청했는지 확인해주세요.");
            }

            // 같은 이메일로 이미 구글로 가입한 계정이 있으면 새 계정을 만들지 않고 연동한다
            // (users.email이 UNIQUE라 새로 만들면 제약 위반으로 500이 난다).
            user = appleUser.emailVerified()
                    ? userRepository.findByEmail(appleUser.email()).orElse(null)
                    : null;

            if (user != null) {
                user.linkAppleId(appleUser.appleId());
            } else {
                // fullName은 최초 인증 때만 오고 name/nickname은 NOT NULL이라 기본값이 필요하다.
                String displayName = request.fullName() != null && !request.fullName().isBlank()
                        ? request.fullName()
                        : "애플 사용자";
                user = userRepository.save(User.builder()
                        .appleId(appleUser.appleId())
                        .name(displayName)
                        .email(appleUser.email())
                        .nickname(displayName)
                        .age(20)
                        .mbti("INFP")
                        .datingStyle("신중형")
                        .build());
                isNewUser = true;
            }
        }

        if (request.authorizationCode() != null && !request.authorizationCode().isBlank()) {
            persistAppleRefreshToken(user, request.authorizationCode());
        }

        return issueLoginResponse(user, isNewUser);
    }

    /**
     * authorization code로 refresh token을 받아 저장해 둔다 — 회원 탈퇴 시 재인증 없이 바로 애플
     * 토큰을 해지하기 위한 용도다. identityToken 검증(로그인의 본 목적)과는 독립적인 best-effort
     * 단계라 실패해도 로그인 자체는 막지 않는다. config 누락(IllegalStateException)까지 포함해
     * 의도적으로 넓게 잡는다 — 이 메서드의 실패가 로그인 전체를 절대 막아서는 안 되기 때문이다.
     * 실패하면 이 회원은 다음 로그인 때 새 code를 보내면 그때 다시 저장을 시도한다.
     */
    private void persistAppleRefreshToken(User user, String authorizationCode) {
        try {
            AppleTokenResponse tokenResponse = appleOAuthClient.exchangeAuthorizationCode(authorizationCode);
            AppleRefreshToken existing = appleRefreshTokenRepository.findByUserId(user.getId()).orElse(null);
            if (existing != null) {
                existing.updateRefreshToken(tokenResponse.refreshToken());
            } else {
                appleRefreshTokenRepository.save(new AppleRefreshToken(user, tokenResponse.refreshToken()));
            }
        } catch (RuntimeException e) {
            log.warn("Apple refresh token 저장 실패(탈퇴 시 자동 해지 불가 상태로 남음): userId={}, cause={}",
                    user.getId(), e.getMessage());
        }
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

        return issueLoginResponse(user, isNewUser);
    }

    private AuthLoginResponse issueLoginResponse(User user, boolean isNewUser) {
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
