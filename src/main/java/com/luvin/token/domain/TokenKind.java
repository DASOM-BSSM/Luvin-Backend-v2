package com.luvin.token.domain;

/**
 * 토큰 종류.
 * - SALT(소금빵): 무료. 미니게임으로만 얻는다. user_tokens.token_balance
 * - BREAD(식빵): 유료. 회차 대기(다음 자정까지)를 건너뛰고 바로 열 때 쓴다. user_tokens.bread_token_balance
 */
public enum TokenKind {
    SALT,
    BREAD;

    /** 토큰 내역(reason)·로그에 쓰는 이름. */
    public String label() {
        return this == BREAD ? "식빵" : "소금빵";
    }
}
