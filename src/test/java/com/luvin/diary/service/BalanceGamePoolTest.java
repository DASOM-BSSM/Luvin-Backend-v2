package com.luvin.diary.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceGamePoolTest {

    @Test
    void 준비된_질문은_모두_규칙에_맞고_질문이_겹치지_않는다() {
        Set<String> questions = new HashSet<>();
        for (BalanceGamePool.Entry entry : BalanceGamePool.ENTRIES) {
            assertTrue(entry.isValid(), entry.toString());
            assertTrue(questions.add(entry.question()), "겹치는 질문: " + entry.question());
        }
    }

    @Test
    void 규칙() {
        assertTrue(new BalanceGamePool.Entry("데이트 계획은?", "미리 짠다", "즉흥으로").isValid());
        assertTrue(new BalanceGamePool.Entry("질문?", " 여섯글자요 ", "a").isValid()); // 앞뒤 공백 제외 6자
        assertFalse(new BalanceGamePool.Entry("질문?", "일곱글자입니다", "짧다").isValid());
        assertFalse(new BalanceGamePool.Entry("질문?", "같다", " 같다 ").isValid());
        assertFalse(new BalanceGamePool.Entry("질문?", "", "짧다").isValid());
        assertFalse(new BalanceGamePool.Entry(" ", "길다", "짧다").isValid());
        assertFalse(new BalanceGamePool.Entry("Q. 질문?", "길다", "짧다").isValid());
        assertFalse(new BalanceGamePool.Entry("가".repeat(26), "길다", "짧다").isValid());
    }

    @Test
    void 규칙에_맞지_않거나_최근에_나간_질문은_건너뛰고_다음_후보를_고른다() {
        BalanceGamePool.Entry invalid = new BalanceGamePool.Entry("보기가 긴 질문?", "확실해질 때까지 숨긴다", "티 낸다");
        BalanceGamePool.Entry recent = new BalanceGamePool.Entry("최근 질문?", "가", "나");
        BalanceGamePool.Entry next = new BalanceGamePool.Entry("다음 질문?", "가", "나");
        List<BalanceGamePool.Entry> entries = List.of(invalid, recent, next);
        LocalDate date = LocalDate.ofEpochDay(0); // 0번(invalid)부터 본다

        assertEquals(next, BalanceGamePool.pick(entries, date, List.of("최근 질문?")));
        // 규칙에 맞는 질문이 전부 최근에 나갔으면 그중 처음 것을 쓴다(규칙에 안 맞는 질문은 쓰지 않는다).
        assertEquals(recent, BalanceGamePool.pick(entries, date, List.of("최근 질문?", "다음 질문?")));
        assertNull(BalanceGamePool.pick(List.of(invalid), date, List.of()));
    }
}
