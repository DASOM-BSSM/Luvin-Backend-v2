package com.luvin.user.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BreadNamesTest {

    /** 앱(character-persona.ts deriveAdjective)이 같은 설문 결과 id로 고른 형용사. 앱과 규칙이 어긋나면 깨진다. */
    @Test
    void 앱과_같은_형용사를_고른다() {
        assertEquals("유쾌한 소금빵", BreadNames.of(UUID.fromString("022c127a-cb46-4400-825e-59deec5dad43"), "salt_bread"));
        assertEquals("다정한 카스테라", BreadNames.of(UUID.fromString("4a1129f8-81d9-4861-863e-66ab278eec71"), "castella"));
        assertEquals("성숙한 팥빵", BreadNames.of(UUID.fromString("62973629-0eba-4dc1-b3b1-8f9646eb2c9b"), "red_bean_bread"));
        assertEquals("솔직한 크림빵", BreadNames.of(UUID.fromString("2ea90de9-a417-4930-ae64-fe040b4477d8"), "cream_bread"));
    }

    @Test
    void 설문_전이거나_모르는_빵이면_null() {
        UUID resultId = UUID.fromString("022c127a-cb46-4400-825e-59deec5dad43");
        assertNull(BreadNames.of(null, "salt_bread"));
        assertNull(BreadNames.of(resultId, null));
        assertNull(BreadNames.of(resultId, "unknown_bread"));
    }

    @Test
    void 해시가_int_최솟값이어도_음수_번호가_나오지_않는다() {
        // "polygenelubricants".hashCode() == Integer.MIN_VALUE
        assertEquals(Integer.MIN_VALUE, "polygenelubricants".hashCode());
        assertEquals("조용한", BreadNames.adjective("polygenelubricants")); // 앱 결과와 같다
    }
}
