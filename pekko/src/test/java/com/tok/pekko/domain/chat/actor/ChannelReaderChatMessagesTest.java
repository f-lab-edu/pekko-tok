package com.tok.pekko.domain.chat.actor;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelReaderChatMessagesTest {

    @Test
    void 새로운_메시지를_추가할_수_있다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage message = ChatMessage.create(1L, 100L, 1L, "Test Message", timestamp, timestamp);

        // when
        messages.add(message);

        // then
        List<ChatMessage> allMessages = messages.getMessages();
        assertAll(
                () -> assertThat(allMessages).hasSize(1),
                () -> assertThat(allMessages.get(0)).isEqualTo(message)
        );
    }

    @Test
    void 메시지_ID로_메시지를_삭제할_수_있다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage message = ChatMessage.create(1L, 100L, 1L, "Test Message", timestamp, timestamp);
        messages.add(message);

        // when
        messages.delete(message.messageId());

        // then
        assertThat(messages.getMessages()).isEmpty();
    }

    @Test
    void 메시지를_수정할_수_있다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage message = new ChatMessage(1L, 1L, 100L, 1L, "원본 메시지", timestamp, timestamp);
        messages.add(message);

        LocalDateTime updatedAt = LocalDateTime.now();

        // when
        messages.update(1L, "수정된 메시지", updatedAt);

        // then
        ChatMessage actual = messages.getMessage(1L);

        assertAll(
                () -> assertThat(actual.message()).isEqualTo("수정된 메시지"),
                () -> assertThat(actual.updatedAt()).isEqualTo(updatedAt)
        );
    }

    @Test
    void getHistory_size가_0이하이면_빈_리스트를_반환한다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();

        // when & then
        List<ChatMessage> history = messages.getHistory(1L, 0);

        assertThat(history).isEmpty();
    }

    @Test
    void getHistory_size가_MAX를_초과하면_MAX까지만_반환한다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();

        for (int i = 1; i <= 100; i++) {
            messages.add(ChatMessage.create(1L, (long) i, i, "Message " + i, timestamp, timestamp));
        }

        // when
        List<ChatMessage> history = messages.getHistory(200L, 150);

        // then
        assertAll(
                () -> assertThat(history).hasSize(100),
                () -> assertThat(history.get(0).orderSequence()).isEqualTo(100L),
                () -> assertThat(history.get(99).orderSequence()).isEqualTo(1L)
        );
    }

    @Test
    void getRecentMessages_size가_0이하이면_빈_리스트를_반환한다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();

        // when & then
        List<ChatMessage> recentMessages = messages.getRecentMessages(0);

        assertThat(recentMessages).isEmpty();
    }

    @Test
    void getRecentMessages_size가_MAX를_초과하면_MAX까지만_반환한다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();

        for (int i = 1; i <= 120; i++) {
            messages.add(ChatMessage.create(1L, (long) i, i, "Message " + i, timestamp, timestamp));
        }

        // when
        List<ChatMessage> recentMessages = messages.getRecentMessages(200);

        // then
        assertAll(
                () -> assertThat(recentMessages).hasSize(100),
                () -> assertThat(recentMessages.get(0).orderSequence()).isEqualTo(120L),
                () -> assertThat(recentMessages.get(99).orderSequence()).isEqualTo(21L)
        );
    }

    @Test
    void 특정_시퀀스_이전의_메시지_히스토리를_조회할_수_있다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();

        messages.add(ChatMessage.create(1L, 100L, 10L, "Message 10", timestamp, timestamp));
        messages.add(ChatMessage.create(1L, 101L, 20L, "Message 20", timestamp, timestamp));
        messages.add(ChatMessage.create(1L, 102L, 30L, "Message 30", timestamp, timestamp));

        // when
        List<ChatMessage> history = messages.getHistory(25L, 10);

        // then
        assertAll(
                () -> assertThat(history).hasSize(2),
                () -> assertThat(history.get(0).orderSequence()).isEqualTo(20L),
                () -> assertThat(history.get(1).orderSequence()).isEqualTo(10L)
        );
    }

    @Test
    void 최근_메시지를_지정한_개수만큼_조회할_수_있다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();

        messages.add(ChatMessage.create(1L, 100L, 10L, "Message 1", timestamp, timestamp));
        messages.add(ChatMessage.create(1L, 101L, 20L, "Message 2", timestamp, timestamp));
        messages.add(ChatMessage.create(1L, 102L, 30L, "Message 3", timestamp, timestamp));

        // when
        List<ChatMessage> recentMessages = messages.getRecentMessages(2);

        // then
        assertAll(
                () -> assertThat(recentMessages).hasSize(2),
                () -> assertThat(recentMessages.get(0).orderSequence()).isEqualTo(30L),
                () -> assertThat(recentMessages.get(1).orderSequence()).isEqualTo(20L)
        );
    }

    @Test
    void 특정_시퀀스_이후의_메시지를_조회할_수_있다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();

        messages.add(ChatMessage.create(1L, 100L, 10L, "Message 10", timestamp, timestamp));
        messages.add(ChatMessage.create(1L, 101L, 20L, "Message 20", timestamp, timestamp));
        messages.add(ChatMessage.create(1L, 102L, 30L, "Message 30", timestamp, timestamp));

        // when
        List<ChatMessage> messagesAfter = messages.getMessagesAfter(15L);

        // then
        assertAll(
                () -> assertThat(messagesAfter).hasSize(2),
                () -> assertThat(messagesAfter.get(0).orderSequence()).isEqualTo(30L),
                () -> assertThat(messagesAfter.get(1).orderSequence()).isEqualTo(20L)
        );
    }

    @Test
    void 메시지_ID로_특정_메시지를_조회할_수_있다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage message = ChatMessage.create(1L, 100L, 1L, "Test Message", timestamp, timestamp);
        messages.add(message);

        // when
        ChatMessage retrievedMessage = messages.getMessage(message.messageId());

        // then
        assertThat(retrievedMessage).isEqualTo(message);
    }

    @Test
    void 모든_메시지를_조회할_수_있다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();

        ChatMessage message1 = ChatMessage.create(1L, 100L, 10L, "Message 1", timestamp, timestamp);
        ChatMessage message2 = ChatMessage.create(1L, 101L, 20L, "Message 2", timestamp, timestamp);
        ChatMessage message3 = ChatMessage.create(1L, 102L, 30L, "Message 3", timestamp, timestamp);

        messages.add(message1);
        messages.add(message2);
        messages.add(message3);

        // when
        List<ChatMessage> allMessages = messages.getMessages();

        // then
        assertAll(
                () -> assertThat(allMessages).hasSize(3),
                () -> assertThat(allMessages).containsExactly(message3, message2, message1)
        );
    }

    @Test
    void 최대_크기를_초과하면_가장_오래된_메시지가_제거된다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();

        for (int i = 1; i <= 101; i++) {
            messages.add(ChatMessage.create(1L, (long) i, i, "Message " + i, timestamp, timestamp));
        }

        // when
        List<ChatMessage> allMessages = messages.getMessages();

        // then
        assertAll(
                () -> assertThat(allMessages).hasSize(100),
                () -> assertThat(allMessages.get(0).orderSequence()).isEqualTo(101L),
                () -> assertThat(allMessages.get(99).orderSequence()).isEqualTo(2L)
        );
    }

    @Test
    void syncMessages로_새로운_메시지_목록과_동기화할_수_있다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();

        ChatMessage oldMessage = new ChatMessage(1L, 100L, 100L, 10L, "Old Message", timestamp, timestamp);
        messages.add(oldMessage);

        List<ChatMessage> newMessages = List.of(
                new ChatMessage(2L, 1L, 101L, 20L, "New Message 1", timestamp, timestamp),
                new ChatMessage(3L, 1L, 102L, 30L, "New Message 2", timestamp, timestamp)
        );

        // when
        messages.syncMessages(newMessages);

        // then
        List<ChatMessage> allMessages = messages.getMessages();
        assertAll(
                () -> assertThat(allMessages).hasSize(3),
                () -> assertThat(allMessages.get(0).orderSequence()).isEqualTo(30L),
                () -> assertThat(allMessages.get(1).orderSequence()).isEqualTo(20L),
                () -> assertThat(allMessages.get(2).orderSequence()).isEqualTo(10L)
        );
    }

    @Test
    void syncMessages로_중복된_메시지는_제거되어_동기화된다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();

        ChatMessage originalMessage = new ChatMessage(1L, 1L, 100L, 10L, "Original", timestamp, timestamp);
        messages.add(originalMessage);

        ChatMessage updatedMessage = new ChatMessage(1L, 1L, 100L, 10L, "Updated", timestamp, timestamp);
        List<ChatMessage> newMessages = List.of(updatedMessage);

        // when
        messages.syncMessages(newMessages);

        // then
        List<ChatMessage> allMessages = messages.getMessages();
        assertAll(
                () -> assertThat(allMessages).hasSize(1),
                () -> assertThat(allMessages.get(0).message()).isEqualTo("Updated")
        );
    }

    @Test
    void syncMessages에_빈_리스트를_전달하면_아무_동작도_하지_않는다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();

        ChatMessage message = ChatMessage.create(1L, 100L, 10L, "Message", timestamp, timestamp);
        messages.add(message);

        // when
        messages.syncMessages(List.of());

        // then
        List<ChatMessage> allMessages = messages.getMessages();
        assertAll(
                () -> assertThat(allMessages).hasSize(1),
                () -> assertThat(allMessages.get(0)).isEqualTo(message)
        );
    }

    @Test
    void 여러_메시지를_추가하고_순서대로_조회할_수_있다() {
        // given
        ChannelReaderChatMessages messages = new ChannelReaderChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();

        ChatMessage message1 = ChatMessage.create(1L, 100L, 10L, "First", timestamp, timestamp);
        ChatMessage message2 = ChatMessage.create(1L, 101L, 20L, "Second", timestamp, timestamp);
        ChatMessage message3 = ChatMessage.create(1L, 102L, 30L, "Third", timestamp, timestamp);

        // when
        messages.add(message1);
        messages.add(message2);
        messages.add(message3);

        // then
        List<ChatMessage> allMessages = messages.getMessages();
        assertAll(
                () -> assertThat(allMessages).hasSize(3),
                () -> assertThat(allMessages.get(0).orderSequence()).isEqualTo(30L),
                () -> assertThat(allMessages.get(1).orderSequence()).isEqualTo(20L),
                () -> assertThat(allMessages.get(2).orderSequence()).isEqualTo(10L)
        );
    }
}
