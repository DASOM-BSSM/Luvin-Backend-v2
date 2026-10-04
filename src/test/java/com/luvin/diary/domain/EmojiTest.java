package com.luvin.diary.domain;

import com.luvin.common.exception.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmojiTest {

    @ParameterizedTest
    @ValueSource(strings = {"😊", "😭", "😂", "🥺", "😍", "🔥", "👍🏻", "👨‍👩‍👧", "🇰🇷"})
    void 이모지_1개는_그대로_통과한다(String emoji) {
        assertEquals(emoji, Emoji.normalize(emoji));
    }

    @ParameterizedTest
    @ValueSource(strings = {"❤️", "❤"})
    void 하트는_이모지_표시문자_FE0F를_빼고_같은_값으로_저장한다(String heart) {
        assertEquals("❤", Emoji.normalize(heart));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "🔥🔥", "ㅋ", "a", "1", "#", "😊a", "가😊"})
    void 이모지_1개가_아니면_400(String raw) {
        assertThrows(BusinessException.class, () -> Emoji.normalize(raw));
    }
}
