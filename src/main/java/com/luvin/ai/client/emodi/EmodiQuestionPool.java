package com.luvin.ai.client.emodi;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 미리 준비해 둔 질문. 오늘의 밸런스 게임은 AI 생성이 실패하면 여기서 대체 질문을 고르고(daily_questions.source=FALLBACK),
 * stub 클라이언트는 AI 대신 여기서 고른다. 자리표시 문장이 아니라 그대로 내보내도 되는 실제 질문이다.
 */
public final class EmodiQuestionPool {

    /** O/X로 답하는 문장. */
    static final List<String> BALANCE = List.of(
            "좋아하는 사람이 생기면 친구들에게 바로 말하는 편이다",
            "연락은 답장 속도보다 내용이 더 중요하다",
            "썸 탈 때 먼저 연락하는 건 지는 거라고 생각한다",
            "연인과 하루 종일 연락이 안 돼도 괜찮다",
            "다투고 나면 그날 안에 꼭 풀어야 한다",
            "기념일은 꼭 챙겨야 한다",
            "좋아하는 마음은 말보다 행동으로 보여주는 편이다",
            "연인의 친구들과도 친하게 지내고 싶다",
            "서운한 점은 바로 말하는 편이다",
            "첫인상이 별로였던 사람과도 연애할 수 있다",
            "연인과 취미가 달라도 상관없다",
            "고백은 직접 만나서 해야 한다",
            "연애할 때 혼자만의 시간이 꼭 필요하다",
            "연인의 휴대폰을 보고 싶었던 적이 있다",
            "사소한 일상도 연인에게 다 공유하는 편이다",
            "좋아하는 사람 앞에서는 말수가 줄어든다",
            "깜짝 이벤트를 받는 것보다 하는 게 더 좋다",
            "헤어진 연인과 친구로 지낼 수 있다",
            "연인이 이성 친구와 단둘이 밥 먹는 건 괜찮다",
            "답장을 일부러 늦게 보낸 적이 있다",
            "연애 고민은 혼자 해결하는 편이다",
            "좋아하는 사람의 SNS를 자주 확인한다",
            "약속 시간에 늦는 연인은 참기 어렵다",
            "사랑한다는 말을 자주 하는 편이다");

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

    /** 날짜로 고르되, 최근에 나간 질문은 건너뛴다(전부 최근에 나갔으면 날짜로 고른 것을 그대로 쓴다). */
    public static String balanceFor(LocalDate date, Collection<String> recent) {
        return pick(BALANCE, Math.floorMod(date.toEpochDay(), BALANCE.size()), recent);
    }

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
