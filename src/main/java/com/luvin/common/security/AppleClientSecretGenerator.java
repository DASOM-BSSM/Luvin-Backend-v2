package com.luvin.common.security;

import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Apple REST API(POST /auth/token, POST /auth/revoke) 호출에 필요한 client_secret(ES256 JWT)을
 * 생성한다. Apple Developer 콘솔 "Certificates, Identifiers & Profiles &gt; Keys"에서 "Sign in with
 * Apple"을 활성화해 발급한 .p8 private key로 서명한다.
 *
 * Apple은 exp를 최대 15777000초(약 6개월)까지 허용하지만, 호출마다 새로 발급해도 비용이 거의
 * 없는 로컬 서명 연산이라 굳이 캐싱/장기 보관하지 않고 매번 5분짜리로 새로 만든다 — 캐시
 * 무효화를 고민할 필요가 없고, 혹시 로그 등으로 노출되더라도 노출 창이 훨씬 짧다.
 */
@Component
public class AppleClientSecretGenerator {

    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";
    private static final Duration CLIENT_SECRET_TTL = Duration.ofMinutes(5);

    private final AppleOAuthProperties properties;

    public AppleClientSecretGenerator(AppleOAuthProperties properties) {
        this.properties = properties;
    }

    public String generate() {
        requireConfig(properties.teamId(), "team-id");
        requireConfig(properties.keyId(), "key-id");
        requireConfig(properties.bundleId(), "bundle-id");
        requireConfig(properties.privateKey(), "private-key");

        PrivateKey privateKey = parsePrivateKey(properties.privateKey());
        Instant now = Instant.now();

        return Jwts.builder()
                .header().keyId(properties.keyId()).and()
                .issuer(properties.teamId())
                .subject(properties.bundleId())
                .audience().add(APPLE_AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(CLIENT_SECRET_TTL)))
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    /**
     * .p8은 PKCS8 PEM으로 인코딩된 EC(P-256) private key다 — 표준 JDK(KeyFactory)만으로 파싱 가능해
     * BouncyCastle 같은 별도 라이브러리가 필요 없다. 환경변수로 주입하면 실제 줄바꿈이 "\n" 두 글자
     * 문자열로 들어오는 경우가 흔해 먼저 진짜 줄바꿈으로 치환한다.
     */
    private PrivateKey parsePrivateKey(String pem) {
        try {
            String base64 = pem
                    .replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception e) {
            // 키 원문은 절대 예외 메시지/로그에 남기지 않는다.
            throw new IllegalStateException("app.oauth.apple.private-key(.p8)를 파싱할 수 없습니다.", e);
        }
    }

    private void requireConfig(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("서버에 app.oauth.apple." + key + "가 설정되지 않았습니다.");
        }
    }
}
