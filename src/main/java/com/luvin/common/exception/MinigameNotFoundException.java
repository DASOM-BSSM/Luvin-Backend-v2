package com.luvin.common.exception;

public class MinigameNotFoundException extends RuntimeException {
    public MinigameNotFoundException(Long gameId) {
        super("존재하지 않는 미니게임입니다. gameId=" + gameId);
    }
}
