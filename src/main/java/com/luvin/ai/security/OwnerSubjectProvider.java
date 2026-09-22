package com.luvin.ai.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * X-Owner-Subject 값을 인증 주체(Spring이 검증한 내부 사용자 ID)로부터 직접 생성한다.
 * 이메일, 전화번호, 실명 등 개인정보나 앱이 직접 제공한 값을 사용하지 않는다 (요구사항 3.2, 7절).
 * memberId를 그대로 노출하지 않도록 고정 salt를 섞은 SHA-256 해시의 불투명 문자열로 만든다.
 */
@Component
public class OwnerSubjectProvider {

    // 실제 운영에서는 애플리케이션 설정(app.ai.owner-subject-salt)으로 주입한다.
    // 여기서는 상수로 두되, 값 자체는 비밀정보로 취급해 형상관리에서 별도 관리하는 것을 권장한다.
    private static final String SALT_PREFIX = "luvin-ai-owner-subject-v1:";

    public String resolve(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId가 없습니다. 인증되지 않은 요청입니다.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((SALT_PREFIX + memberId).getBytes(StandardCharsets.UTF_8));
            return "usr_" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
