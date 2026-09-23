package com.luvin.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.luvin.common.exception.InvalidGoogleTokenException;
import com.luvin.common.security.GoogleOAuthProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 웹 OAuth 리디렉션(authorization code) 플로우 전용. Google이 콜백으로 돌려준 code를
 * id_token으로 교환한다. 이후 검증은 기존 GoogleTokenVerifier(ID 토큰 플로우와 동일)에 맡긴다.
 */
@Component
public class GoogleAuthorizationCodeExchanger {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final RestClient restClient = RestClient.create();
    private final GoogleOAuthProperties googleOAuthProperties;

    public GoogleAuthorizationCodeExchanger(GoogleOAuthProperties googleOAuthProperties) {
        this.googleOAuthProperties = googleOAuthProperties;
    }

    public String exchangeForIdToken(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidGoogleTokenException("구글 authorization code가 없습니다.");
        }
        if (googleOAuthProperties.clientSecret() == null || googleOAuthProperties.clientSecret().isBlank()) {
            throw new InvalidGoogleTokenException("서버에 app.oauth.google.client-secret이 설정되지 않았습니다.");
        }
        if (googleOAuthProperties.redirectUri() == null || googleOAuthProperties.redirectUri().isBlank()) {
            throw new InvalidGoogleTokenException("서버에 app.oauth.google.redirect-uri가 설정되지 않았습니다.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", googleOAuthProperties.clientId());
        form.add("client_secret", googleOAuthProperties.clientSecret());
        form.add("redirect_uri", googleOAuthProperties.redirectUri());
        form.add("grant_type", "authorization_code");

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            throw new InvalidGoogleTokenException("구글 authorization code 교환에 실패했습니다.");
        }

        if (response == null || response.idToken() == null || response.idToken().isBlank()) {
            throw new InvalidGoogleTokenException("구글 토큰 응답에 id_token이 없습니다.");
        }
        return response.idToken();
    }

    private record TokenResponse(
            @JsonProperty("id_token") String idToken
    ) {
    }
}
