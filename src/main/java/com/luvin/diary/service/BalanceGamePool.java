package com.luvin.diary.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 오늘의 밸런스 게임으로 내보낼 질문 목록. 날짜로 하나씩 고르고, 최근에 나간 질문과 규칙({@link Entry#isValid})에
 * 맞지 않는 질문은 건너뛴다. 자리표시 문장이 아니라 그대로 내보내도 되는 실제 질문이다.
 *
 * 앱은 보기 문구를 버튼에 그대로 보여주고 첫 번째 보기를 왼쪽에 둔다 — 보기는 찬성(optionA) → 반대(optionB) 순서로
 * 1~6자로 짧게 쓴다. 질문 앞의 "Q."는 앱이 붙이므로 질문 문장에 넣지 않는다.
 */
public final class BalanceGamePool {

    /** 보기 문구 최대 길이(앞뒤 공백 제외). */
    public static final int OPTION_MAX_LENGTH = 6;
    /** 질문 문장 최대 길이. 20자 안팎으로 쓴다. */
    public static final int QUESTION_MAX_LENGTH = 25;

    public record Entry(String question, String optionA, String optionB) {

        /** 보기 2개가 각각 1~6자이고 서로 다르며, 질문이 비어 있지 않고 25자 이내이고 "Q."로 시작하지 않는다. */
        public boolean isValid() {
            return isValidQuestion(question) && isValidOption(optionA) && isValidOption(optionB)
                    && !optionA.strip().equals(optionB.strip());
        }

        private static boolean isValidQuestion(String question) {
            return question != null && !question.isBlank() && question.strip().length() <= QUESTION_MAX_LENGTH
                    && !question.strip().startsWith("Q.");
        }

        private static boolean isValidOption(String option) {
            return option != null && !option.isBlank() && option.strip().length() <= OPTION_MAX_LENGTH;
        }
    }

    static final List<Entry> ENTRIES = List.of(
            new Entry("좋아하는 사람이 생기면?", "티 낸다", "숨긴다"),
            new Entry("연락 스타일은?", "자주 연락", "한 번 길게"),
            new Entry("다투고 나면?", "바로 푼다", "시간 필요"),
            new Entry("데이트 계획은?", "미리 짠다", "즉흥으로"),
            new Entry("고백은 어떻게?", "직접 만나서", "메시지로"),
            new Entry("기념일은?", "꼭 챙긴다", "평소가 중요"),
            new Entry("서운한 일이 생기면?", "바로 말한다", "쌓아 둔다"),
            new Entry("연인과의 시간은?", "자주 짧게", "가끔 길게"),
            new Entry("깜짝 이벤트는?", "해 주기", "받기"),
            new Entry("사랑 표현은?", "말로", "행동으로"),
            new Entry("연인의 이성 친구와 단둘이 밥은?", "괜찮다", "싫다"),
            new Entry("답장이 늦을 때 나는?", "넘긴다", "신경 쓰인다"),
            new Entry("헤어진 연인과?", "친구 가능", "연락 끊기"),
            new Entry("끌리는 사랑은?", "첫눈에 반함", "천천히 스밈"),
            new Entry("연인과 취미는?", "같이 하기", "각자 하기"),
            new Entry("힘든 날 연인에게 바라는 건?", "곁에 있기", "같이 해결"),
            new Entry("썸 탈 때 먼저 연락은?", "내가 먼저", "기다린다"),
            new Entry("연인의 휴대폰은?", "보여 준다", "안 본다"),
            new Entry("싸웠을 때 먼저 사과는?", "내가 먼저", "잘못한 쪽"),
            new Entry("데이트 장소는?", "새로운 곳", "단골집"),
            new Entry("연애 고민은?", "털어놓는다", "혼자 정리"),
            new Entry("좋아하는 사람 앞에서 나는?", "말 많아짐", "말수 줄어듦"),
            new Entry("일상 공유는?", "전부 공유", "중요한 것만"),
            new Entry("연인과 여행은?", "알차게", "느긋하게"),
            new Entry("선물은?", "원하던 것", "깜짝 선물"),
            new Entry("연애할 때 혼자만의 시간은?", "꼭 필요", "같이가 좋아"),
            new Entry("약속에 늦는 연인은?", "이해한다", "못 참는다"),
            new Entry("SNS에 연애 티 내기?", "올린다", "둘만 안다"),
            new Entry("연인이 약속보다 친구 모임을 택하면?", "이해한다", "서운하다"),
            new Entry("연인의 친구들과는?", "친하게", "적당히"));

    private BalanceGamePool() {
    }

    /**
     * 날짜로 고르되, 규칙에 맞지 않는 질문과 최근에 나간 질문은 건너뛰고 다음 후보를 쓴다. 규칙에 맞는 질문이 전부
     * 최근에 나갔으면 날짜로 고른 다음 규칙에 맞는 질문을 쓰고, 규칙에 맞는 질문이 하나도 없으면 null.
     */
    public static Entry pick(LocalDate date, Collection<String> recent) {
        return pick(ENTRIES, date, recent);
    }

    static Entry pick(List<Entry> entries, LocalDate date, Collection<String> recent) {
        if (entries.isEmpty()) {
            return null;
        }
        int start = Math.floorMod(date.toEpochDay(), entries.size());
        Entry fallback = null;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get((start + i) % entries.size());
            if (!entry.isValid()) {
                continue;
            }
            if (!recent.contains(entry.question())) {
                return entry;
            }
            if (fallback == null) {
                fallback = entry;
            }
        }
        return fallback;
    }
}
