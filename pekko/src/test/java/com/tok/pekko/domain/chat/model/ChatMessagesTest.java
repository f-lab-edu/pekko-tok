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
        // when
        ChatMessages chatMessages = new ChatMessages();

        // then
        assertThat(chatMessages.getRecentMessages(10)).isEmpty();
    }

    @Test
    void 메시지를_추가한다() {
        // given
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage message = new ChatMessage(1L, 1L, 1L, 1L, "Test", LocalDateTime.now());

        // when
        chatMessages.add(message);

        // then
        assertThat(chatMessages.getRecentMessages(10)).hasSize(1)
                                                            .contains(message);
    }

    @Test
    void null_메시지를_추가하면_예외가_발생한다() {
        // given
        ChatMessages chatMessages = new ChatMessages();

        // when & then
        assertThatThrownBy(() -> chatMessages.add(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메시지는 null 일 수 없습니다.");
    }

    @Test
    void MAX_SIZE를_초과하면_가장_오래된_메시지가_제거된다() {
        // given
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage firstMessage = new ChatMessage(1L, 1L, 1L, 1L, "First", LocalDateTime.now());
        chatMessages.add(firstMessage);

        // when
        for (long i = 2; i <= 101; i++) {
            chatMessages.add(new ChatMessage(i, 1L, 1L, i, "Message " + i, LocalDateTime.now()));
        }

        // then
        List<ChatMessage> recentMessages = chatMessages.getRecentMessages(100);
        assertAll(
                () -> assertThat(recentMessages).hasSize(100),
                () -> assertThat(recentMessages).doesNotContain(firstMessage)
        );
    }

    @Test
    void 특정_메시지_시퀀스_번호_이전의_메시지_히스토리를_조회한다() {
        // given
        ChatMessages chatMessages = new ChatMessages();
        chatMessages.add(new ChatMessage(5L, 1L, 1L, 5L, "Message 5", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(3L, 1L, 1L, 3L, "Message 3", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(1L, 1L, 1L, 1L, "Message 1", LocalDateTime.now()));

        // when
        List<ChatMessage> history = chatMessages.getHistory(4L, 10);

        // then
        assertAll(
                () -> assertThat(history).hasSize(2),
                () -> assertThat(history).allMatch(message -> message.messageSequence() < 4L)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 메시지_히스토리_조회_시_개수가_0_이하면_예외가_발생한다(int invalidSize) {
        // given
        ChatMessages chatMessages = new ChatMessages();

        // when & then
        assertThatThrownBy(() -> chatMessages.getHistory(10L, invalidSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회하려는 메시지 개수는 양수여야 합니다.");
    }

    @Test
    void 메시지_히스토리_조회_시_개수가_MAX_SIZE를_초과하면_예외가_발생한다() {
        // given
        ChatMessages chatMessages = new ChatMessages();

        // when & then
        assertThatThrownBy(() -> chatMessages.getHistory(10L, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회하려는 메시지 개수는 100개를 넘을 수 없습니다.");
    }

    @Test
    void 지정한_개수만큼_최근_메시지를_조회한다() {
        // given
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage old = new ChatMessage(1L, 1L, 1L, 1L, "Old", LocalDateTime.now());
        ChatMessage recent2 = new ChatMessage(2L, 1L, 1L, 2L, "Recent 2", LocalDateTime.now());
        ChatMessage recent1 = new ChatMessage(3L, 1L, 1L, 3L, "Recent 1", LocalDateTime.now());

        chatMessages.add(old);
        chatMessages.add(recent2);
        chatMessages.add(recent1);

        // when
        List<ChatMessage> recentMessages = chatMessages.getRecentMessages(2);

        // then
        assertAll(
                () -> assertThat(recentMessages).hasSize(2),
                () -> assertThat(recentMessages).containsExactly(recent1, recent2)
        );
    }

    @Test
    void 특정_메시지_시퀀스_번호_이후의_메시지를_조회한다() {
        // given
        ChatMessages chatMessages = new ChatMessages();
        chatMessages.add(new ChatMessage(5L, 1L, 1L, 5L, "Message 5", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(3L, 1L, 1L, 3L, "Message 3", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(1L, 1L, 1L, 1L, "Message 1", LocalDateTime.now()));

        // when
        List<ChatMessage> messagesAfter = chatMessages.getMessagesAfter(2L);

        // then
        assertAll(
                () -> assertThat(messagesAfter).hasSize(2),
                () -> assertThat(messagesAfter).allMatch(message -> message.messageSequence() > 2L)
        );
    }

    @Test
    void 기존과_독립적인_복사본을_생성한다() {
        // given
        ChatMessages original = new ChatMessages();
        ChatMessage message1 = new ChatMessage(1L, 1L, 1L, 1L, "Original", LocalDateTime.now());
        original.add(message1);

        // when
        ChatMessages copy = original.deepCopy();
        copy.add(new ChatMessage(2L, 1L, 2L, 2L, "Copy Only", LocalDateTime.now()));

        // then
        assertAll(
                () -> assertThat(original.getRecentMessages(10)).hasSize(1),
                () -> assertThat(copy.getRecentMessages(10)).hasSize(2)
        );
    }

    @Test
    void null_메시지_리스트로_동기화하면_예외가_발생한다() {
        // given
        ChatMessages chatMessages = new ChatMessages();

        // when & then
        assertThatThrownBy(() -> chatMessages.syncMessages(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메시지는 null 일 수 없습니다.");
    }

    @Test
    void 빈_메시지_리스트로_동기화하면_아무_일도_일어나지_않는다() {
        // given
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage existing = new ChatMessage(1L, 1L, 1L, 1L, "Existing", LocalDateTime.now());
        chatMessages.add(existing);

        // when
        chatMessages.syncMessages(List.of());

        // then
        assertAll(
                () -> assertThat(chatMessages.getRecentMessages(10)).hasSize(1),
                () -> assertThat(chatMessages.getRecentMessages(10)).containsExactly(existing)
        );
    }

    @Test
    void 새로운_메시지를_동기화하면_messageSequence_순서대로_정렬된다() {
        // given
        ChatMessages chatMessages = new ChatMessages();
        List<ChatMessage> newMessages = List.of(
                new ChatMessage(3L, 1L, 1L, 3L, "Message 3", LocalDateTime.now()),
                new ChatMessage(1L, 1L, 1L, 1L, "Message 1", LocalDateTime.now()),
                new ChatMessage(5L, 1L, 1L, 5L, "Message 5", LocalDateTime.now())
        );

        // when
        chatMessages.syncMessages(newMessages);

        // then
        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(3),
                () -> assertThat(result)
                        .extracting(ChatMessage::messageSequence)
                        .isSortedAccordingTo(Comparator.reverseOrder()),
                () -> assertThat(result.get(0).messageSequence()).isEqualTo(5L),
                () -> assertThat(result.get(1).messageSequence()).isEqualTo(3L),
                () -> assertThat(result.get(2).messageSequence()).isEqualTo(1L)
        );
    }

    @Test
    void 기존_메시지와_새_메시지를_동기화하면_모두_순서대로_병합된다() {
        // given
        ChatMessages chatMessages = new ChatMessages();
        chatMessages.add(new ChatMessage(2L, 1L, 1L, 2L, "Message 2", LocalDateTime.now()));
        chatMessages.add(new ChatMessage(6L, 1L, 1L, 6L, "Message 6", LocalDateTime.now()));

        List<ChatMessage> newMessages = List.of(
                new ChatMessage(4L, 1L, 1L, 4L, "Message 4", LocalDateTime.now()),
                new ChatMessage(8L, 1L, 1L, 8L, "Message 8", LocalDateTime.now())
        );

        // when
        chatMessages.syncMessages(newMessages);

        // then
        List<ChatMessage> result = chatMessages.getRecentMessages(10);
        assertAll(
                () -> assertThat(result).hasSize(4),
                () -> assertThat(result)
                        .extracting(ChatMessage::messageSequence)
                        .containsExactly(8L, 6L, 4L, 2L)
        );
    }

    @Test
    void 동기화_후_MAX_SIZE를_초과하면_가장_오래된_메시지가_제거된다() {
        // given
        ChatMessages chatMessages = new ChatMessages();
        for (long i = 1; i <= 50; i++) {
            chatMessages.add(new ChatMessage(i, 1L, 1L, i, "Message " + i, LocalDateTime.now()));
        }

        List<ChatMessage> newMessages = new java.util.ArrayList<>();
        for (long i = 51; i <= 105; i++) {
            newMessages.add(new ChatMessage(i, 1L, 1L, i, "Message " + i, LocalDateTime.now()));
        }

        // when
        chatMessages.syncMessages(newMessages);

        // then
        List<ChatMessage> result = chatMessages.getRecentMessages(100);
        assertAll(
                () -> assertThat(result).hasSize(100),
                () -> assertThat(result.get(0).messageSequence()).isEqualTo(105L),
                () -> assertThat(result.get(99).messageSequence()).isEqualTo(6L),
                () -> assertThat(result).noneMatch(message -> message.messageSequence() < 6L)
        );
    }

    @Test
    void 동기화_시_중복된_messageId를_가진_메시지는_제거된다() {
        // given
        ChatMessages chatMessages = new ChatMessages();
        ChatMessage existing = new ChatMessage(1L, 1L, 1L, 100L, "Existing", LocalDateTime.now());
        chatMessages.add(existing);

        List<ChatMessage> newMessages = List.of(
                new ChatMessage(1L, 1L, 1L, 100L, "Updated", LocalDateTime.now()),
                new ChatMessage(2L, 1L, 1L, 200L, "New Message", LocalDateTime.now())
        );

        // when
        chatMessages.syncMessages(newMessages);

        // then
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
        // given
        ChatMessages chatMessages = new ChatMessages();

        List<ChatMessage> newMessages = List.of(
                new ChatMessage(1L, 1L, 1L, 100L, "First", LocalDateTime.now()),
                new ChatMessage(1L, 1L, 1L, 100L, "Second", LocalDateTime.now()),
                new ChatMessage(1L, 1L, 1L, 100L, "Third", LocalDateTime.now()),
                new ChatMessage(2L, 1L, 1L, 200L, "Unique", LocalDateTime.now())
        );

        // when
        chatMessages.syncMessages(newMessages);

        // then
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
        // given
        ChatMessages chatMessages = new ChatMessages();
        for (long i = 1; i <= 50; i++) {
            chatMessages.add(new ChatMessage(i, 1L, 1L, i * 10, "Message " + i, LocalDateTime.now()));
        }

        List<ChatMessage> newMessages = new java.util.ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            newMessages.add(new ChatMessage(i, 1L, 1L, i * 10, "Updated " + i, LocalDateTime.now()));
        }
        for (long i = 51; i <= 60; i++) {
            newMessages.add(new ChatMessage(i, 1L, 1L, i * 10, "New Message " + i, LocalDateTime.now()));
        }

        // when
        chatMessages.syncMessages(newMessages);

        // then
        List<ChatMessage> result = chatMessages.getRecentMessages(100);
        assertAll(
                () -> assertThat(result).hasSize(60),
                () -> assertThat(result)
                        .extracting(ChatMessage::messageSequence)
                        .isSortedAccordingTo(Comparator.reverseOrder()),
                () -> assertThat(result.get(0).messageSequence()).isEqualTo(600L),
                () -> assertThat(result)
                        .filteredOn(message -> message.messageId() <= 10L)
                        .allMatch(message -> message.message().startsWith("Updated"))
        );
    }
}
