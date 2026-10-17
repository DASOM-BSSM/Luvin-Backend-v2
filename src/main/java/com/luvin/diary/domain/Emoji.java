package com.luvin.diary.domain;

import com.luvin.common.exception.BusinessException;
import com.luvin.common.exception.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 반응 이모지 검사/정규화. 디자인의 기본 6개뿐 아니라 이모지 키보드의 아무 이모지나 받는다. */
public final class Emoji {

    private static final int MAX_LENGTH = 32;
    /** 사람 눈에 보이는 글자(grapheme) 단위. 👨‍👩‍👧, 👍🏻처럼 여러 코드가 합쳐진 이모지도 1개로 센다. */
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    /** "이모지 모양으로 보여줘" 표시 문자. ❤ 와 ❤️ 를 같은 반응으로 보기 위해 제거한다. */
    private static final String VARIATION_SELECTOR_16 = "️";

    private Emoji() {
    }

    /** 이모지 정확히 1개인지 확인하고 저장용 형태로 바꾼다. 아니면 400. */
    public static String normalize(String raw) {
        if (raw == null) {
            throw invalid();
        }
        String emoji = raw.strip().replace(VARIATION_SELECTOR_16, "");
        if (emoji.isEmpty() || emoji.length() > MAX_LENGTH || graphemeCount(emoji) != 1) {
            throw invalid();
        }
        boolean hasEmoji = emoji.codePoints()
                .anyMatch(cp -> Character.isEmoji(cp) && !isPlainKeycapBase(cp));
        if (!hasEmoji) {
            throw invalid();
        }
        return emoji;
    }

    private static int graphemeCount(String s) {
        Matcher m = GRAPHEME.matcher(s);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    /** 숫자/#/* 는 유니코드상 이모지 속성이 있지만 혼자서는 그냥 문자라 이모지로 치지 않는다. */
    private static boolean isPlainKeycapBase(int cp) {
        return (cp >= '0' && cp <= '9') || cp == '#' || cp == '*';
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
