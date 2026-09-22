package com.luvin.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 현재 AI 서비스 오류 body는 표준 error envelope로 통일되어 있지 않고 FastAPI {@code detail}
 * 문자열 또는 객체가 올 수 있다. HTTP status를 우선 사용하고 두 형식을 모두 안전하게 수용하며,
 * 파싱할 수 없는 body를 성공으로 간주하지 않는다 (요구사항 6절).
 */
public final class AiErrorBodyParser {

    private AiErrorBodyParser() {
    }

    public record ParsedError(String message, String errorCode) {
    }

    public static ParsedError parse(ObjectMapper mapper, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return new ParsedError("AI 서비스가 빈 오류 응답을 반환했습니다.", null);
        }
        try {
            JsonNode root = mapper.readTree(rawBody);
            JsonNode detail = root.get("detail");
            if (detail == null) {
                JsonNode errorCode = root.get("error_code");
                JsonNode message = root.get("message");
                if (errorCode != null || message != null) {
                    return new ParsedError(
                            message != null ? message.asText() : "AI 서비스 오류",
                            errorCode != null ? errorCode.asText() : null);
                }
                return new ParsedError(root.toString(), null);
            }
            if (detail.isTextual()) {
                return new ParsedError(detail.asText(), extractErrorCode(detail.asText()));
            }
            if (detail.isArray() && !detail.isEmpty()) {
                // FastAPI HTTPValidationError 형태: [{loc, msg, type}, ...]
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : detail) {
                    JsonNode msg = item.get("msg");
                    if (msg != null) {
                        if (!sb.isEmpty()) {
                            sb.append("; ");
                        }
                        sb.append(msg.asText());
                    }
                }
                return new ParsedError(!sb.isEmpty() ? sb.toString() : detail.toString(), null);
            }
            if (detail.isObject()) {
                JsonNode code = detail.get("code");
                JsonNode msg = detail.get("message");
                return new ParsedError(
                        msg != null ? msg.asText() : detail.toString(),
                        code != null ? code.asText() : null);
            }
            return new ParsedError(detail.toString(), null);
        } catch (Exception parseFailure) {
            // 파싱 실패 시에도 원문을 그대로 메시지로 보존한다. 성공으로 간주하지 않는다.
            return new ParsedError("AI 서비스 오류 응답 파싱 실패: " + rawBody, null);
        }
    }

    private static String extractErrorCode(String text) {
        // 일부 오류는 "REVISION_CONFLICT: ..." 형태의 문자열로 온다. 콜론 앞부분이 전부 대문자/언더스코어면
        // error code로 간주한다.
        int colonIndex = text.indexOf(':');
        if (colonIndex <= 0) {
            return null;
        }
        String candidate = text.substring(0, colonIndex).trim();
        return candidate.matches("^[A-Z][A-Z0-9_]*$") ? candidate : null;
    }
}
