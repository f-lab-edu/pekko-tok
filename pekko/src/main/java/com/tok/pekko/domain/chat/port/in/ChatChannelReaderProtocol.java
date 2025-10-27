package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.global.common.CborSerializable;
import com.tok.pekko.domain.chat.model.ChatMessage;

public interface ChatChannelReaderProtocol {

    interface ChatChannelReaderCommand extends CborSerializable { }

    record SyncNewCommand(ChatMessage message) implements ChatChannelReaderCommand { }
    record SyncDeletion(Long messageId) implements ChatChannelReaderCommand { }
    record SyncUpdate(Long messageId, String updatedMessage) implements ChatChannelReaderCommand { }
    record RequestHistory(long messageSequence, int size) implements ChatChannelReaderCommand { }
    record Shutdown() implements ChatChannelReaderCommand { }
}
