package com.tok.pekko.domain.chat.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelEntityChatMessagesTest {

    @Test
    void 빈_ChatMessages를_생성한다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();

        assertThat(channelEntityChatMessages.getRecentMessages(10)).isEmpty();
    }

    @Test
    void 메시지를_추가한다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage message = new ChatMessage(1L, 10L, 100L, 1000L, "Test", timestamp, timestamp);

        channelEntityChatMessages.add(message);

        assertThat(channelEntityChatMessages.getRecentMessages(10)).hasSize(1)
                                                                   .contains(message);
    }

    @Test
    void null_메시지는_추가할_수_없다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();

        assertThatThrownBy(() -> channelEntityChatMessages.add(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메시지는 null 일 수 없습니다.");
    }

    @Test
    void MAX_SIZE를_초과하면_가장_오래된_메시지가_제거된다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage firstMessage = new ChatMessage(1L, 10L, 100L, 1000L, "First", timestamp, timestamp);
        channelEntityChatMessages.add(firstMessage);

        for (long i = 2; i <= 101; i++) {
            LocalDateTime ts = LocalDateTime.now();
            channelEntityChatMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "Message " + i, ts, ts));
        }

        List<ChatMessage> recentMessages = channelEntityChatMessages.getRecentMessages(100);
        assertAll(
                () -> assertThat(recentMessages).hasSize(100),
                () -> assertThat(recentMessages).doesNotContain(firstMessage)
        );
    }

    @Test
    void 특정_메시지_시퀀스_번호_이전의_메시지_히스토리를_조회한다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        channelEntityChatMessages.add(new ChatMessage(5L, 50L, 500L, 5000L, "Message 5", timestamp1, timestamp1));
        channelEntityChatMessages.add(new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", timestamp2, timestamp2));
        channelEntityChatMessages.add(new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", timestamp3, timestamp3));

        List<ChatMessage> history = channelEntityChatMessages.getHistory(4000L, 10);

        assertAll(
                () -> assertThat(history).hasSize(2),
                () -> assertThat(history).allMatch(message -> message.orderSequence() < 4000L)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 메시지_히스토리_조회_시_개수가_0_이하라면_히스토리를_조회할_수_없다(int invalidSize) {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();

        assertThatThrownBy(() -> channelEntityChatMessages.getHistory(10L, invalidSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회하려는 메시지 개수는 양수여야 합니다.");
    }

    @Test
    void 메시지_히스토리_조회_시_개수가_MAX_SIZE를_초과하면_히스토리를_조회할_수_없다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();

        assertThatThrownBy(() -> channelEntityChatMessages.getHistory(10L, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회하려는 메시지 개수는 100개를 넘을 수 없습니다.");
    }

    @Test
    void 지정한_개수만큼_최근_메시지를_조회한다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        ChatMessage old = new ChatMessage(1L, 10L, 100L, 1000L, "Old", timestamp1, timestamp1);
        ChatMessage recent2 = new ChatMessage(2L, 20L, 200L, 2000L, "Recent 2", timestamp2, timestamp2);
        ChatMessage recent1 = new ChatMessage(3L, 30L, 300L, 3000L, "Recent 1", timestamp3, timestamp3);

        channelEntityChatMessages.add(old);
        channelEntityChatMessages.add(recent2);
        channelEntityChatMessages.add(recent1);

        List<ChatMessage> recentMessages = channelEntityChatMessages.getRecentMessages(2);

        assertAll(
                () -> assertThat(recentMessages).hasSize(2),
                () -> assertThat(recentMessages).containsExactly(recent1, recent2)
        );
    }

    @Test
    void 특정_메시지_시퀀스_번호_이후의_메시지를_조회한다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        channelEntityChatMessages.add(new ChatMessage(5L, 50L, 500L, 5000L, "Message 5", timestamp1, timestamp1));
        channelEntityChatMessages.add(new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", timestamp2, timestamp2));
        channelEntityChatMessages.add(new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", timestamp3, timestamp3));

        List<ChatMessage> messagesAfter = channelEntityChatMessages.getMessagesAfter(2000L);

        assertAll(
                () -> assertThat(messagesAfter).hasSize(2),
                () -> assertThat(messagesAfter).allMatch(message -> message.orderSequence() > 2000L)
        );
    }

    @Test
    void 기존과_독립적인_복사본을_초기화한다() {
        ChannelEntityChatMessages original = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Original", timestamp1, timestamp1);
        original.add(message1);

        ChannelEntityChatMessages copy = original.deepCopy();
        copy.add(new ChatMessage(2L, 20L, 200L, 2000L, "Copy Only", timestamp2, timestamp2));

        assertAll(
                () -> assertThat(original.getRecentMessages(10)).hasSize(1),
                () -> assertThat(copy.getRecentMessages(10)).hasSize(2)
        );
    }

    @Test
    void 빈_메시지_리스트로_동기화하면_아무_일도_일어나지_않는다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage existing = new ChatMessage(1L, 10L, 100L, 1000L, "Existing", timestamp, timestamp);
        channelEntityChatMessages.add(existing);

        channelEntityChatMessages.syncMessages(List.of());

        assertAll(
                () -> assertThat(channelEntityChatMessages.getRecentMessages(10)).hasSize(1),
                () -> assertThat(channelEntityChatMessages.getRecentMessages(10)).containsExactly(existing)
        );
    }

    @Test
    void 새로운_메시지를_동기화하면_messageSequence_순서대로_정렬된다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        List<ChatMessage> newMessages = List.of(
                new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", timestamp1, timestamp1),
                new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", timestamp2, timestamp2),
                new ChatMessage(5L, 50L, 500L, 5000L, "Message 5", timestamp3, timestamp3)
        );

        channelEntityChatMessages.syncMessages(newMessages);

        List<ChatMessage> result = channelEntityChatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(3),
                () -> assertThat(result)
                        .extracting(ChatMessage::orderSequence)
                        .isSortedAccordingTo(Comparator.reverseOrder()),
                () -> assertThat(result.get(0).orderSequence()).isEqualTo(5000L),
                () -> assertThat(result.get(1).orderSequence()).isEqualTo(3000L),
                () -> assertThat(result.get(2).orderSequence()).isEqualTo(1000L)
        );
    }

    @Test
    void 기존_메시지와_새_메시지를_동기화하면_모두_순서대로_병합된다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        LocalDateTime timestamp4 = LocalDateTime.now();
        channelEntityChatMessages.add(new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", timestamp1, timestamp1));
        channelEntityChatMessages.add(new ChatMessage(6L, 60L, 600L, 6000L, "Message 6", timestamp2, timestamp2));

        List<ChatMessage> newMessages = List.of(
                new ChatMessage(4L, 40L, 400L, 4000L, "Message 4", timestamp3, timestamp3),
                new ChatMessage(8L, 80L, 800L, 8000L, "Message 8", timestamp4, timestamp4)
        );

        channelEntityChatMessages.syncMessages(newMessages);

        List<ChatMessage> result = channelEntityChatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(4),
                () -> assertThat(result)
                        .extracting(ChatMessage::orderSequence)
                        .containsExactly(8000L, 6000L, 4000L, 2000L)
        );
    }

    @Test
    void 동기화_후_MAX_SIZE를_초과하면_가장_오래된_메시지가_제거된다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        for (long i = 1; i <= 50; i++) {
            LocalDateTime timestamp = LocalDateTime.now();
            channelEntityChatMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "Message " + i, timestamp, timestamp));
        }

        List<ChatMessage> newMessages = new ArrayList<>();
        for (long i = 51; i <= 105; i++) {
            LocalDateTime timestamp = LocalDateTime.now();
            newMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "Message " + i, timestamp, timestamp));
        }

        channelEntityChatMessages.syncMessages(newMessages);

        List<ChatMessage> result = channelEntityChatMessages.getRecentMessages(100);
        assertAll(
                () -> assertThat(result).hasSize(100),
                () -> assertThat(result.get(0).orderSequence()).isEqualTo(105000L),
                () -> assertThat(result.get(99).orderSequence()).isEqualTo(6000L),
                () -> assertThat(result).noneMatch(message -> message.orderSequence() < 6000L)
        );
    }

    @Test
    void 동기화_시_중복된_messageId를_가진_메시지는_제거된다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        ChatMessage existing = new ChatMessage(1L, 10L, 100L, 1000L, "Existing", timestamp1, timestamp1);
        channelEntityChatMessages.add(existing);

        List<ChatMessage> newMessages = List.of(
                new ChatMessage(1L, 10L, 100L, 1000L, "Updated", timestamp2, timestamp2),
                new ChatMessage(2L, 20L, 200L, 2000L, "New Message", timestamp3, timestamp3)
        );

        channelEntityChatMessages.syncMessages(newMessages);

        List<ChatMessage> result = channelEntityChatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result)
                        .extracting(ChatMessage::messageId)
                        .containsExactly(2L, 1L),
                () -> assertThat(result.get(1).message()).isEqualTo("Updated")
        );
    }

    @Test
    void 동기화_시_여러_중복_메시지가_있어도_마지막_메시지만_유지된다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        LocalDateTime timestamp4 = LocalDateTime.now();

        List<ChatMessage> newMessages = List.of(
                new ChatMessage(1L, 10L, 100L, 1000L, "First", timestamp1, timestamp1),
                new ChatMessage(1L, 10L, 100L, 1000L, "Second", timestamp2, timestamp2),
                new ChatMessage(1L, 10L, 100L, 1000L, "Third", timestamp3, timestamp3),
                new ChatMessage(2L, 20L, 200L, 2000L, "Unique", timestamp4, timestamp4)
        );

        channelEntityChatMessages.syncMessages(newMessages);

        List<ChatMessage> result = channelEntityChatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result)
                        .extracting(ChatMessage::messageId)
                        .containsExactly(2L, 1L),
                () -> assertThat(result)
                        .filteredOn(message -> message.messageId().equals(1L))
                        .extracting(ChatMessage::message)
                        .containsExactly("Third")
        );
    }

    @Test
    void 동기화_시_중복_제거_후_정렬과_크기_제한이_올바르게_적용된다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        for (long i = 1; i <= 50; i++) {
            LocalDateTime timestamp = LocalDateTime.now();
            channelEntityChatMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "Message " + i, timestamp, timestamp));
        }

        List<ChatMessage> newMessages = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            LocalDateTime timestamp = LocalDateTime.now();
            newMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "Updated " + i, timestamp, timestamp));
        }
        for (long i = 51; i <= 60; i++) {
            LocalDateTime timestamp = LocalDateTime.now();
            newMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "New Message " + i, timestamp, timestamp));
        }

        channelEntityChatMessages.syncMessages(newMessages);

        List<ChatMessage> result = channelEntityChatMessages.getRecentMessages(100);
        assertAll(
                () -> assertThat(result).hasSize(60),
                () -> assertThat(result)
                        .extracting(ChatMessage::orderSequence)
                        .isSortedAccordingTo(Comparator.reverseOrder()),
                () -> assertThat(result.get(0).orderSequence()).isEqualTo(60000L),
                () -> assertThat(result)
                        .filteredOn(message -> message.messageId() <= 10L)
                        .allMatch(message -> message.message().startsWith("Updated"))
        );
    }

    @Test
    void 메시지를_삭제한다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", timestamp1, timestamp1);
        ChatMessage message2 = new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", timestamp2, timestamp2);
        ChatMessage message3 = new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", timestamp3, timestamp3);

        channelEntityChatMessages.add(message1);
        channelEntityChatMessages.add(message2);
        channelEntityChatMessages.add(message3);

        ChatMessage deleted = channelEntityChatMessages.delete(2L);

        List<ChatMessage> result = channelEntityChatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(deleted).isEqualTo(message2),
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result).containsExactly(message3, message1),
                () -> assertThat(result).doesNotContain(message2)
        );
    }

    @Test
    void 첫번째_메시지를_삭제한다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", timestamp1, timestamp1);
        ChatMessage message2 = new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", timestamp2, timestamp2);
        ChatMessage message3 = new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", timestamp3, timestamp3);

        channelEntityChatMessages.add(message1);
        channelEntityChatMessages.add(message2);
        channelEntityChatMessages.add(message3);

        ChatMessage deleted = channelEntityChatMessages.delete(3L);

        List<ChatMessage> result = channelEntityChatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(deleted).isEqualTo(message3),
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result).containsExactly(message2, message1)
        );
    }

    @Test
    void 마지막_메시지를_삭제한다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", timestamp1, timestamp1);
        ChatMessage message2 = new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", timestamp2, timestamp2);
        ChatMessage message3 = new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", timestamp3, timestamp3);

        channelEntityChatMessages.add(message1);
        channelEntityChatMessages.add(message2);
        channelEntityChatMessages.add(message3);

        ChatMessage deleted = channelEntityChatMessages.delete(1L);

        List<ChatMessage> result = channelEntityChatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(deleted).isEqualTo(message1),
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result).containsExactly(message3, message2)
        );
    }

    @Test
    void 존재하지_않는_메시지는_삭제할_수_없다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage message = new ChatMessage(1L, 10L, 100L, 1000L, "Message", timestamp, timestamp);
        channelEntityChatMessages.add(message);

        assertThatThrownBy(() -> channelEntityChatMessages.delete(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 메시지입니다: 999");
    }

    @Test
    void 메시지_ID로_null을_전달하면_메시지를_삭제할_수_없다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();

        assertThatThrownBy(() -> channelEntityChatMessages.delete(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메시지 ID는 null 일 수 없습니다.");
    }

    @Test
    void 모든_메시지를_삭제할_수_있다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", timestamp1, timestamp1);
        ChatMessage message2 = new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", timestamp2, timestamp2);

        channelEntityChatMessages.add(message1);
        channelEntityChatMessages.add(message2);

        ChatMessage deleted1 = channelEntityChatMessages.delete(1L);
        ChatMessage deleted2 = channelEntityChatMessages.delete(2L);

        assertAll(
                () -> assertThat(deleted1).isEqualTo(message1),
                () -> assertThat(deleted2).isEqualTo(message2),
                () -> assertThat(channelEntityChatMessages.getRecentMessages(10)).isEmpty()
        );
    }

    @Test
    void 메시지_히스토리_조회_시_삭제된_메시지는_포함되지_않는다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        LocalDateTime timestamp4 = LocalDateTime.now();
        channelEntityChatMessages.add(new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", timestamp1, timestamp1));
        channelEntityChatMessages.add(new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", timestamp2, timestamp2));
        channelEntityChatMessages.add(new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", timestamp3, timestamp3));
        channelEntityChatMessages.add(new ChatMessage(4L, 40L, 400L, 4000L, "Message 4", timestamp4, timestamp4));

        ChatMessage deleted = channelEntityChatMessages.delete(2L);
        List<ChatMessage> history = channelEntityChatMessages.getHistory(3500L, 10);

        assertAll(
                () -> assertThat(deleted.messageId()).isEqualTo(2L),
                () -> assertThat(history).hasSize(2),
                () -> assertThat(history)
                        .extracting(ChatMessage::messageId)
                        .containsExactly(3L, 1L)
        );
    }

    @Test
    void 특정_시점_이후_메시지_조회_시_삭제된_메시지는_포함되지_않는다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        channelEntityChatMessages.add(new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", timestamp1, timestamp1));
        channelEntityChatMessages.add(new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", timestamp2, timestamp2));
        channelEntityChatMessages.add(new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", timestamp3, timestamp3));

        ChatMessage deleted = channelEntityChatMessages.delete(2L);
        List<ChatMessage> messagesAfter = channelEntityChatMessages.getMessagesAfter(1500L);

        assertAll(
                () -> assertThat(deleted.messageId()).isEqualTo(2L),
                () -> assertThat(messagesAfter).hasSize(1),
                () -> assertThat(messagesAfter)
                        .extracting(ChatMessage::messageId)
                        .containsExactly(3L)
        );
    }

    @Test
    void 메시지를_수정한다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage original = new ChatMessage(1L, 10L, 100L, 1000L, "Original", timestamp, timestamp);
        channelEntityChatMessages.add(original);

        ChatMessage updated = channelEntityChatMessages.update(1L, "Updated", LocalDateTime.now());

        List<ChatMessage> result = channelEntityChatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(updated.message()).isEqualTo("Updated"),
                () -> assertThat(updated.messageId()).isEqualTo(1L),
                () -> assertThat(updated.orderSequence()).isEqualTo(1000L),
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).message()).isEqualTo("Updated"),
                () -> assertThat(result.get(0).messageId()).isEqualTo(1L),
                () -> assertThat(result.get(0).orderSequence()).isEqualTo(1000L)
        );
    }

    @Test
    void 존재하지_않는_메시지를_수정할_수_없다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage message = new ChatMessage(1L, 10L, 100L, 1000L, "Message", timestamp, timestamp);
        channelEntityChatMessages.add(message);

        assertThatThrownBy(() -> channelEntityChatMessages.update(999L, "Updated", LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 채팅 메시지입니다.");
    }

    @Test
    void null_메시지로는_수정할_수_없다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage message = new ChatMessage(1L, 10L, 100L, 1000L, "Message", timestamp, timestamp);

        channelEntityChatMessages.add(message);

        assertThatThrownBy(() -> channelEntityChatMessages.update(1L, null, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메시지는 비어 있을 수 없습니다.");
    }

    @Test
    void 채팅_히스토리_조회_시_수정된_메시지가_올바르게_조회된다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        channelEntityChatMessages.add(new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", timestamp1, timestamp1));
        channelEntityChatMessages.add(new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", timestamp2, timestamp2));
        channelEntityChatMessages.add(new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", timestamp3, timestamp3));

        ChatMessage updated = channelEntityChatMessages.update(2L, "Updated Message 2", LocalDateTime.now());
        List<ChatMessage> history = channelEntityChatMessages.getHistory(2500L, 10);

        assertAll(
                () -> assertThat(updated.message()).isEqualTo("Updated Message 2"),
                () -> assertThat(updated.messageId()).isEqualTo(2L),
                () -> assertThat(history).hasSize(2),
                () -> assertThat(history)
                        .extracting(ChatMessage::messageId)
                        .containsExactly(2L, 1L),
                () -> assertThat(history.get(0).message()).isEqualTo("Updated Message 2")
        );
    }

    @Test
    void 채팅_메시지_복제_시_수정된_메시지가_올바르게_동기화된다() {
        ChannelEntityChatMessages channelEntityChatMessages = new ChannelEntityChatMessages();
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage message = new ChatMessage(1L, 10L, 100L, 1000L, "Original", timestamp, timestamp);
        channelEntityChatMessages.add(message);

        ChatMessage updated = channelEntityChatMessages.update(1L, "Updated", LocalDateTime.now());

        ChannelEntityChatMessages copy = channelEntityChatMessages.deepCopy();

        List<ChatMessage> result = copy.getRecentMessages(10);
        assertAll(
                () -> assertThat(updated.message()).isEqualTo("Updated"),
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).message()).isEqualTo("Updated")
        );
    }
}
