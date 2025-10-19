package com.tok.pekko.domain.chat.model;

import com.tok.pekko.global.common.ActorThreadSafe;
import java.util.List;
import java.util.Deque;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

public class ChatMessages {

    private static final int MAX_SIZE = 100;

    private final Deque<ChatMessage> messages;

    public ChatMessages() {
        this.messages = new LinkedList<>();
    }

    public ChatMessages deepCopy() {
        ChatMessages copy = new ChatMessages();
        copy.messages.addAll(this.messages);
        return copy;
    }

    @ActorThreadSafe
    public void add(ChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("메시지는 null 일 수 없습니다.");
        }

        messages.addFirst(message);

        if (messages.size() > MAX_SIZE) {
            messages.removeLast();
        }
    }

    @ActorThreadSafe
    public void syncMessages(List<ChatMessage> newMessages) {
        if (newMessages == null) {
            throw new IllegalArgumentException("메시지는 null 일 수 없습니다.");
        }

        if (newMessages.isEmpty()) {
            return;
        }

        List<ChatMessage> allMessages = new ArrayList<>(messages);

        allMessages.addAll(newMessages);

        List<ChatMessage> uniqueMessages = removeDuplicates(allMessages);

        uniqueMessages.sort(Comparator.comparingLong(ChatMessage::messageSequence).reversed());

        if (uniqueMessages.size() > MAX_SIZE) {
            uniqueMessages = uniqueMessages.subList(0, MAX_SIZE);
        }

        messages.clear();
        messages.addAll(uniqueMessages);
    }

    private List<ChatMessage> removeDuplicates(List<ChatMessage> messageList) {
        Map<Long, ChatMessage> messageIdMap = messageList.stream()
                                                         .collect(
                                                                 Collectors.toMap(
                                                                         ChatMessage::messageId,
                                                                         message -> message,
                                                                         (existing, replacement) -> replacement
                                                                 )
                                                         );

        return new ArrayList<>(messageIdMap.values());
    }

    @ActorThreadSafe
    public List<ChatMessage> getHistory(long beforeMessageSequence, int size) {
        validateSize(size);

        return messages.stream()
                       .filter(message -> message.messageSequence() < beforeMessageSequence)
                       .limit(size)
                       .toList();
    }

    @ActorThreadSafe
    public List<ChatMessage> getRecentMessages(int size) {
        validateSize(size);

        return messages.stream()
                       .limit(size)
                       .toList();
    }

    @ActorThreadSafe
    public List<ChatMessage> getMessagesAfter(long afterMessageSequence) {
        return messages.stream()
                       .filter(message -> message.messageSequence() > afterMessageSequence)
                       .toList();
    }

    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    private void validateSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("조회하려는 메시지 개수는 양수여야 합니다.");
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("조회하려는 메시지 개수는 " + MAX_SIZE + "개를 넘을 수 없습니다.");
        }
    }
}
