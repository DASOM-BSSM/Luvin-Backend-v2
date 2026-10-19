package com.luvin.auth.service;

import com.luvin.common.exception.AppleTokenExchangeException;
import com.luvin.common.security.AppleClientSecretGenerator;
import com.luvin.common.security.AppleOAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Apple REST API(POST /auth/token, POST /auth/revoke) 호출 클라이언트. identityToken 검증
 * ({@link AppleTokenVerifier})과는 별개로, authorization code를 refresh token으로 교환하고
 * (회원가입/로그인 시) 탈퇴 시 그 refresh token을 해지하는 데 쓰인다.
 */
@Component
public class AppleOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(AppleOAuthClient.class);
    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";

    private final RestClient restClient = RestClient.create();
    private final AppleOAuthProperties properties;
    private final AppleClientSecretGenerator clientSecretGenerator;

    public AppleOAuthClient(AppleOAuthProperties properties, AppleClientSecretGenerator clientSecretGenerator) {
        this.properties = properties;
        this.clientSecretGenerator = clientSecretGenerator;
    }

    /** authorization code → refresh token 교환. code는 5분 안에, 한 번만 쓸 수 있다. */
    public AppleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.bundleId());
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");

        try {
            AppleTokenResponse response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(AppleTokenResponse.class);
            if (response == null || response.refreshToken() == null || response.refreshToken().isBlank()) {
                throw new AppleTokenExchangeException("애플 토큰 응답에 refresh_token이 없습니다.");
            }
            return response;
        } catch (RestClientException e) {
            // 4xx(RestClientResponseException)면 body에 {"error": "invalid_grant"} 등이 들어있다 —
            // 만료/이미 사용된 code가 가장 흔한 원인이다. 토큰 원문은 절대 로그에 남기지 않는다.
            String detail = extractErrorBody(e);
            log.warn("Apple authorization code exchange failed: {}", detail);
            throw new AppleTokenExchangeException("애플 authorization code 교환에 실패했습니다: " + detail);
        }
    }

    /**
     * refresh token 해지. 애플은 해지에 성공하거나 이미 무효화된 토큰이어도 본문 없이 200을
     * 반환한다 — 200 응답만으로 "실제로 해지됐다"를 확신할 수 없다는 뜻이다. 그래도 애플이 명시적
     * 오류(4xx/5xx)로 응답한 경우는 구분해 예외로 알린다.
     */
    public void revoke(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.bundleId());
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");

        try {
            restClient.post()
                    .uri(REVOKE_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            String detail = extractErrorBody(e);
            log.warn("Apple refresh token revoke failed: {}", detail);
            throw new AppleTokenExchangeException("애플 refresh token 해지에 실패했습니다: " + detail);
        }
    }

    private String extractErrorBody(RestClientException e) {
        if (e instanceof RestClientResponseException responseException) {
            return responseException.getStatusCode() + " " + responseException.getResponseBodyAsString();
        }
        return e.getMessage();
    }
}
