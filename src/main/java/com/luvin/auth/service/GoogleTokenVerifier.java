package com.luvin.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.luvin.common.exception.InvalidGoogleTokenException;
import com.luvin.common.security.GoogleOAuthProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class GoogleTokenVerifier {

    private static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final RestClient restClient = RestClient.create();
    private final GoogleOAuthProperties googleOAuthProperties;

    public GoogleTokenVerifier(GoogleOAuthProperties googleOAuthProperties) {
        this.googleOAuthProperties = googleOAuthProperties;
    }

    public GoogleUserInfo verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidGoogleTokenException("구글 ID 토큰이 없습니다.");
        }

        TokenInfoResponse response;
        try {
            response = restClient.get()
                    .uri(TOKEN_INFO_URL + "?id_token={idToken}", idToken)
                    .retrieve()
                    .body(TokenInfoResponse.class);
        } catch (RestClientException e) {
            throw new InvalidGoogleTokenException("유효하지 않거나 만료된 구글 토큰입니다.");
        }

        if (response == null || response.sub() == null || response.sub().isBlank()) {
            throw new InvalidGoogleTokenException("구글 토큰 검증 응답이 올바르지 않습니다.");
        }

        List<String> allowedClientIds = googleOAuthProperties.clientIds();
        if (allowedClientIds == null || allowedClientIds.isEmpty()) {
            throw new InvalidGoogleTokenException("서버에 app.oauth.google.client-ids(구글 클라이언트 ID 목록)가 설정되지 않았습니다.");
        }
        if (!allowedClientIds.contains(response.aud())) {
            throw new InvalidGoogleTokenException("이 서버용으로 발급되지 않은 구글 토큰입니다.");
        }

        if (!"true".equalsIgnoreCase(response.emailVerified())) {
            throw new InvalidGoogleTokenException("이메일 인증이 완료되지 않은 구글 계정입니다.");
        }

        return new GoogleUserInfo(response.sub(), response.email(), response.name());
    }


    private record TokenInfoResponse(
            String sub,
            String email,
            @JsonProperty("email_verified") String emailVerified,
            String aud,
            String name
    ) {
    }
}
