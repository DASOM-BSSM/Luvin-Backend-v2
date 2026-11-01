package com.luvin.diary.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 오늘의 밸런스 게임으로 내보낼 질문 목록. 날짜로 하나씩 고르고, 최근에 나간 질문은 건너뛴다.
 * 자리표시 문장이 아니라 그대로 내보내도 되는 실제 질문이다. 보기는 항상 2개.
 */
public final class BalanceGamePool {

    public record Entry(String question, String optionA, String optionB) {
    }

    static final List<Entry> ENTRIES = List.of(
            new Entry("좋아하는 사람이 생기면?", "바로 티를 낸다", "확실해질 때까지 숨긴다"),
            new Entry("연락 스타일은?", "하루 종일 자주 연락", "하루 한 번 길게 통화"),
            new Entry("다투고 나면?", "그날 바로 풀어야 한다", "하루쯤 생각할 시간이 필요하다"),
            new Entry("데이트 계획은?", "꼼꼼하게 미리 짠다", "그날 기분 따라 즉흥으로"),
            new Entry("고백은 어떻게?", "직접 만나서 말한다", "편지나 메시지로 전한다"),
            new Entry("기념일은?", "작은 거라도 꼭 챙긴다", "특별한 날보다 평소가 중요하다"),
            new Entry("서운한 일이 생기면?", "바로 말한다", "쌓아 두다가 한 번에 말한다"),
            new Entry("연인과의 시간은?", "매일 조금씩 자주 보기", "가끔 만나도 하루 종일 같이"),
            new Entry("깜짝 이벤트는?", "해 주는 게 더 좋다", "받는 게 더 좋다"),
            new Entry("사랑 표현은?", "말로 자주 한다", "행동으로 보여 준다"),
            new Entry("연인의 이성 친구는?", "단둘이 밥 정도는 괜찮다", "단둘이 만나는 건 싫다"),
            new Entry("답장이 늦을 때 나는?", "바쁜가 보다 하고 넘긴다", "무슨 일 있나 계속 신경 쓰인다"),
            new Entry("헤어진 연인과?", "친구로 지낼 수 있다", "다시는 연락 안 한다"),
            new Entry("끌리는 사랑은?", "첫눈에 반하는 사랑", "천천히 스며드는 사랑"),
            new Entry("연인과 취미는?", "같은 취미를 함께 하고 싶다", "각자 취미를 존중하고 싶다"),
            new Entry("힘든 날 연인에게 바라는 건?", "조용히 옆에 있어 주기", "해결 방법을 같이 찾아 주기"),
            new Entry("썸 탈 때 먼저 연락은?", "내가 먼저 하는 편", "상대가 먼저 해 주길 기다리는 편"),
            new Entry("연인의 휴대폰은?", "보여 달라면 보여 줄 수 있다", "서로 안 보는 게 맞다"),
            new Entry("싸웠을 때 먼저 사과는?", "잘못이 없어도 먼저 한다", "잘못한 사람이 먼저 해야 한다"),
            new Entry("데이트 장소는?", "새로운 곳을 찾아다닌다", "익숙한 단골집이 편하다"),
            new Entry("연애 고민은?", "친구들에게 털어놓는다", "혼자 정리한다"),
            new Entry("좋아하는 사람 앞에서 나는?", "말이 많아진다", "말수가 줄어든다"),
            new Entry("일상 공유는?", "사소한 것까지 다 말한다", "중요한 일만 말한다"),
            new Entry("연인과 여행은?", "계획대로 알차게", "느긋하게 쉬다 오기"),
            new Entry("선물은?", "갖고 싶던 걸 정확히 받기", "예상 못 한 걸로 놀라기"),
            new Entry("연애할 때 혼자만의 시간은?", "꼭 있어야 한다", "같이 있는 게 더 좋다"),
            new Entry("약속에 늦는 연인은?", "가끔은 이해할 수 있다", "시간 약속은 꼭 지켜야 한다"),
            new Entry("SNS에 연애 티 내기?", "커플 사진도 올리고 싶다", "둘만 알고 싶다"),
            new Entry("연인이 약속보다 친구 모임을 택하면?", "그럴 수 있다고 이해한다", "서운한 마음을 말한다"),
            new Entry("연인의 친구들과는?", "다 같이 친하게 지내고 싶다", "적당한 거리가 편하다"));

    private BalanceGamePool() {
    }

    /** 날짜로 고르되, 최근에 나간 질문은 건너뛴다(전부 최근에 나갔으면 날짜로 고른 것을 그대로 쓴다). */
    public static Entry pick(LocalDate date, Collection<String> recent) {
        int start = Math.floorMod(date.toEpochDay(), ENTRIES.size());
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get((start + i) % ENTRIES.size());
            if (!recent.contains(entry.question())) {
                return entry;
            }
        }
        return ENTRIES.get(start);
    }
}
