package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.global.common.CborSerializable;
import com.tok.pekko.domain.chat.model.ChatMessage;
import java.time.LocalDateTime;

public interface ChatChannelReaderProtocol {

    interface ChatChannelReaderCommand extends CborSerializable { }

    record SyncNewMessage(ChatMessage message) implements ChatChannelReaderCommand { }
    record SyncDeletion(Long messageId) implements ChatChannelReaderCommand { }
    record SyncUpdate(Long messageId, String updatedMessage, LocalDateTime updatedAt) implements ChatChannelReaderCommand { }
    record RequestHistory(long messageSequence, int size) implements ChatChannelReaderCommand { }
    record Shutdown() implements ChatChannelReaderCommand { }
}
