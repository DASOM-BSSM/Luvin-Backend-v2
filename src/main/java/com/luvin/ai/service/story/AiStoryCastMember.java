package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiCastRole;

import java.util.UUID;

/** 판정·설계에 쓰는 출연진 한 명. breadId는 시즌 캐릭터의 characterId다. */
public record AiStoryCastMember(UUID breadId, AiCastRole castRole, String name, String gender) {

    public boolean isMe() {
        return castRole == AiCastRole.MY_BREAD;
    }
}
