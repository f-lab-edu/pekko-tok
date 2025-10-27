package com.tok.pekko.domain.chat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatMessagesTest {

    @Test
    void 빈_ChatMessages를_생성한다() {
        ChatMessages chatMessages = new ChatMessages();

        assertThat(chatMessages.getRecentMessages(10)).isEmpty();
    }

    @Test
    void 메시지를_추가한다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message = new ChatMessage(1L, 10L, 100L, 1000L, "Test", LocalDateTime.now());

        chatMessages.add(message);

        assertThat(chatMessages.getRecentMessages(10)).hasSize(1)
                                                      .contains(message);
    }

    @Test
    void null_메시지를_추가하면_예외가_발생한다() {
        ChatMessages chatMessages = new ChatMessages();

        assertThatThrownBy(() -> chatMessages.add(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메시지는 null 일 수 없습니다.");
    }

    @Test
    void MAX_SIZE를_초과하면_가장_오래된_메시지가_제거된다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage firstMessage = new ChatMessage(1L, 10L, 100L, 1000L, "First", LocalDateTime.now());
        chatMessages.add(firstMessage);

        for (long i = 2; i <= 101; i++) {
            chatMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "Message " + i, LocalDateTime.now()));
        }

        List<ChatMessage> recentMessages = chatMessages.getRecentMessages(100);
        assertAll(
                () -> assertThat(recentMessages).hasSize(100),
                () -> assertThat(recentMessages).doesNotContain(firstMessage)
        );
    }

    @Test
    void 특정_메시지_시퀀스_번호_이전의_메시지_히스토리를_조회한다() {
        ChatMessages chatMessages = new ChatMessages();
        chatMessages.add(new ChatMessage(5L, 50L, 500L, 5000L, "Message 5", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now()));

        List<ChatMessage> history = chatMessages.getHistory(4000L, 10);

        assertAll(
                () -> assertThat(history).hasSize(2),
                () -> assertThat(history).allMatch(message -> message.messageSequence() < 4000L)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 메시지_히스토리_조회_시_개수가_0_이하면_예외가_발생한다(int invalidSize) {
        ChatMessages chatMessages = new ChatMessages();

        assertThatThrownBy(() -> chatMessages.getHistory(10L, invalidSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회하려는 메시지 개수는 양수여야 합니다.");
    }

    @Test
    void 메시지_히스토리_조회_시_개수가_MAX_SIZE를_초과하면_예외가_발생한다() {
        ChatMessages chatMessages = new ChatMessages();

        assertThatThrownBy(() -> chatMessages.getHistory(10L, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회하려는 메시지 개수는 100개를 넘을 수 없습니다.");
    }

    @Test
    void 지정한_개수만큼_최근_메시지를_조회한다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage old = new ChatMessage(1L, 10L, 100L, 1000L, "Old", LocalDateTime.now());
        ChatMessage recent2 = new ChatMessage(2L, 20L, 200L, 2000L, "Recent 2", LocalDateTime.now());
        ChatMessage recent1 = new ChatMessage(3L, 30L, 300L, 3000L, "Recent 1", LocalDateTime.now());

        chatMessages.add(old);
        chatMessages.add(recent2);
        chatMessages.add(recent1);

        List<ChatMessage> recentMessages = chatMessages.getRecentMessages(2);

        assertAll(
                () -> assertThat(recentMessages).hasSize(2),
                () -> assertThat(recentMessages).containsExactly(recent1, recent2)
        );
    }

    @Test
    void 특정_메시지_시퀀스_번호_이후의_메시지를_조회한다() {
        ChatMessages chatMessages = new ChatMessages();
        chatMessages.add(new ChatMessage(5L, 50L, 500L, 5000L, "Message 5", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now()));

        List<ChatMessage> messagesAfter = chatMessages.getMessagesAfter(2000L);

        assertAll(
                () -> assertThat(messagesAfter).hasSize(2),
                () -> assertThat(messagesAfter).allMatch(message -> message.messageSequence() > 2000L)
        );
    }

    @Test
    void 기존과_독립적인_복사본을_생성한다() {
        ChatMessages original = new ChatMessages();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Original", LocalDateTime.now());
        original.add(message1);

        ChatMessages copy = original.deepCopy();
        copy.add(new ChatMessage(2L, 20L, 200L, 2000L, "Copy Only", LocalDateTime.now()));

        assertAll(
                () -> assertThat(original.getRecentMessages(10)).hasSize(1),
                () -> assertThat(copy.getRecentMessages(10)).hasSize(2)
        );
    }

    @Test
    void 빈_메시지_리스트로_동기화하면_아무_일도_일어나지_않는다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage existing = new ChatMessage(1L, 10L, 100L, 1000L, "Existing", LocalDateTime.now());
        chatMessages.add(existing);

        chatMessages.syncMessages(List.of());

        assertAll(
                () -> assertThat(chatMessages.getRecentMessages(10)).hasSize(1),
                () -> assertThat(chatMessages.getRecentMessages(10)).containsExactly(existing)
        );
    }

    @Test
    void 새로운_메시지를_동기화하면_messageSequence_순서대로_정렬된다() {
        ChatMessages chatMessages = new ChatMessages();
        List<ChatMessage> newMessages = List.of(
                new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", LocalDateTime.now()),
                new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now()),
                new ChatMessage(5L, 50L, 500L, 5000L, "Message 5", LocalDateTime.now())
        );

        chatMessages.syncMessages(newMessages);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(3),
                () -> assertThat(result)
                        .extracting(ChatMessage::messageSequence)
                        .isSortedAccordingTo(Comparator.reverseOrder()),
                () -> assertThat(result.get(0).messageSequence()).isEqualTo(5000L),
                () -> assertThat(result.get(1).messageSequence()).isEqualTo(3000L),
                () -> assertThat(result.get(2).messageSequence()).isEqualTo(1000L)
        );
    }

    @Test
    void 기존_메시지와_새_메시지를_동기화하면_모두_순서대로_병합된다() {
        ChatMessages chatMessages = new ChatMessages();
        chatMessages.add(new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(6L, 60L, 600L, 6000L, "Message 6", LocalDateTime.now()));

        List<ChatMessage> newMessages = List.of(
                new ChatMessage(4L, 40L, 400L, 4000L, "Message 4", LocalDateTime.now()),
                new ChatMessage(8L, 80L, 800L, 8000L, "Message 8", LocalDateTime.now())
        );

        chatMessages.syncMessages(newMessages);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(4),
                () -> assertThat(result)
                        .extracting(ChatMessage::messageSequence)
                        .containsExactly(8000L, 6000L, 4000L, 2000L)
        );
    }

    @Test
    void 동기화_후_MAX_SIZE를_초과하면_가장_오래된_메시지가_제거된다() {
        ChatMessages chatMessages = new ChatMessages();
        for (long i = 1; i <= 50; i++) {
            chatMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "Message " + i, LocalDateTime.now()));
        }

        List<ChatMessage> newMessages = new java.util.ArrayList<>();
        for (long i = 51; i <= 105; i++) {
            newMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "Message " + i, LocalDateTime.now()));
        }

        chatMessages.syncMessages(newMessages);

        List<ChatMessage> result = chatMessages.getRecentMessages(100);
        assertAll(
                () -> assertThat(result).hasSize(100),
                () -> assertThat(result.get(0).messageSequence()).isEqualTo(105000L),
                () -> assertThat(result.get(99).messageSequence()).isEqualTo(6000L),
                () -> assertThat(result).noneMatch(message -> message.messageSequence() < 6000L)
        );
    }

    @Test
    void 동기화_시_중복된_messageId를_가진_메시지는_제거된다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage existing = new ChatMessage(1L, 10L, 100L, 1000L, "Existing", LocalDateTime.now());
        chatMessages.add(existing);

        List<ChatMessage> newMessages = List.of(
                new ChatMessage(1L, 10L, 100L, 1000L, "Updated", LocalDateTime.now()),
                new ChatMessage(2L, 20L, 200L, 2000L, "New Message", LocalDateTime.now())
        );

        chatMessages.syncMessages(newMessages);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
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
        ChatMessages chatMessages = new ChatMessages();

        List<ChatMessage> newMessages = List.of(
                new ChatMessage(1L, 10L, 100L, 1000L, "First", LocalDateTime.now()),
                new ChatMessage(1L, 10L, 100L, 1000L, "Second", LocalDateTime.now()),
                new ChatMessage(1L, 10L, 100L, 1000L, "Third", LocalDateTime.now()),
                new ChatMessage(2L, 20L, 200L, 2000L, "Unique", LocalDateTime.now())
        );

        chatMessages.syncMessages(newMessages);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
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
        ChatMessages chatMessages = new ChatMessages();
        for (long i = 1; i <= 50; i++) {
            chatMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "Message " + i, LocalDateTime.now()));
        }

        List<ChatMessage> newMessages = new java.util.ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            newMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "Updated " + i, LocalDateTime.now()));
        }
        for (long i = 51; i <= 60; i++) {
            newMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "New Message " + i, LocalDateTime.now()));
        }

        chatMessages.syncMessages(newMessages);

        List<ChatMessage> result = chatMessages.getRecentMessages(100);
        assertAll(
                () -> assertThat(result).hasSize(60),
                () -> assertThat(result)
                        .extracting(ChatMessage::messageSequence)
                        .isSortedAccordingTo(Comparator.reverseOrder()),
                () -> assertThat(result.get(0).messageSequence()).isEqualTo(60000L),
                () -> assertThat(result)
                        .filteredOn(message -> message.messageId() <= 10L)
                        .allMatch(message -> message.message().startsWith("Updated"))
        );
    }

    @Test
    void 메시지를_삭제한다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now());
        ChatMessage message2 = new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", LocalDateTime.now());
        ChatMessage message3 = new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", LocalDateTime.now());

        chatMessages.add(message1);
        chatMessages.add(message2);
        chatMessages.add(message3);

        chatMessages.delete(2L);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result).containsExactly(message3, message1),
                () -> assertThat(result).doesNotContain(message2)
        );
    }

    @Test
    void 첫번째_메시지를_삭제한다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now());
        ChatMessage message2 = new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", LocalDateTime.now());
        ChatMessage message3 = new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", LocalDateTime.now());

        chatMessages.add(message1);
        chatMessages.add(message2);
        chatMessages.add(message3);

        chatMessages.delete(3L);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result).containsExactly(message2, message1)
        );
    }

    @Test
    void 마지막_메시지를_삭제한다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now());
        ChatMessage message2 = new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", LocalDateTime.now());
        ChatMessage message3 = new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", LocalDateTime.now());

        chatMessages.add(message1);
        chatMessages.add(message2);
        chatMessages.add(message3);

        chatMessages.delete(1L);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result).containsExactly(message3, message2)
        );
    }

    @Test
    void 존재하지_않는_메시지를_삭제하면_예외가_발생한다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message = new ChatMessage(1L, 10L, 100L, 1000L, "Message", LocalDateTime.now());
        chatMessages.add(message);

        assertThatThrownBy(() -> chatMessages.delete(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 메시지입니다: 999");
    }

    @Test
    void null_메시지_ID로_삭제하면_예외가_발생한다() {
        ChatMessages chatMessages = new ChatMessages();

        assertThatThrownBy(() -> chatMessages.delete(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메시지 ID는 null 일 수 없습니다.");
    }

    @Test
    void 모든_메시지를_삭제할_수_있다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now());
        ChatMessage message2 = new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", LocalDateTime.now());

        chatMessages.add(message1);
        chatMessages.add(message2);

        chatMessages.delete(1L);
        chatMessages.delete(2L);

        assertThat(chatMessages.getRecentMessages(10)).isEmpty();
    }

    @Test
    void 삭제_후_새_메시지를_추가할_수_있다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now());
        ChatMessage message2 = new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", LocalDateTime.now());

        chatMessages.add(message1);
        chatMessages.add(message2);
        chatMessages.delete(1L);

        ChatMessage message3 = new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", LocalDateTime.now());
        chatMessages.add(message3);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result).containsExactly(message3, message2)
        );
    }

    @Test
    void 중간_메시지들을_순차적으로_삭제한다() {
        ChatMessages chatMessages = new ChatMessages();
        for (long i = 1; i <= 5; i++) {
            chatMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "Message " + i, LocalDateTime.now()));
        }

        chatMessages.delete(2L);
        chatMessages.delete(4L);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(3),
                () -> assertThat(result)
                        .extracting(ChatMessage::messageId)
                        .containsExactly(5L, 3L, 1L)
        );
    }

    @Test
    void 삭제_후_getHistory가_올바르게_동작한다() {
        ChatMessages chatMessages = new ChatMessages();
        chatMessages.add(new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(4L, 40L, 400L, 4000L, "Message 4", LocalDateTime.now()));

        chatMessages.delete(2L);
        List<ChatMessage> history = chatMessages.getHistory(3500L, 10);

        assertAll(
                () -> assertThat(history).hasSize(2),
                () -> assertThat(history)
                        .extracting(ChatMessage::messageId)
                        .containsExactly(3L, 1L)
        );
    }

    @Test
    void 삭제_후_getMessagesAfter가_올바르게_동작한다() {
        ChatMessages chatMessages = new ChatMessages();
        chatMessages.add(new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", LocalDateTime.now()));

        chatMessages.delete(2L);
        List<ChatMessage> messagesAfter = chatMessages.getMessagesAfter(1500L);

        assertAll(
                () -> assertThat(messagesAfter).hasSize(1),
                () -> assertThat(messagesAfter)
                        .extracting(ChatMessage::messageId)
                        .containsExactly(3L)
        );
    }

    @Test
    void 메시지를_수정한다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage original = new ChatMessage(1L, 10L, 100L, 1000L, "Original", LocalDateTime.now());
        chatMessages.add(original);

        ChatMessage updated = new ChatMessage(1L, 10L, 100L, 1000L, "Updated", LocalDateTime.now());
        chatMessages.update(updated);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).message()).isEqualTo("Updated"),
                () -> assertThat(result.get(0).messageId()).isEqualTo(1L),
                () -> assertThat(result.get(0).messageSequence()).isEqualTo(1000L)
        );
    }

    @Test
    void 중간_메시지를_수정해도_순서가_유지된다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now());
        ChatMessage message2 = new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", LocalDateTime.now());
        ChatMessage message3 = new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", LocalDateTime.now());

        chatMessages.add(message1);
        chatMessages.add(message2);
        chatMessages.add(message3);

        ChatMessage updated = new ChatMessage(2L, 20L, 200L, 2000L, "Updated Message 2", LocalDateTime.now());
        chatMessages.update(updated);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(3),
                () -> assertThat(result)
                        .extracting(ChatMessage::messageSequence)
                        .containsExactly(3000L, 2000L, 1000L),
                () -> assertThat(result.get(1).message()).isEqualTo("Updated Message 2")
        );
    }

    @Test
    void 존재하지_않는_메시지를_수정하면_예외가_발생한다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message = new ChatMessage(1L, 10L, 100L, 1000L, "Message", LocalDateTime.now());
        chatMessages.add(message);

        ChatMessage nonExistent = new ChatMessage(999L, 9990L, 99900L, 999000L, "Updated", LocalDateTime.now());
        assertThatThrownBy(() -> chatMessages.update(nonExistent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 메시지입니다: 999");
    }

    @Test
    void null_메시지로_수정하면_예외가_발생한다() {
        ChatMessages chatMessages = new ChatMessages();

        assertThatThrownBy(() -> chatMessages.update(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메시지는 null 일 수 없습니다.");
    }

    @Test
    void 여러_메시지를_수정할_수_있다() {
        ChatMessages chatMessages = new ChatMessages();
        for (long i = 1; i <= 5; i++) {
            chatMessages.add(new ChatMessage(i, i * 10, i * 100, i * 1000, "Message " + i, LocalDateTime.now()));
        }

        chatMessages.update(new ChatMessage(2L, 20L, 200L, 2000L, "Updated 2", LocalDateTime.now()));
        chatMessages.update(new ChatMessage(4L, 40L, 400L, 4000L, "Updated 4", LocalDateTime.now()));

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(5),
                () -> assertThat(result)
                        .extracting(ChatMessage::messageSequence)
                        .containsExactly(5000L, 4000L, 3000L, 2000L, 1000L),
                () -> assertThat(result)
                        .filteredOn(m -> m.messageId() == 2L)
                        .extracting(ChatMessage::message)
                        .containsExactly("Updated 2"),
                () -> assertThat(result)
                        .filteredOn(m -> m.messageId() == 4L)
                        .extracting(ChatMessage::message)
                        .containsExactly("Updated 4")
        );
    }

    @Test
    void 수정_후_삭제가_올바르게_동작한다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message = new ChatMessage(1L, 10L, 100L, 1000L, "Original", LocalDateTime.now());
        chatMessages.add(message);

        ChatMessage updated = new ChatMessage(1L, 10L, 100L, 1000L, "Updated", LocalDateTime.now());
        chatMessages.update(updated);
        chatMessages.delete(1L);

        assertThat(chatMessages.getRecentMessages(10)).isEmpty();
    }

    @Test
    void 수정_후_getHistory가_올바르게_동작한다() {
        ChatMessages chatMessages = new ChatMessages();
        chatMessages.add(new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(3L, 30L, 300L, 3000L, "Message 3", LocalDateTime.now()));

        chatMessages.update(new ChatMessage(2L, 20L, 200L, 2000L, "Updated Message 2", LocalDateTime.now()));
        List<ChatMessage> history = chatMessages.getHistory(2500L, 10);

        assertAll(
                () -> assertThat(history).hasSize(2),
                () -> assertThat(history)
                        .extracting(ChatMessage::messageId)
                        .containsExactly(2L, 1L),
                () -> assertThat(history.get(0).message()).isEqualTo("Updated Message 2")
        );
    }

    @Test
    void 수정_후_deepCopy가_올바르게_동작한다() {
        ChatMessages original = new ChatMessages();
        ChatMessage message = new ChatMessage(1L, 10L, 100L, 1000L, "Original", LocalDateTime.now());
        original.add(message);

        ChatMessage updated = new ChatMessage(1L, 10L, 100L, 1000L, "Updated", LocalDateTime.now());
        original.update(updated);

        ChatMessages copy = original.deepCopy();

        List<ChatMessage> result = copy.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).message()).isEqualTo("Updated")
        );
    }

    @Test
    void 첫번째_메시지를_수정해도_순서가_유지된다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now());
        ChatMessage message2 = new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", LocalDateTime.now());

        chatMessages.add(message1);
        chatMessages.add(message2);

        ChatMessage updated = new ChatMessage(2L, 20L, 200L, 2000L, "Updated Message 2", LocalDateTime.now());
        chatMessages.update(updated);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result.get(0).messageId()).isEqualTo(2L),
                () -> assertThat(result.get(0).message()).isEqualTo("Updated Message 2")
        );
    }

    @Test
    void 마지막_메시지를_수정해도_순서가_유지된다() {
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message1 = new ChatMessage(1L, 10L, 100L, 1000L, "Message 1", LocalDateTime.now());
        ChatMessage message2 = new ChatMessage(2L, 20L, 200L, 2000L, "Message 2", LocalDateTime.now());

        chatMessages.add(message1);
        chatMessages.add(message2);

        ChatMessage updated = new ChatMessage(1L, 10L, 100L, 1000L, "Updated Message 1", LocalDateTime.now());
        chatMessages.update(updated);

        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result.get(1).messageId()).isEqualTo(1L),
                () -> assertThat(result.get(1).message()).isEqualTo("Updated Message 1")
        );
    }
}
