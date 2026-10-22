package com.luvin.ai.client.story;

import java.util.List;
import java.util.UUID;

/** AI가 쓴 블록 하나. id는 요청한 블록 id와 같아야 한다. */
public record StoryWrittenBlockDto(
        String id,
        /** transition만 */
        String transitionText,
        /** messenger만 */
        String roomName,
        List<Message> messages
) {

    /** read/typing/deletedAfterSend는 messenger 연출용이고 다른 블록에서는 null이다. */
    public record Message(UUID speakerId, String text, Boolean read, Boolean typing, Boolean deletedAfterSend) {
    }
}
