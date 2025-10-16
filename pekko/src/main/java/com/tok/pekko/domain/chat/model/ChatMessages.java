package com.tok.pekko.domain.chat.model;

import java.util.List;
import java.util.Deque;
import java.util.LinkedList;

public class ChatMessages {

    private static final int MAX_SIZE = 100;

    private final Deque<ChatMessage> messages;

    public ChatMessages() {
        this.messages = new LinkedList<>();
    }

    public ChatMessages(List<ChatMessage> messageList) {
        if (messageList == null || messageList.isEmpty()) {
            throw new IllegalArgumentException("관리할 메시지는 비어 있을 수 없습니다.");
        }

        this.messages = new LinkedList<>(messageList);
    }

    public ChatMessages deepCopy() {
        ChatMessages copy = new ChatMessages();

        copy.messages.addAll(this.messages);

        return copy;
    }

    public void add(ChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("메시지는 null 일 수 없습니다.");
        }

        messages.addFirst(message);

        if (messages.size() > MAX_SIZE) {
            messages.removeLast();
        }
    }

    public List<ChatMessage> getHistory(long beforeMessageSequence, int size) {
        validateSize(size);

        return messages.stream()
                       .filter(message -> message.messageSequence() < beforeMessageSequence)
                       .limit(size)
                       .toList();
    }

    public List<ChatMessage> getRecentMessages(int size) {
        validateSize(size);

        return messages.stream()
                       .limit(size)
                       .toList();
    }

    public List<ChatMessage> getMessagesAfter(long afterMessageSequence) {
        return messages.stream()
                       .filter(message -> message.messageSequence() > afterMessageSequence)
                       .toList();
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
