package com.luvin.ai.service.exception;

/** 다음 시즌을 시작할 무료 횟수도, 토큰도 부족할 때. required/balance를 응답 data에 그대로 노출한다. */
public class AiInsufficientTokensException extends RuntimeException {

    private final int required;
    private final int balance;

    public AiInsufficientTokensException(int required, int balance) {
        super("보유한 토큰이 부족합니다. 필요: " + required + ", 보유: " + balance);
        this.required = required;
        this.balance = balance;
    }

    public int getRequired() {
        return required;
    }

    public int getBalance() {
        return balance;
    }
}
