package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.common.CborSerializable;
import com.tok.pekko.domain.chat.model.ChatMessage;
import java.util.List;

public interface ChatChannelReaderProtocol {

    interface ChatChannelReaderCommand extends CborSerializable { }

    record SyncNewCommand(ChatMessage message) implements ChatChannelReaderCommand { }
    record RequestHistory(long messageSequence, int size) implements ChatChannelReaderCommand { }
    record HistoryLoaded(List<ChatMessage> history) implements ChatChannelReaderCommand { }
    record Shutdown() implements ChatChannelReaderCommand { }
}
