package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiBreadRelation;
import com.luvin.ai.domain.AiSpeechLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiStoryStateWriterMemoryTest {

    @Test
    void 최근_기억은_3개만_남기고_같은_문장은_겹쳐_쌓지_않는다() {
        AiBreadRelation relation = new AiBreadRelation(1L, null, UUID.randomUUID(), 25, "처음 만남",
                AiSpeechLevel.CASUAL);
        remember(relation, "포스터 색을 같이 골랐다.");
        remember(relation, "조퇴한 날 안부를 물었다.");
        remember(relation, "포스터 색을  같이 골랐다. ");

        assertEquals(List.of("조퇴한 날 안부를 물었다.", "포스터 색을  같이 골랐다."),
                AiStoryJson.readStrings(relation.getRecentMemoriesJson()));

        remember(relation, "축제 당일 함께 웃었다.");
        remember(relation, "시험 기간에 연락이 줄었다.");
        assertEquals(List.of("포스터 색을  같이 골랐다.", "축제 당일 함께 웃었다.", "시험 기간에 연락이 줄었다."),
                AiStoryJson.readStrings(relation.getRecentMemoriesJson()));
    }

    private static void remember(AiBreadRelation relation, String memory) {
        relation.applyMemory(null, AiStoryStateWriter.appendRecentMemory(relation, memory), null, null, null, null);
    }
}
