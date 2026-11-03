package com.luvin.auth.service;

import com.luvin.auth.dto.AppleLoginRequest;
import com.luvin.auth.dto.AuthLoginRequest;
import com.luvin.auth.dto.AuthLoginResponse;
import com.luvin.auth.repository.AppleRefreshTokenRepository;
import com.luvin.common.exception.InvalidAppleTokenException;
import com.luvin.common.security.JwtTokenProvider;
import com.luvin.common.security.TokenBlacklistService;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Google·Apple 로그인의 계정 연결: 같은 인증된 이메일이면 어느 쪽으로 먼저 가입했든 한 계정이 된다. */
class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
    private final AppleTokenVerifier appleTokenVerifier = mock(AppleTokenVerifier.class);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, jwtTokenProvider, mock(TokenBlacklistService.class),
                googleTokenVerifier, mock(GoogleAuthorizationCodeExchanger.class), appleTokenVerifier,
                mock(AppleOAuthClient.class), mock(AppleRefreshTokenRepository.class));
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("token");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });
    }

    @Test
    void 애플로_먼저_가입한_이메일로_구글_로그인하면_그_계정에_연동한다() {
        User appleUser = user(null, "apple-sub", "me@gmail.com");
        when(googleTokenVerifier.verify("id-token")).thenReturn(new GoogleUserInfo("google-sub", "me@gmail.com", "나"));
        when(userRepository.findByGoogleId("google-sub")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("me@gmail.com")).thenReturn(Optional.of(appleUser));

        AuthLoginResponse response = authService.login(new AuthLoginRequest("id-token"));

        assertEquals(1L, response.userId());
        assertFalse(response.isNewUser());
        assertEquals("google-sub", appleUser.getGoogleId());
        verify(userRepository, never()).save(any());
    }

    @Test
    void 구글_계정이_이미_연동된_계정의_구글_id는_덮어쓰지_않는다() {
        User existing = user("other-google", null, "me@gmail.com");
        when(googleTokenVerifier.verify("id-token")).thenReturn(new GoogleUserInfo("google-sub", "me@gmail.com", "나"));
        when(userRepository.findByGoogleId("google-sub")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("me@gmail.com")).thenReturn(Optional.of(existing));

        authService.login(new AuthLoginRequest("id-token"));

        assertEquals("other-google", existing.getGoogleId());
    }

    @Test
    void 처음_보는_구글_계정은_새로_가입한다() {
        when(googleTokenVerifier.verify("id-token")).thenReturn(new GoogleUserInfo("google-sub", "new@gmail.com", "나"));
        when(userRepository.findByGoogleId("google-sub")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.empty());

        assertTrue(authService.login(new AuthLoginRequest("id-token")).isNewUser());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void 구글로_먼저_가입한_이메일로_애플_로그인하면_그_계정에_연동한다() {
        User googleUser = user("google-sub", null, "me@gmail.com");
        when(appleTokenVerifier.verify("identity")).thenReturn(new AppleUserInfo("apple-sub", "me@gmail.com", true));
        when(userRepository.findByAppleId("apple-sub")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("me@gmail.com")).thenReturn(Optional.of(googleUser));

        AuthLoginResponse response = authService.loginWithApple(new AppleLoginRequest("identity", null, null));

        assertEquals(1L, response.userId());
        assertFalse(response.isNewUser());
        assertEquals("apple-sub", googleUser.getAppleId());
    }

    @Test
    void 애플_토큰에_이메일이_없어도_sub로_가입한다() {
        when(appleTokenVerifier.verify("identity")).thenReturn(new AppleUserInfo("apple-sub", null, false));
        when(userRepository.findByAppleId("apple-sub")).thenReturn(Optional.empty());

        AuthLoginResponse response = authService.loginWithApple(new AppleLoginRequest("identity", "홍길동", null));

        assertTrue(response.isNewUser());
        assertEquals("홍길동", response.nickname());
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(u ->
                "apple-sub".equals(u.getAppleId()) && u.getEmail() == null));
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void 이미_가입한_애플_사용자는_빈_fullName으로_이름을_덮어쓰지_않는다() {
        User existing = user(null, "apple-sub", null);
        when(appleTokenVerifier.verify("identity")).thenReturn(new AppleUserInfo("apple-sub", null, false));
        when(userRepository.findByAppleId("apple-sub")).thenReturn(Optional.of(existing));

        AuthLoginResponse response = authService.loginWithApple(new AppleLoginRequest("identity", null, null));

        assertFalse(response.isNewUser());
        assertEquals("빵", existing.getNickname());
    }

    @Test
    void 인증되지_않은_애플_이메일로는_다른_계정에_연동하지_않는다() {
        User googleUser = user("google-sub", null, "me@gmail.com");
        when(appleTokenVerifier.verify("identity")).thenReturn(new AppleUserInfo("apple-sub", "me@gmail.com", false));
        when(userRepository.findByAppleId("apple-sub")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("me@gmail.com")).thenReturn(Optional.of(googleUser));

        assertThrows(InvalidAppleTokenException.class,
                () -> authService.loginWithApple(new AppleLoginRequest("identity", null, null)));
        assertNull(googleUser.getAppleId());
    }

    private static User user(String googleId, String appleId, String email) {
        User user = User.builder().googleId(googleId).appleId(appleId).name("빵").email(email).nickname("빵").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
