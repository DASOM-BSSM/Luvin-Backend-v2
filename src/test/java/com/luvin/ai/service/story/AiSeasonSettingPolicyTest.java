package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiSeasonSetting;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiSeasonSettingPolicyTest {

    @Test
    void 나이_구간별_배경() {
        Random random = new Random(1);
        assertEquals(AiSeasonSetting.SCHOOL, AiSeasonSettingPolicy.decideSetting(15, random));
        assertEquals(AiSeasonSetting.SCHOOL, AiSeasonSettingPolicy.decideSetting(19, random));
        assertEquals(AiSeasonSetting.OFFICE, AiSeasonSettingPolicy.decideSetting(24, random));
        assertEquals(AiSeasonSetting.OFFICE, AiSeasonSettingPolicy.decideSetting(40, random));
    }

    @Test
    void 스무살부터_스물세살은_대학_또는_아르바이트() {
        Random random = new Random(7);
        Set<AiSeasonSetting> picked = IntStream.range(0, 50)
                .mapToObj(i -> AiSeasonSettingPolicy.decideSetting(20 + i % 4, random))
                .collect(Collectors.toSet());
        assertEquals(Set.of(AiSeasonSetting.CAMPUS, AiSeasonSetting.CAFE), picked);
    }

    @Test
    void audience와_연령대() {
        assertEquals("minor", AiSeasonSettingPolicy.audience(19));
        assertEquals("adult", AiSeasonSettingPolicy.audience(20));
        assertEquals("10대 후반", AiSeasonSettingPolicy.ageGroup(17));
        assertEquals("20대 초반", AiSeasonSettingPolicy.ageGroup(23));
        assertEquals("20대 중후반", AiSeasonSettingPolicy.ageGroup(24));
        assertEquals("30대 이상", AiSeasonSettingPolicy.ageGroup(31));
    }
}
