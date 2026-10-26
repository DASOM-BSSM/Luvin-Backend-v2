package com.luvin.ai.client.emodi;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 미리 준비해 둔 그룹 질문. stub 클라이언트는 AI 대신 여기서 고른다. 자리표시 문장이 아니라 그대로 내보내도 되는 실제 질문이다.
 */
public final class EmodiQuestionPool {

    /** 그룹에서 각자 짧게 답하는 질문. */
    static final List<String> ROOM = List.of(
            "요즘 나를 가장 웃게 만든 일은 뭐야?",
            "이번 주에 가장 고마웠던 순간은?",
            "요즘 자주 듣는 노래 하나만 알려줘",
            "오늘 기분을 날씨로 표현하면?",
            "최근에 새로 알게 된 내 모습이 있어?",
            "요즘 가장 기다려지는 일은 뭐야?",
            "지친 날 나를 회복시키는 방법은?",
            "요즘 마음에 남은 한마디가 있어?",
            "이번 주 나에게 칭찬해주고 싶은 점은?",
            "요즘 가장 많이 생각나는 사람은 어떤 사람이야?",
            "최근에 용기 냈던 순간이 있어?",
            "요즘 나의 작은 행복 하나만 말해줘",
            "지금 가장 듣고 싶은 말은 뭐야?",
            "이번 달에 꼭 해보고 싶은 일은?",
            "요즘 나를 가장 신경 쓰이게 하는 건?",
            "오늘 하루를 한 단어로 표현하면?");

    private EmodiQuestionPool() {
    }

    /** 날짜·방으로 고르되, 최근에 나간 질문은 건너뛴다(전부 최근에 나갔으면 고른 것을 그대로 쓴다). */
    public static String roomFor(long roomId, LocalDate date, Collection<String> recent) {
        long seed = date.toEpochDay() * 31 + roomId;
        return pick(ROOM, Math.floorMod(seed, ROOM.size()), recent);
    }

    private static String pick(List<String> pool, int start, Collection<String> recent) {
        for (int i = 0; i < pool.size(); i++) {
            String candidate = pool.get((start + i) % pool.size());
            if (!recent.contains(candidate)) {
                return candidate;
            }
        }
        return pool.get(start);
    }
}
