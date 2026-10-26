package com.luvin.learning.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 말투 요약 JSON을 이전 값과 섞는다. 필드 구성은 AI 프롬프트가 정하므로 특정 key에 묶이지 않고 값의 종류로 섞는다.
 * <ul>
 *   <li>숫자: 이전*(1-r) + 새 값*r</li>
 *   <li>문자열 목록: 새 항목을 최대 {@link LearningPolicy#SPEECH_LIST_NEW_ITEMS}개만 앞에 더하고 전체 길이를 자른다</li>
 *   <li>문자열: 새 값(AI가 이전 요약을 보고 쓴다)</li>
 *   <li>객체: 같은 규칙으로 재귀</li>
 *   <li>새 결과에 없는 key: 이전 값 유지</li>
 * </ul>
 * 저장 전에 원문 인용(긴 문자열이 원문과 겹침)과 너무 긴 문자열은 버린다.
 */
public final class SpeechStyleBlender {

    private static final int MAX_DEPTH = 3;

    private SpeechStyleBlender() {
    }

    public static Map<String, Object> blend(Map<String, Object> previous, Map<String, Object> extracted,
                                            Collection<String> sourceTexts) {
        Map<String, Object> clean = sanitizeMap(extracted, normalized(sourceTexts), 0);
        if (previous == null || previous.isEmpty()) {
            return clean;
        }
        return mergeMap(previous, clean, 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeMap(Map<String, Object> previous, Map<String, Object> next, int depth) {
        Map<String, Object> merged = new LinkedHashMap<>(previous);
        next.forEach((key, value) -> {
            Object old = previous.get(key);
            if (old instanceof Number o && value instanceof Number n) {
                double r = LearningPolicy.SPEECH_BLEND_RATIO;
                merged.put(key, round(o.doubleValue() * (1 - r) + n.doubleValue() * r));
            } else if (old instanceof List<?> o && value instanceof List<?> n) {
                merged.put(key, mergeList(o, n));
            } else if (old instanceof Map<?, ?> o && value instanceof Map<?, ?> n && depth < MAX_DEPTH) {
                merged.put(key, mergeMap((Map<String, Object>) o, (Map<String, Object>) n, depth + 1));
            } else {
                merged.put(key, value);
            }
        });
        return merged;
    }

    private static List<Object> mergeList(List<?> previous, List<?> next) {
        LinkedHashSet<Object> merged = new LinkedHashSet<>();
        next.stream().filter(item -> !previous.contains(item))
                .limit(LearningPolicy.SPEECH_LIST_NEW_ITEMS)
                .forEach(merged::add);
        merged.addAll(previous);
        return new ArrayList<>(merged).subList(0, Math.min(merged.size(), LearningPolicy.SPEECH_LIST_MAX_ITEMS));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sanitizeMap(Map<String, Object> map, List<String> sources, int depth) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (map == null) {
            return clean;
        }
        map.forEach((key, value) -> {
            if (value instanceof Number n && Double.isFinite(n.doubleValue())) {
                clean.put(key, round(n.doubleValue()));
            } else if (value instanceof Boolean) {
                clean.put(key, value);
            } else if (value instanceof String s) {
                if (isSafe(s, LearningPolicy.SPEECH_TEXT_VALUE_MAX, sources)) {
                    clean.put(key, s.strip());
                }
            } else if (value instanceof List<?> list) {
                List<Object> items = list.stream()
                        .filter(item -> item instanceof String s && isSafe(s, LearningPolicy.SPEECH_LIST_VALUE_MAX, sources))
                        .map(item -> (Object) ((String) item).strip())
                        .distinct()
                        .limit(LearningPolicy.SPEECH_LIST_MAX_ITEMS)
                        .toList();
                if (!items.isEmpty()) {
                    clean.put(key, items);
                }
            } else if (value instanceof Map<?, ?> nested && depth < MAX_DEPTH) {
                Map<String, Object> child = sanitizeMap((Map<String, Object>) nested, sources, depth + 1);
                if (!child.isEmpty()) {
                    clean.put(key, child);
                }
            }
        });
        return clean;
    }

    /** 비어 있지 않고, 길이 제한 안이고, 원문을 그대로 옮긴 문장이 아니다. */
    static boolean isSafe(String value, int maxLength, List<String> normalizedSources) {
        String text = value.strip();
        if (text.isEmpty() || text.length() > maxLength) {
            return false;
        }
        String compact = compact(text);
        if (compact.length() < LearningPolicy.SPEECH_QUOTE_MIN_CHARS) {
            return true;
        }
        return normalizedSources.stream().noneMatch(source -> containsRun(source, compact));
    }

    /** value 안의 QUOTE_MIN_CHARS 길이 구간 중 하나라도 원문에 그대로 있으면 인용으로 본다. */
    private static boolean containsRun(String source, String value) {
        int window = LearningPolicy.SPEECH_QUOTE_MIN_CHARS;
        for (int i = 0; i + window <= value.length(); i++) {
            if (source.contains(value.substring(i, i + window))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> normalized(Collection<String> texts) {
        return texts == null ? List.of() : texts.stream().filter(t -> t != null).map(SpeechStyleBlender::compact).toList();
    }

    /** 공백 차이로 인용 검사를 피하지 못하게 공백을 지운다. */
    private static String compact(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
