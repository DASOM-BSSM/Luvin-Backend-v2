package com.luvin.ai.service.story;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;

/**
 * 장면 블록·관계 기록 JSON 컬럼 직렬화. 앱 응답 형식(camelCase) 그대로 저장해 GET content가 다시 조립하지 않고
 * 읽어서 돌려준다. AI wire 형식(snake_case, aiObjectMapper)과는 별개다.
 */
public final class AiStoryJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private AiStoryJson() {
    }

    public static String write(Object value) {
        try {
            return value == null ? null : MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("장면 블록 JSON을 직렬화할 수 없습니다.", e);
        }
    }

    public static <T> T read(String json, Class<T> type) {
        try {
            return json == null ? null : MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("저장된 장면 블록 JSON을 읽을 수 없습니다.", e);
        }
    }

    public static <T> T read(String json, TypeReference<T> type) {
        try {
            return json == null ? null : MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("저장된 장면 블록 JSON을 읽을 수 없습니다.", e);
        }
    }

    public static List<String> readStrings(String json) {
        List<String> values = read(json, STRING_LIST);
        return values == null ? List.of() : values;
    }
}
