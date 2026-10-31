package com.luvin.ai.domain;

import org.junit.jupiter.api.Test;

import static com.luvin.ai.domain.AiSpeechLevel.CASUAL;
import static com.luvin.ai.domain.AiSpeechLevel.FORMAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSpeechLevelTest {

    @Test
    void 시즌_시작_말투는_친구는_항상_casual_나머지는_school만_casual() {
        for (AiCastRole role : new AiCastRole[]{AiCastRole.SECURE, AiCastRole.MOOD_MAKER, null}) {
            assertEquals(CASUAL, AiSpeechLevel.initial(AiSeasonSetting.SCHOOL, role));
            assertEquals(FORMAL, AiSpeechLevel.initial(AiSeasonSetting.CAMPUS, role));
            assertEquals(FORMAL, AiSpeechLevel.initial(AiSeasonSetting.CAFE, role));
            assertEquals(FORMAL, AiSpeechLevel.initial(AiSeasonSetting.OFFICE, role));
        }
        for (AiSeasonSetting setting : AiSeasonSetting.values()) {
            assertEquals(CASUAL, AiSpeechLevel.initial(setting, AiCastRole.FRIEND));
        }
    }

    @Test
    void 친구는_저장된_말투가_formal이어도_casual로_읽는다() {
        assertEquals(CASUAL, AiSpeechLevel.current(FORMAL, AiSeasonSetting.OFFICE, AiCastRole.FRIEND));
        assertEquals(CASUAL, AiSpeechLevel.current(null, AiSeasonSetting.CAFE, AiCastRole.FRIEND));
        assertEquals(FORMAL, AiSpeechLevel.current(FORMAL, AiSeasonSetting.OFFICE, AiCastRole.MOOD_MAKER));
        assertEquals(FORMAL, AiSpeechLevel.current(null, AiSeasonSetting.CAFE, AiCastRole.AVOIDANT));
        assertEquals(CASUAL, AiSpeechLevel.current(CASUAL, AiSeasonSetting.CAFE, AiCastRole.SECURE));
    }

    @Test
    void campus는_누구나_casual로_바뀔_수_있다() {
        assertTrue(AiSpeechLevel.allowsChange(AiSeasonSetting.CAMPUS, AiCastRole.MOOD_MAKER, FORMAL, CASUAL, false));
        assertTrue(AiSpeechLevel.allowsChange(AiSeasonSetting.CAMPUS, AiCastRole.ANXIOUS, FORMAL, CASUAL, false));
    }

    @Test
    void office_cafe는_연인과_친구만_casual로_바뀐다() {
        for (AiSeasonSetting setting : new AiSeasonSetting[]{AiSeasonSetting.OFFICE, AiSeasonSetting.CAFE}) {
            assertFalse(AiSpeechLevel.allowsChange(setting, AiCastRole.SECURE, FORMAL, CASUAL, false));
            assertFalse(AiSpeechLevel.allowsChange(setting, AiCastRole.MOOD_MAKER, FORMAL, CASUAL, false));
            assertTrue(AiSpeechLevel.allowsChange(setting, AiCastRole.SECURE, FORMAL, CASUAL, true));
            assertTrue(AiSpeechLevel.allowsChange(setting, AiCastRole.FRIEND, FORMAL, CASUAL, false));
        }
    }

    @Test
    void casual에서_formal로_되돌아가지_않는다() {
        for (AiSeasonSetting setting : AiSeasonSetting.values()) {
            for (AiCastRole role : new AiCastRole[]{AiCastRole.SECURE, AiCastRole.FRIEND}) {
                assertFalse(AiSpeechLevel.allowsChange(setting, role, CASUAL, FORMAL, true));
                assertFalse(AiSpeechLevel.allowsChange(setting, role, CASUAL, CASUAL, true));
                assertFalse(AiSpeechLevel.allowsChange(setting, role, FORMAL, null, true));
            }
        }
    }

    @Test
    void 계약_값은_소문자() {
        assertEquals(CASUAL, AiSpeechLevel.fromWire("casual"));
        assertEquals(FORMAL, AiSpeechLevel.fromWire("formal"));
        assertNull(AiSpeechLevel.fromWire("CASUAL"));
        assertEquals("formal", FORMAL.wireValue());
    }
}
