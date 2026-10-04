package com.luvin.diary.domain;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiaryTest {

    private static final Long OWNER = 1L;
    private static final Long AUTHOR = 2L;
    private static final Long OTHER = 3L;

    private final Diary diary = new Diary(AUTHOR, new DiaryRoom("방", null, OWNER), "제목", "내용");

    @Test
    void 작성자는_방_멤버_조회없이_볼_수_있다() {
        AtomicInteger memberQueries = new AtomicInteger();
        assertTrue(diary.canBeViewedBy(AUTHOR, () -> { memberQueries.incrementAndGet(); return false; }));
        assertEquals(0, memberQueries.get());
    }

    @Test
    void 방장은_방_멤버_조회없이_볼_수_있다() {
        AtomicInteger memberQueries = new AtomicInteger();
        assertTrue(diary.canBeViewedBy(OWNER, () -> { memberQueries.incrementAndGet(); return false; }));
        assertEquals(0, memberQueries.get());
    }

    @Test
    void 방_멤버는_볼_수_있다() {
        assertTrue(diary.canBeViewedBy(OTHER, () -> true));
    }

    @Test
    void 방_멤버가_아니면_볼_수_없다() {
        assertFalse(diary.canBeViewedBy(OTHER, () -> false));
    }

    @Test
    void 수정은_제목과_내용만_바꾼다() {
        diary.update("새 제목", "새 내용");
        assertEquals("새 제목", diary.getTitle());
        assertEquals("새 내용", diary.getContent());
        assertEquals(AUTHOR, diary.getUserId());
    }
}
