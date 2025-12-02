package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.global.common.ActorThreadSafe;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ActorThreadSafe
public class ChannelReaderChatMessages {

    private static final int MAX_SIZE = 100;

    private final ChatMessageNode head;
    private final ChatMessageNode tail;
    private final Map<Long, ChatMessageNode> messageIdMap;
    private int size;

    public ChannelReaderChatMessages() {
        this.head = new ChatMessageNode(null);
        this.tail = new ChatMessageNode(null);
        this.head.next = tail;
        this.tail.prev = head;
        this.messageIdMap = new HashMap<>();
        this.size = 0;
    }

    public void add(ChatMessage message) {
        addNewMessageNode(message);
        evictOldest();
    }

    public void delete(Long messageId) {
        findChatMessageNode(messageId)
                .ifPresent(node -> {
                    removeNode(node);
                    messageIdMap.remove(messageId);
                    size--;
                });
    }

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

    public void update(Long messageId, String updatedMessage, LocalDateTime updatedAt) {
        ChatMessageNode node = messageIdMap.get(messageId);

        if (node != null) {
            node.data = node.data.updateMessage(updatedMessage, updatedAt);
        }
    }

    public List<ChatMessage> getHistory(long beforeMessageSequence, int size) {
        if (size <= 0) {
            return List.of();
        }
        if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }

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

    public List<ChatMessage> getRecentMessages(int size) {
        if (size <= 0) {
            return List.of();
        }
        if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }

        List<ChatMessage> result = new ArrayList<>();
        ChatMessageNode current = head.next;

        while (current != tail && result.size() < size) {
            result.add(current.data);
            current = current.next;
        }

        return result;
    }

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

    public ChatMessage getMessage(Long messageId) {
        return messageIdMap.get(messageId)
                .data;
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

    private Optional<ChatMessageNode> findChatMessageNode(Long messageId) {
        ChatMessageNode node = messageIdMap.get(messageId);

        return Optional.ofNullable(node);
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
