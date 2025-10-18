package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.infrastructure.persistence.event.LoadedHistoryEvent;
import com.tok.pekko.infrastructure.persistence.event.LoadedRecentMessagesEvent;
import com.tok.pekko.infrastructure.persistence.event.StoredEvent;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.eventstream.EventStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MessageStorageEventStreamAdapterTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() {
        testKit = ActorTestKit.create();
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void EventStream에_StoredEvent_메시지를_발행한다() {
        // given
        TestProbe<StoredEvent> eventProbe = createStoredEventProbe();
        MessageStorageEventStreamAdapter messageStorageEventStreamAdapter = createMessageStorageAdapter();

        ChatMessage message = ChatMessage.create(
                1L,
                100L,
                1L,
                "Test Message",
                LocalDateTime.of(2025, 10, 17, 17, 0, 0)
        );

        // when
        messageStorageEventStreamAdapter.store(message);

        // then
        StoredEvent storedEvent = eventProbe.expectMessageClass(
                StoredEvent.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(storedEvent.message()).isEqualTo(message),
                () -> assertThat(storedEvent.message().channelId()).isEqualTo(1L),
                () -> assertThat(storedEvent.message().userId()).isEqualTo(100L),
                () -> assertThat(storedEvent.message().message()).isEqualTo("Test Message")
        );
    }

    @Test
    void EventStream에_LoadedHistoryEvent_메시지를_발행한다() {
        // given
        TestProbe<LoadedHistoryEvent> eventProbe = createLoadedHistoryEventProbe();
        MessageStorageEventStreamAdapter messageStorageEventStreamAdapter = createMessageStorageAdapter();

        Long channelId = 2L;
        long messageSequence = 10L;
        int size = 5;
        TestProbe<ChatChannelReaderCommand> replyProbe = testKit.createTestProbe(ChatChannelReaderCommand.class);

        // when
        messageStorageEventStreamAdapter.findHistory(channelId, messageSequence, size, replyProbe.ref());

        // then
        LoadedHistoryEvent loadedHistoryEvent = eventProbe.expectMessageClass(
                LoadedHistoryEvent.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(loadedHistoryEvent.channelId()).isEqualTo(channelId),
                () -> assertThat(loadedHistoryEvent.messageSequence()).isEqualTo(messageSequence),
                () -> assertThat(loadedHistoryEvent.size()).isEqualTo(size),
                () -> assertThat(loadedHistoryEvent.replyTo()).isEqualTo(replyProbe.ref())
        );
    }

    @Test
    void EventStream에_LoadedRecentMessagesEvent_메시지를_발행한다() {
        // given
        TestProbe<LoadedRecentMessagesEvent> eventProbe = createLoadedRecentMessagesEventProbe();
        MessageStorageEventStreamAdapter messageStorageEventStreamAdapter = createMessageStorageAdapter();

        Long channelId = 3L;
        int size = 50;
        TestProbe<ChatChannelEntityCommand> replyProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);

        // when
        messageStorageEventStreamAdapter.findRecentMessages(channelId, size, replyProbe.ref());

        // then
        LoadedRecentMessagesEvent loadedRecentMessagesEvent = eventProbe.expectMessageClass(
                LoadedRecentMessagesEvent.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(loadedRecentMessagesEvent.channelId()).isEqualTo(channelId),
                () -> assertThat(loadedRecentMessagesEvent.size()).isEqualTo(size),
                () -> assertThat(loadedRecentMessagesEvent.replyTo()).isEqualTo(replyProbe.ref())
        );
    }

    private MessageStorageEventStreamAdapter createMessageStorageAdapter() {

        ObjectProvider<ActorSystem<?>> actorSystemProvider = new ObjectProvider<>() {
            @Override
            public ActorSystem<?> getObject() {
                return testKit.system();
            }

            @Override
            public ActorSystem<?> getObject(Object... args) {
                return testKit.system();
            }

            @Override
            public ActorSystem<?> getIfAvailable() {
                return testKit.system();
            }

            @Override
            public ActorSystem<?> getIfUnique() {
                return testKit.system();
            }
        };

        return new MessageStorageEventStreamAdapter(actorSystemProvider);
    }

    private TestProbe<StoredEvent> createStoredEventProbe() {
        TestProbe<StoredEvent> probe = testKit.createTestProbe(StoredEvent.class);
        testKit.system()
               .eventStream()
               .tell(new EventStream.Subscribe<>(StoredEvent.class, probe.ref()));
        return probe;
    }

    private TestProbe<LoadedHistoryEvent> createLoadedHistoryEventProbe() {
        TestProbe<LoadedHistoryEvent> probe = testKit.createTestProbe(LoadedHistoryEvent.class);
        testKit.system()
               .eventStream()
               .tell(new EventStream.Subscribe<>(LoadedHistoryEvent.class, probe.ref()));
        return probe;
    }

    private TestProbe<LoadedRecentMessagesEvent> createLoadedRecentMessagesEventProbe() {
        TestProbe<LoadedRecentMessagesEvent> probe = testKit.createTestProbe(LoadedRecentMessagesEvent.class);
        testKit.system()
               .eventStream()
               .tell(new EventStream.Subscribe<>(LoadedRecentMessagesEvent.class, probe.ref()));
        return probe;
    }
}
