package com.luvin.auth.service;

import com.luvin.common.exception.InvalidAppleTokenException;
import com.luvin.common.security.AppleOAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 애플 로그인은 구글과 달리 백엔드가 애플 리소스 서버에 사용자 정보를 조회하지 않는다.
 * 클라이언트가 보낸 identityToken(JWT)의 서명을 애플 공개키(JWKS)로 직접 검증해서 사용자를 식별한다.
 */
@Component
public class AppleTokenVerifier {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWKS_URL = APPLE_ISSUER + "/auth/keys";
    /** 잘못된 kid로 들어오는 요청이 반복돼도 애플 키 서버를 난타하지 않도록 최소 재조회 간격을 둔다. */
    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofMinutes(1);

    private final RestClient restClient = RestClient.create();
    private final AppleOAuthProperties appleOAuthProperties;

    private final Map<String, Key> keyCache = new ConcurrentHashMap<>();
    private volatile Instant lastRefreshedAt = Instant.EPOCH;

    public AppleTokenVerifier(AppleOAuthProperties appleOAuthProperties) {
        this.appleOAuthProperties = appleOAuthProperties;
    }

    public AppleUserInfo verify(String identityToken) {
        if (identityToken == null || identityToken.isBlank()) {
            throw new InvalidAppleTokenException("애플 identityToken이 없습니다.");
        }

        List<String> allowedClientIds = appleOAuthProperties.clientIds();
        if (allowedClientIds == null || allowedClientIds.isEmpty()) {
            throw new InvalidAppleTokenException("서버에 app.oauth.apple.client-ids(애플 클라이언트 ID 목록)가 설정되지 않았습니다.");
        }

        Claims claims;
        try {
            // 서명·만료(exp)·발급처(iss)는 parser가 검증한다. aud는 iOS 번들 ID와 웹 Service ID 등
            // 여러 값을 허용해야 해서 requireAudience 대신 아래에서 직접 확인한다.
            claims = Jwts.parser()
                    .keyLocator(new AppleKeyLocator())
                    .requireIssuer(APPLE_ISSUER)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();
        } catch (InvalidAppleTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidAppleTokenException("유효하지 않거나 만료된 애플 토큰입니다.");
        }

        Set<String> audience = claims.getAudience();
        if (audience == null || audience.stream().noneMatch(allowedClientIds::contains)) {
            throw new InvalidAppleTokenException("이 서버용으로 발급되지 않은 애플 토큰입니다.");
        }

        String appleId = claims.getSubject();
        if (appleId == null || appleId.isBlank()) {
            throw new InvalidAppleTokenException("애플 토큰에 사용자 식별자(sub)가 없습니다.");
        }

        // 애플은 email_verified를 boolean 또는 "true" 문자열로 보낸다(케이스가 섞여 있다).
        Object emailVerifiedClaim = claims.get("email_verified");
        boolean emailVerified = emailVerifiedClaim instanceof Boolean verified
                ? verified
                : "true".equalsIgnoreCase(String.valueOf(emailVerifiedClaim));

        return new AppleUserInfo(appleId, claims.get("email", String.class), emailVerified);
    }

    private final class AppleKeyLocator extends LocatorAdapter<Key> {
        @Override
        protected Key locate(JwsHeader header) {
            String kid = header.getKeyId();
            if (kid == null || kid.isBlank()) {
                throw new InvalidAppleTokenException("애플 토큰 헤더에 kid가 없습니다.");
            }
            Key cached = keyCache.get(kid);
            if (cached != null) {
                return cached;
            }
            // 애플이 서명 키를 교체하면 캐시에 없는 kid가 올라온다 — 이때만 JWKS를 다시 가져온다.
            refreshKeys();
            Key refreshed = keyCache.get(kid);
            if (refreshed == null) {
                throw new InvalidAppleTokenException("일치하는 애플 공개키를 찾을 수 없습니다.");
            }
            return refreshed;
        }
    }

    private synchronized void refreshKeys() {
        if (Instant.now().isBefore(lastRefreshedAt.plus(MIN_REFRESH_INTERVAL))) {
            return;
        }

        String jwksJson;
        try {
            jwksJson = restClient.get().uri(APPLE_JWKS_URL).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new InvalidAppleTokenException("애플 공개키 목록을 가져오지 못했습니다.");
        }
        if (jwksJson == null || jwksJson.isBlank()) {
            throw new InvalidAppleTokenException("애플 공개키 응답이 비어 있습니다.");
        }

        JwkSet jwkSet = Jwks.setParser().build().parse(jwksJson);
        for (Jwk<?> jwk : jwkSet) {
            if (jwk.getId() != null) {
                keyCache.put(jwk.getId(), jwk.toKey());
            }
        }
        lastRefreshedAt = Instant.now();
    }
}
