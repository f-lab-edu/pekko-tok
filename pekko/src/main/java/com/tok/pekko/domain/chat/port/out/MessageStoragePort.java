package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.chat.model.ChatChannelReaderActor.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.model.ChatMessage;
import org.apache.pekko.actor.typed.ActorRef;

public interface MessageStoragePort {

    void store(ChatMessage message);

    void findHistory(long messageSequence, int size, ActorRef<ChatChannelReaderCommand> replyTo);
}
