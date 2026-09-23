package com.luvin.auth.controller;

import com.luvin.auth.dto.AuthLoginRequest;
import com.luvin.auth.dto.AuthLoginResponse;
import com.luvin.auth.service.AuthService;
import com.luvin.common.exception.InvalidGoogleTokenException;
import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.GoogleOAuthProperties;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final GoogleOAuthProperties googleOAuthProperties;

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("ok");
    }

    /**
     * 웹 OAuth 리디렉션 플로우 시작점. 브라우저로 직접 열면 구글 동의 화면으로 리디렉트된다
     * (수동으로 authorize URL을 만들 필요 없이 로컬 테스트용).
     */
    @GetMapping("/google/login")
    public ResponseEntity<Void> googleLoginRedirect() {
        URI target = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleOAuthProperties.clientId())
                .queryParam("redirect_uri", googleOAuthProperties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }

    @PostMapping("/google")
    public ApiResponse<AuthLoginResponse> googleLogin(@Valid @RequestBody AuthLoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * 웹 OAuth 리디렉션(authorization code) 플로우 콜백. Google Cloud Console의 "승인된 리디렉션 URI"에
     * 이 경로(app.oauth.google.redirect-uri와 동일한 값)를 등록해야 한다.
     */
    @GetMapping("/google/callback")
    public ApiResponse<AuthLoginResponse> googleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error) {
        if (error != null) {
            throw new InvalidGoogleTokenException("구글 로그인이 취소되었습니다: " + error);
        }
        return ApiResponse.ok(authService.loginWithAuthorizationCode(code));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : null;
        authService.logout(token);
        return ApiResponse.okMessage("로그아웃이 완료되었습니다.");
    }
}