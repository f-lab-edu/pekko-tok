package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.chat.common.ActorCommand;
import com.tok.pekko.domain.chat.model.ChatMessage;
import java.util.List;

public interface ClientSessionProtocol {

    interface ClientSessionCommand extends ActorCommand { }

    record DeliverCommand(ChatMessage message) implements ClientSessionCommand { }
    record DeliverHistory(List<ChatMessage> messages) implements ClientSessionCommand { }
    record Shutdown() implements ClientSessionCommand { }
}
