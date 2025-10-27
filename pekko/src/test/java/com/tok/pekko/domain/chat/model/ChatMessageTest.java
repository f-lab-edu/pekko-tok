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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatMessageTest {

    @Test
    void 영속화되지_않은_ChatMessage를_초기화한다() {
        // when
        ChatMessage chatMessage = ChatMessage.create(
                1L,
                1001L,
                1L,
                "Hello World",
                LocalDateTime.now()
        );

        // then
        assertAll(
                () -> assertThat(chatMessage.messageId()).isNull(),
                () -> assertThat(chatMessage.channelId()).isEqualTo(1L),
                () -> assertThat(chatMessage.userId()).isEqualTo(1001L),
                () -> assertThat(chatMessage.messageSequence()).isEqualTo(1L),
                () -> assertThat(chatMessage.message()).isEqualTo("Hello World"),
                () -> assertThat(chatMessage.timestamp()).isNotNull()
        );
    }

    @Test
    void 영속화한_CHatMessage를_초기화한다() {
        // when
        ChatMessage chatMessage = new ChatMessage(
                100L,
                1L,
                1001L,
                1L,
                "Test Message",
                LocalDateTime.now()
        );

        // then
        assertAll(
                () -> assertThat(chatMessage.messageId()).isEqualTo(100L),
                () -> assertThat(chatMessage.channelId()).isEqualTo(1L),
                () -> assertThat(chatMessage.userId()).isEqualTo(1001L),
                () -> assertThat(chatMessage.messageSequence()).isEqualTo(1L),
                () -> assertThat(chatMessage.message()).isEqualTo("Test Message"),
                () -> assertThat(chatMessage.timestamp()).isNotNull()
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 메시지가_비어_있다면_ChatMessage를_초기화할_수_없다(String invalidMessage) {
        // when & then
        assertThatThrownBy(
                () -> ChatMessage.create(
                        1L,
                        1001L,
                        1L,
                        invalidMessage,
                        LocalDateTime.now()
                )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessage("메시지는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, -100})
    void 채널_ID가_비어_있거나_0_또는_음수라면_ChatMessage를_초기화할_수_없다(Long channelId) {
        // when & then
        assertThatThrownBy(
                () -> ChatMessage.create(
                        channelId,
                        1001L,
                        1L,
                        "Test",
                        LocalDateTime.now()
                )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessage("채널 ID는 양수여야 합니다.");
    }

    @ParameterizedTest(name = "{0}일 때 초기화할 수 없다")
    @NullSource
    @ValueSource(longs = {0, -1, -100})
    void 사용자_ID가_비어_있거나_0_또는_음수라면_ChatMessage를_생성할_수_없다(Long userId) {
        // when & then
        assertThatThrownBy(
                () -> ChatMessage.create(
                        1L,
                        userId,
                        1L,
                        "Test",
                        LocalDateTime.now()
                )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessage("사용자 ID는 양수여야 합니다.");
    }

    @Test
    void 기존_ChatMessage의_내용을_변경한다() {
        // given
        ChatMessage chatMessage = new ChatMessage(
                100L,
                1L,
                1001L,
                1L,
                "Test Message",
                LocalDateTime.now()
        );

        // when
        ChatMessage actual = chatMessage.updateMessage("New Message");

        // then
        assertThat(actual).extracting(ChatMessage::message)
                          .isEqualTo("New Message")
                          .isNotEqualTo("Test Message");
    }
}
