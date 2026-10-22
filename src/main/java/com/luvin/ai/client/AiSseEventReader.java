package com.luvin.ai.client;

import com.luvin.ai.client.dto.AiSseEventDto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.function.Predicate;

/**
 * text/event-stream 본문을 이벤트 단위로 자른다 (WHATWG SSE 형식).
 * 빈 줄에서 이벤트 1개가 끝나고, ':'로 시작하는 줄(주석/heartbeat)과 retry 필드는 무시한다.
 * data가 여러 줄이면 '\n'으로 이어 붙인다.
 */
final class AiSseEventReader {

    private static final String DEFAULT_EVENT = "message";

    private AiSseEventReader() {
    }

    /** onEvent가 false를 돌려주면 그 자리에서 읽기를 멈춘다. */
    static void read(Reader source, Predicate<AiSseEventDto> onEvent) throws IOException {
        BufferedReader reader = source instanceof BufferedReader br ? br : new BufferedReader(source);
        String id = null;
        String event = null;
        StringBuilder data = null;

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (data != null) {
                    AiSseEventDto dispatched = new AiSseEventDto(id, event == null ? DEFAULT_EVENT : event, data.toString());
                    if (!onEvent.test(dispatched)) {
                        return;
                    }
                }
                // 중계용이라 이벤트에 실제로 붙어 온 id만 넘긴다(브라우저 EventSource처럼 이전 id를 이어받지 않는다).
                // id 없는 이벤트를 앱에 id 없이 보내야 앱의 Last-Event-ID가 마지막 실제 id로 남는다.
                id = null;
                event = null;
                data = null;
                continue;
            }
            if (line.startsWith(":")) {
                continue;
            }

            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }

            switch (field) {
                case "id" -> id = value;
                case "event" -> event = value;
                case "data" -> {
                    if (data == null) {
                        data = new StringBuilder(value);
                    } else {
                        data.append('\n').append(value);
                    }
                }
                default -> {
                    // retry 등 나머지 필드는 Spring이 쓰지 않는다.
                }
            }
        }
    }
}
