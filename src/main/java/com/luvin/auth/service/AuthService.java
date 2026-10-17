package com.luvin.auth.service;

import com.luvin.auth.dto.AppleLoginRequest;
import com.luvin.auth.dto.AuthLoginRequest;
import com.luvin.auth.dto.AuthLoginResponse;
import com.luvin.common.exception.InvalidAppleTokenException;
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
    private final AppleTokenVerifier appleTokenVerifier;

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

        return issueLoginResponse(user, isNewUser);
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
