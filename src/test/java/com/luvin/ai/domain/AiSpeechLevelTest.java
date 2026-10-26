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
    void 시즌_시작_말투는_school만_casual() {
        assertEquals(CASUAL, AiSpeechLevel.initial(AiSeasonSetting.SCHOOL));
        assertEquals(FORMAL, AiSpeechLevel.initial(AiSeasonSetting.CAMPUS));
        assertEquals(FORMAL, AiSpeechLevel.initial(AiSeasonSetting.CAFE));
        assertEquals(FORMAL, AiSpeechLevel.initial(AiSeasonSetting.OFFICE));
    }

    @Test
    void campus는_누구나_casual로_바뀔_수_있다() {
        assertTrue(AiSpeechLevel.allowsChange(AiSeasonSetting.CAMPUS, FORMAL, CASUAL, false));
    }

    @Test
    void office_cafe는_연인만_casual로_바뀐다() {
        for (AiSeasonSetting setting : new AiSeasonSetting[]{AiSeasonSetting.OFFICE, AiSeasonSetting.CAFE}) {
            assertFalse(AiSpeechLevel.allowsChange(setting, FORMAL, CASUAL, false));
            assertTrue(AiSpeechLevel.allowsChange(setting, FORMAL, CASUAL, true));
        }
    }

    @Test
    void casual에서_formal로_되돌아가지_않는다() {
        for (AiSeasonSetting setting : AiSeasonSetting.values()) {
            assertFalse(AiSpeechLevel.allowsChange(setting, CASUAL, FORMAL, true));
            assertFalse(AiSpeechLevel.allowsChange(setting, CASUAL, CASUAL, true));
            assertFalse(AiSpeechLevel.allowsChange(setting, FORMAL, null, true));
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
