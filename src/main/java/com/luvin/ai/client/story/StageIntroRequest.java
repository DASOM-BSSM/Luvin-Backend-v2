package com.luvin.ai.client.story;

import java.util.List;

/** 5-1 POST /luvin/stage-intro. seasonRole은 7장 역할(내 빵, 중심 후보, 내 빵의 친구, 분위기 메이커). */
public record StageIntroRequest(String setting, String audience, String ageGroup, List<Cast> cast) {

    public record Cast(String breadId, String name, String breadType, String gender, String attachmentStyle,
                       String seasonRole) {
    }
}
