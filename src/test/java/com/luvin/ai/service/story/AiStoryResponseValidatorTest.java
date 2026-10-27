package com.luvin.ai.service.story;

import com.luvin.ai.client.story.EpisodeResponse;
import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.domain.AiSceneBlockType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiStoryResponseValidatorTest {

    private static final List<AiStoryResponseValidator.ExpectedBlock> TRANSITION_ONLY = List.of(
            new AiStoryResponseValidator.ExpectedBlock("b2", AiSceneBlockType.TRANSITION, Set.of(), Set.of()));

    @Test
    void 전환_문구는_FastAPI_계약과_같이_300자까지_받는다() {
        assertEquals(300, AiStoryResponseValidator.TRANSITION_MAX);
        // 15자 제한일 때 실제로 실패한 길이(62·76자)와 경계값
        for (int length : List.of(62, 76, 300)) {
            assertDoesNotThrow(() -> AiStoryResponseValidator.validateEpisode(episode("가".repeat(length)), TRANSITION_ONLY),
                    length + "자");
        }
    }

    @Test
    void 전환_문구가_300자를_넘거나_비어_있으면_실패한다() {
        assertThrows(AiStoryInvalidResponseException.class,
                () -> AiStoryResponseValidator.validateEpisode(episode("가".repeat(301)), TRANSITION_ONLY));
        assertThrows(AiStoryInvalidResponseException.class,
                () -> AiStoryResponseValidator.validateEpisode(episode(" "), TRANSITION_ONLY));
    }

    private static EpisodeResponse episode(String transitionText) {
        return new EpisodeResponse(new StoryTypes.Intro("새 학기", "새 학기 반 배정", "새 학기 첫날 반 배정이 나왔다."),
                List.of(new StoryTypes.GeneratedBlock("b2", null, transitionText, null)), List.of());
    }
}
