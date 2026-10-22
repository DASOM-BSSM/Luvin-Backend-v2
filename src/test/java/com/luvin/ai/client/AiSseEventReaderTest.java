package com.luvin.ai.client;

import com.luvin.ai.client.dto.AiSseEventDto;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiSseEventReaderTest {

    @Test
    void 빈_줄마다_이벤트_하나로_자르고_주석과_retry는_무시한다() throws IOException {
        String body = ": connected\n"
                + "retry: 3000\n"
                + "id: 1\nevent: segment_start\ndata: {\"segment\":\"scene\"}\n\n"
                + "id:2\r\nevent:delta\r\ndata:{\"segment\":\"scene\",\"text\":\"{\\\"a\\\":\"}\r\n\r\n"
                + "data: 첫째 줄\ndata: 둘째 줄\n\n";

        List<AiSseEventDto> events = readAll(body);

        assertEquals(List.of(
                new AiSseEventDto("1", "segment_start", "{\"segment\":\"scene\"}"),
                new AiSseEventDto("2", "delta", "{\"segment\":\"scene\",\"text\":\"{\\\"a\\\":\"}"),
                new AiSseEventDto(null, "message", "첫째 줄\n둘째 줄")
        ), events);
    }

    @Test
    void data가_없는_블록과_끝_빈_줄이_없는_마지막_블록은_내보내지_않는다() throws IOException {
        String body = "id: 1\nevent: ping\n\n"
                + "id: 2\nevent: delta\ndata: 끝나지 않은 이벤트";

        assertEquals(List.of(), readAll(body));
    }

    @Test
    void onEvent가_false를_돌려주면_더_읽지_않는다() throws IOException {
        String body = "event: completed\ndata: {}\n\nevent: delta\ndata: {}\n\n";
        List<AiSseEventDto> events = new ArrayList<>();

        AiSseEventReader.read(new StringReader(body), e -> {
            events.add(e);
            return false;
        });

        assertEquals(1, events.size());
        assertEquals("completed", events.get(0).event());
    }

    private List<AiSseEventDto> readAll(String body) throws IOException {
        List<AiSseEventDto> events = new ArrayList<>();
        AiSseEventReader.read(new StringReader(body), e -> {
            events.add(e);
            return true;
        });
        return events;
    }
}
