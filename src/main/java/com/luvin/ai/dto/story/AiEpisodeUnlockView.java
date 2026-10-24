package com.luvin.ai.dto.story;

/** POST /episodes/{order}/unlock 응답. 1~3화이거나 이미 연 회차면 차감 없이 같은 응답이다. tokenBalance는 열고 난 뒤 잔액. */
public record AiEpisodeUnlockView(int order, boolean unlocked, int tokenBalance) {
}
