package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiSeasonSetting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    void 지난_시즌이_없으면_첫_시즌처럼_나이로_정한다() {
        Random random = new Random(3);
        assertEquals(AiSeasonSetting.SCHOOL, AiSeasonSettingPolicy.decideNextSetting(17, List.of(), random));
        assertEquals(AiSeasonSetting.OFFICE, AiSeasonSettingPolicy.decideNextSetting(27, Collections.singletonList(null), random));
    }

    @Test
    void 새_시즌은_나이에_맞는_배경_중_안_쓴_배경을_고른다() {
        Random random = new Random(5);
        assertEquals(AiSeasonSetting.CAFE,
                AiSeasonSettingPolicy.decideNextSetting(17, List.of(AiSeasonSetting.SCHOOL), random));
        // 20대: 1시즌 campus → 2·3시즌은 cafe·office를 하나씩 쓴다.
        AiSeasonSetting second = AiSeasonSettingPolicy.decideNextSetting(25, List.of(AiSeasonSetting.CAMPUS), random);
        AiSeasonSetting third = AiSeasonSettingPolicy.decideNextSetting(25, List.of(second, AiSeasonSetting.CAMPUS),
                random);
        assertEquals(Set.of(AiSeasonSetting.CAFE, AiSeasonSetting.OFFICE), Set.of(second, third));
    }

    @Test
    void 다_썼으면_가장_오래전에_쓴_배경으로_돌아가_직전_시즌과_겹치지_않는다() {
        Random random = new Random(9);
        List<AiSeasonSetting> history = new ArrayList<>(List.of(AiSeasonSetting.SCHOOL));
        for (int season = 2; season <= 8; season++) {
            AiSeasonSetting next = AiSeasonSettingPolicy.decideNextSetting(17, history, random);
            assertEquals(history.get(0) == AiSeasonSetting.SCHOOL ? AiSeasonSetting.CAFE : AiSeasonSetting.SCHOOL,
                    next);
            history.add(0, next);
        }
        assertEquals(AiSeasonSetting.CAMPUS, AiSeasonSettingPolicy.decideNextSetting(22,
                List.of(AiSeasonSetting.OFFICE, AiSeasonSetting.CAFE, AiSeasonSetting.CAMPUS), random));
        // 나이에 맞지 않는 배경(10대의 office)은 고르지 않는다.
        assertEquals(AiSeasonSetting.SCHOOL,
                AiSeasonSettingPolicy.decideNextSetting(16, List.of(AiSeasonSetting.CAFE, AiSeasonSetting.OFFICE),
                        random));
        assertEquals(AiSeasonSetting.CAFE,
                AiSeasonSettingPolicy.decideNextSetting(35, List.of(AiSeasonSetting.OFFICE), random));
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
