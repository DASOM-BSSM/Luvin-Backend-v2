package com.luvin.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppleClientSecretGeneratorTest {

    @Test
    void generatesVerifiableEs256Jwt() throws Exception {
        KeyPair keyPair = generateEcKeyPair();
        String pem = toPem(keyPair);
        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
                new AppleOAuthProperties(List.of("com.example.app"), "TEAM123456", "KEY1234567",
                        "com.example.app", pem));

        String jwt = generator.generate();

        Claims claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(jwt)
                .getPayload();

        assertEquals("TEAM123456", claims.getIssuer());
        assertEquals("com.example.app", claims.getSubject());
        assertTrue(claims.getAudience().contains("https://appleid.apple.com"));
    }

    @Test
    void throwsWhenConfigMissing() {
        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
                new AppleOAuthProperties(List.of(), null, null, null, null));
        assertThrows(IllegalStateException.class, generator::generate);
    }

    @Test
    void parsesPemWithEscapedNewlines() throws Exception {
        // 환경변수로 주입하면 실제 줄바꿈이 "\n" 두 글자 문자열로 들어오는 경우가 흔하다.
        KeyPair keyPair = generateEcKeyPair();
        String base64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String pemWithEscapedNewlines = "-----BEGIN PRIVATE KEY-----\\n" + base64 + "\\n-----END PRIVATE KEY-----";

        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
                new AppleOAuthProperties(List.of("com.example.app"), "TEAM123456", "KEY1234567",
                        "com.example.app", pemWithEscapedNewlines));

        assertDoesNotThrow(generator::generate);
    }

    private KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        return keyPairGenerator.generateKeyPair();
    }

    private String toPem(KeyPair keyPair) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }
}
