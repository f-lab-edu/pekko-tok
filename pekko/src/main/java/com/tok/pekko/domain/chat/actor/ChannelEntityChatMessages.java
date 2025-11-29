package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.global.common.ActorThreadSafe;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChannelEntityChatMessages {

    private static final int MAX_SIZE = 100;

    private final ChatMessageNode head;
    private final ChatMessageNode tail;
    private final Map<Long, ChatMessageNode> messageIdMap;
    private int size;

    public ChannelEntityChatMessages() {
        this.head = new ChatMessageNode(null);
        this.tail = new ChatMessageNode(null);
        this.head.next = tail;
        this.tail.prev = head;
        this.messageIdMap = new HashMap<>();
        this.size = 0;
    }

    public ChannelEntityChatMessages deepCopy() {
        ChannelEntityChatMessages copy = new ChannelEntityChatMessages();
        ChatMessageNode current = this.head.next;

        while (current != this.tail) {
            copy.add(current.data);
            current = current.next;
        }

        return copy;
    }

    @ActorThreadSafe
    public void add(ChatMessage message) {
        validateChatMessage(message);
        addNewMessageNode(message);
        evictOldest();
    }

    @ActorThreadSafe
    public ChatMessage delete(Long messageId) {
        validateMessageId(messageId);

        ChatMessageNode node = findChatMessageNode(messageId);
        ChatMessage deletedMessage = node.data;

        removeNode(node);
        messageIdMap.remove(messageId);
        size--;

        return deletedMessage;
    }

    @ActorThreadSafe
    public void syncMessages(List<ChatMessage> newMessages) {
        if (newMessages.isEmpty()) {
            return;
        }

        List<ChatMessage> mergedMessages = mergeWithCurrentMessages(newMessages);
        List<ChatMessage> uniqueMessages = removeDuplicateMessages(mergedMessages);
        List<ChatMessage> sortedMessages = sortByMessageSequenceDescending(uniqueMessages);
        List<ChatMessage> limitedMessages = limitToMaxSize(sortedMessages);

        rebuildWithMessages(limitedMessages);
    }

    @ActorThreadSafe
    public ChatMessage update(Long messageId, String updatedMessage, LocalDateTime updatedAt) {
        ChatMessageNode node = messageIdMap.get(messageId);

        if (node == null) {
            throw new IllegalArgumentException("존재하지 않는 채팅 메시지입니다.");
        }

        node.data = node.data
                        .updateMessage(updatedMessage, updatedAt);
        return node.data;
    }

    @ActorThreadSafe
    public List<ChatMessage> getHistory(long beforeMessageSequence, int size) {
        validateSize(size);

        List<ChatMessage> result = new ArrayList<>();
        ChatMessageNode current = head.next;

        while (current != tail && result.size() < size) {
            if (current.data.orderSequence() < beforeMessageSequence) {
                result.add(current.data);
            }
            current = current.next;
        }

        return result;
    }

    @ActorThreadSafe
    public List<ChatMessage> getRecentMessages(int size) {
        validateSize(size);

        List<ChatMessage> result = new ArrayList<>();
        ChatMessageNode current = head.next;

        while (current != tail && result.size() < size) {
            result.add(current.data);
            current = current.next;
        }

        return result;
    }

    @ActorThreadSafe
    public List<ChatMessage> getMessagesAfter(long afterMessageSequence) {
        List<ChatMessage> result = new ArrayList<>();
        ChatMessageNode current = head.next;

        while (current != tail) {
            if (current.data.orderSequence() > afterMessageSequence) {
                result.add(current.data);
            }
            current = current.next;
        }

        return result;
    }

    public List<ChatMessage> getMessages() {
        List<ChatMessage> result = new ArrayList<>();
        ChatMessageNode current = head.next;

        while (current != tail) {
            result.add(current.data);
            current = current.next;
        }

        return result;
    }

    private void validateChatMessage(ChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("메시지는 null 일 수 없습니다.");
        }
    }

    private void validateMessageId(Long messageId) {
        if (messageId == null) {
            throw new IllegalArgumentException("메시지 ID는 null 일 수 없습니다.");
        }
    }

    private void validateSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("조회하려는 메시지 개수는 양수여야 합니다.");
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("조회하려는 메시지 개수는 " + MAX_SIZE + "개를 넘을 수 없습니다.");
        }
    }

    private void addNewMessageNode(ChatMessage message) {
        ChatMessageNode newNode = new ChatMessageNode(message);

        addNodeAfterHead(newNode);
        messageIdMap.put(message.messageId(), newNode);
        size++;
    }

    private void evictOldest() {
        if (size > MAX_SIZE) {
            ChatMessageNode oldest = tail.prev;

            removeNode(oldest);
            messageIdMap.remove(oldest.data.messageId());
            size--;
        }
    }

    private ChatMessageNode findChatMessageNode(Long messageId) {
        ChatMessageNode node = messageIdMap.get(messageId);

        if (node == null) {
            throw new IllegalArgumentException("존재하지 않는 메시지입니다: " + messageId);
        }

        return node;
    }

    private List<ChatMessage> mergeWithCurrentMessages(List<ChatMessage> newMessages) {
        List<ChatMessage> allMessages = getAllMessages();
        allMessages.addAll(newMessages);
        return allMessages;
    }

    private List<ChatMessage> removeDuplicateMessages(List<ChatMessage> messageList) {
        Map<Long, ChatMessage> distinctMessageIdMap = messageList.stream()
                                                                 .collect(
                                                                         Collectors.toMap(
                                                                                 ChatMessage::messageId,
                                                                                 message -> message,
                                                                                 (existing, replacement) -> replacement
                                                                         )
                                                                 );

        return new ArrayList<>(distinctMessageIdMap.values());
    }

    private List<ChatMessage> sortByMessageSequenceDescending(List<ChatMessage> messages) {
        messages.sort(Comparator.comparingLong(ChatMessage::orderSequence).reversed());
        return messages;
    }

    private List<ChatMessage> limitToMaxSize(List<ChatMessage> messages) {
        if (messages.size() > MAX_SIZE) {
            return messages.subList(0, MAX_SIZE);
        }
        return messages;
    }

    private void rebuildWithMessages(List<ChatMessage> messages) {
        clearNodeStatus();

        for (ChatMessage message : messages) {
            ChatMessageNode newNode = new ChatMessageNode(message);
            addNodeBeforeTail(newNode);
            messageIdMap.put(message.messageId(), newNode);
            size++;
        }
    }

    private void addNodeAfterHead(ChatMessageNode node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void addNodeBeforeTail(ChatMessageNode node) {
        node.prev = tail.prev;
        node.next = tail;
        tail.prev.next = node;
        tail.prev = node;
    }

    private void removeNode(ChatMessageNode node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void clearNodeStatus() {
        head.next = tail;
        tail.prev = head;
        messageIdMap.clear();
        size = 0;
    }

    private List<ChatMessage> getAllMessages() {
        List<ChatMessage> allMessages = new ArrayList<>();

        ChatMessageNode current = head.next;
        while (current != tail) {
            allMessages.add(current.data);
            current = current.next;
        }

        return allMessages;
    }

    private static class ChatMessageNode {

        ChatMessage data;
        ChatMessageNode prev;
        ChatMessageNode next;

        ChatMessageNode(ChatMessage data) {
            this.data = data;
        }
    }
}
