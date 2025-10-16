package com.tok.pekko.domain.chat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatMessageTest {

    @Test
    void ChatMessage를_생성한다() {
        // given
        Long channelId = 1L;
        Long userId = 1001L;
        long messageSequence = 1L;
        String message = "Hello World";
        LocalDateTime timestamp = LocalDateTime.now();

        // when
        ChatMessage chatMessage = ChatMessage.create(channelId, userId, messageSequence, message, timestamp);

        // then
        assertAll(
                () -> assertThat(chatMessage.messageId()).isNull(),
                () -> assertThat(chatMessage.channelId()).isEqualTo(channelId),
                () -> assertThat(chatMessage.userId()).isEqualTo(userId),
                () -> assertThat(chatMessage.messageSequence()).isEqualTo(messageSequence),
                () -> assertThat(chatMessage.message()).isEqualTo(message),
                () -> assertThat(chatMessage.timestamp()).isEqualTo(timestamp)
        );
    }

    @Test
    void 모든_필드를_초기화한_ChatMessage를_생성한다() {
        // given
        Long messageId = 100L;
        Long channelId = 1L;
        Long userId = 1001L;
        long messageSequence = 1L;
        String message = "Test Message";
        LocalDateTime timestamp = LocalDateTime.now();

        // when
        ChatMessage chatMessage = new ChatMessage(messageId, channelId, userId, messageSequence, message, timestamp);

        // then
        assertAll(
                () -> assertThat(chatMessage.messageId()).isEqualTo(messageId),
                () -> assertThat(chatMessage.channelId()).isEqualTo(channelId),
                () -> assertThat(chatMessage.userId()).isEqualTo(userId),
                () -> assertThat(chatMessage.messageSequence()).isEqualTo(messageSequence),
                () -> assertThat(chatMessage.message()).isEqualTo(message),
                () -> assertThat(chatMessage.timestamp()).isEqualTo(timestamp)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 메시지가_null_또는_빈_문자열이면_ChatMessage를_생성할_수_없다(String invalidMessage) {
        // given
        Long channelId = 1L;
        Long userId = 1001L;
        long messageSequence = 1L;
        LocalDateTime timestamp = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> ChatMessage.create(channelId, userId, messageSequence, invalidMessage, timestamp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메시지는 비어 있을 수 없습니다.");
    }

    @Test
    void 채널_ID가_null이면_ChatMessage를_생성할_수_없다() {
        // given
        Long channelId = null;
        Long userId = 1001L;
        long messageSequence = 1L;
        String message = "Test";
        LocalDateTime timestamp = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> ChatMessage.create(channelId, userId, messageSequence, message, timestamp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채널 ID는 양수여야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -100})
    void 채널_ID가_0_이하면_ChatMessage를_생성할_수_없다(Long invalidChannelId) {
        // given
        Long userId = 1001L;
        long messageSequence = 1L;
        String message = "Test";
        LocalDateTime timestamp = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> ChatMessage.create(invalidChannelId, userId, messageSequence, message, timestamp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채널 ID는 양수여야 합니다.");
    }

    @Test
    void 사용자_ID가_null이면_ChatMessage를_생성할_수_없다() {
        // given
        Long channelId = 1L;
        Long userId = null;
        long messageSequence = 1L;
        String message = "Test";
        LocalDateTime timestamp = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> ChatMessage.create(channelId, userId, messageSequence, message, timestamp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 ID는 양수여야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -100})
    void 사용자_ID가_0_이하면_ChatMessage를_생성할_수_없다(Long invalidUserId) {
        // given
        Long channelId = 1L;
        long messageSequence = 1L;
        String message = "Test";
        LocalDateTime timestamp = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> ChatMessage.create(channelId, invalidUserId, messageSequence, message, timestamp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 ID는 양수여야 합니다.");
    }
}
