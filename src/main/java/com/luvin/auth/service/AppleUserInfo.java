package com.luvin.auth.service;

/**
 * 애플 identityToken 검증 결과. appleId는 애플이 발급하는 안정적인 고유 사용자 ID(sub claim)다.
 * email은 "Hide My Email" 사용 시 애플 relay 주소(@privaterelay.appleid.com)로 올 수 있다.
 */
public record AppleUserInfo(String appleId, String email, boolean emailVerified) {
}
