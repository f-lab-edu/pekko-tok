package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChannelInvitePayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChannelKickedPayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChannelMembershipCountPayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChannelMembershipPayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChannelNamePayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChannelPolicyPayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChatMessagePayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ErrorPayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketOutboundMessage;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SuppressWarnings({"NonAsciiCharacters", "unchecked"})
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WebSocketMessageSenderTest {

    @Test
    void 단일_메시지를_클라이언트에게_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        ChatMessage message = createMessage(1L, "테스트 메시지");

        // when
        sender.sendMessage(message);

        // then
        verify(sink).tryEmitNext(new WebSocketOutboundMessage(WebSocketMessageType.NEW, new ChatMessagePayload(message)));
    }

    @Test
    void 여러_메시지를_클라이언트에게_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        List<ChatMessage> messages = List.of(
                createMessage(1L, "메시지1"),
                createMessage(2L, "메시지2"),
                createMessage(3L, "메시지3")
        );

        // when
        sender.sendMessages(messages);

        // then
        assertAll(
                () -> verify(sink).tryEmitNext(new WebSocketOutboundMessage(WebSocketMessageType.NEW, new ChatMessagePayload(messages.get(0)))),
                () -> verify(sink).tryEmitNext(new WebSocketOutboundMessage(WebSocketMessageType.NEW, new ChatMessagePayload(messages.get(1)))),
                () -> verify(sink).tryEmitNext(new WebSocketOutboundMessage(WebSocketMessageType.NEW, new ChatMessagePayload(messages.get(2))))
        );
    }

    @Test
    void 삭제된_메시지를_클라이언트에게_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        ChatMessage deletedMessage = createMessage(1L, "삭제된 메시지");

        // when
        sender.sendDeletedMessage(deletedMessage);

        // then
        verify(sink).tryEmitNext(new WebSocketOutboundMessage(WebSocketMessageType.DELETED, new ChatMessagePayload(deletedMessage)));
    }

    @Test
    void 수정된_메시지를_클라이언트에게_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        ChatMessage updatedMessage = createMessage(1L, "수정된 메시지");

        // when
        sender.sendUpdatedMessage(updatedMessage);

        // then
        verify(sink).tryEmitNext(new WebSocketOutboundMessage(WebSocketMessageType.UPDATED, new ChatMessagePayload(updatedMessage)));
    }

    @Test
    void 웹소켓_헬스체크_핑을_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);

        // when
        sender.sendWebSocketPing();

        // then
        verify(sink).tryEmitNext(WebSocketOutboundMessage.PING_MESSAGE);
    }

    @Test
    void 재연결을_요청한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);

        // when
        sender.requestSessionReconnect();

        // then
        verify(sink).tryEmitNext(WebSocketOutboundMessage.RECONNECT_MESSAGE);
    }

    @Test
    void sink를_교체하면_새로운_sink로_메시지를_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> firstSink = mock(Sinks.Many.class);
        Sinks.Many<WebSocketOutboundMessage> secondSink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(firstSink);
        ChatMessage message = createMessage(10L, "hello");

        // when
        sender.sendMessage(message);
        sender.attachSink(secondSink);
        sender.sendMessage(message);

        // then
        verify(firstSink, times(1)).tryEmitNext(new WebSocketOutboundMessage(WebSocketMessageType.NEW, new ChatMessagePayload(message)));
        verify(secondSink, times(1)).tryEmitNext(new WebSocketOutboundMessage(WebSocketMessageType.NEW, new ChatMessagePayload(message)));
    }

    @Test
    void sink를_detach하면_추가_메시지가_전송되지_않는다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);

        // when
        sender.detachSink(sink);
        sender.requestSessionReconnect();

        // then
        verifyNoInteractions(sink);
    }

    @Test
    void 채널_멤버십_변경_이벤트를_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        Long channelId = 10L;
        ChannelMembership membership = createManagerMembership(1L, channelId, 100L);
        int membershipCount = 5;

        ChannelMembershipPayload expectedPayload = ChannelMembershipPayload.from(channelId, membership, membershipCount);
        WebSocketOutboundMessage expectedWebSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.CHANNEL_MEMBERSHIP_CHANGED,
                expectedPayload
        );

        // when
        sender.sendChangedChannelMembership(channelId, membership, membershipCount);

        // then
        verify(sink).tryEmitNext(expectedWebSocketPayload);
    }

    @Test
    void 채널_멤버십_카운트_변경_이벤트를_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        Long channelId = 10L;
        int membershipCount = 7;

        ChannelMembershipCountPayload expectedPayload = new ChannelMembershipCountPayload(channelId, membershipCount);
        WebSocketOutboundMessage expectedWebSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.CHANNEL_MEMBERSHIP_COUNT_CHANGED,
                expectedPayload
        );

        // when
        sender.sendChangedMembershipCount(channelId, membershipCount);

        // then
        verify(sink).tryEmitNext(expectedWebSocketPayload);
    }

    @Test
    void 채널_초대_이벤트를_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        Long channelId = 10L;

        ChannelInvitePayload expectedPayload = new ChannelInvitePayload(channelId);
        WebSocketOutboundMessage expectedWebSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.CHANNEL_INVITED,
                expectedPayload
        );

        // when
        sender.sendInvitedChannel(channelId);

        // then
        verify(sink).tryEmitNext(expectedWebSocketPayload);
    }

    @Test
    void 채널_이름_변경_이벤트를_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        Long channelId = 10L;
        String editedName = "새로운 채널명";

        ChannelNamePayload expectedPayload = new ChannelNamePayload(channelId, editedName);
        WebSocketOutboundMessage expectedWebSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.CHANNEL_NAME_EDITED,
                expectedPayload
        );

        // when
        sender.sendEditedChannelName(channelId, editedName);

        // then
        verify(sink).tryEmitNext(expectedWebSocketPayload);
    }

    @Test
    void 채널_강퇴_이벤트를_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        Long channelId = 10L;

        ChannelKickedPayload expectedPayload = new ChannelKickedPayload(channelId);
        WebSocketOutboundMessage expectedWebSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.CHANNEL_KICKED,
                expectedPayload
        );

        // when
        sender.sendKickedFromChannel(channelId);

        // then
        verify(sink).tryEmitNext(expectedWebSocketPayload);
    }

    @Test
    void 채널_정책_변경_이벤트를_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        Long channelId = 10L;
        ChannelPolicy channelPolicy = ChannelPolicy.defaultPolicy()
                                                   .updatePublic(false)
                                                   .updateEditOwnMessage(true)
                                                   .updateDeleteOwnMessage(false);

        ChannelPolicyPayload expectedPayload = ChannelPolicyPayload.from(channelId, channelPolicy);
        WebSocketOutboundMessage expectedWebSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.CHANNEL_POLICY_CHANGED,
                expectedPayload
        );

        // when
        sender.sendChangedChannelPolicy(channelId, channelPolicy);

        // then
        verify(sink).tryEmitNext(expectedWebSocketPayload);
    }

    @Test
    void 에러_이벤트를_전송한다() {
        // given
        Sinks.Many<WebSocketOutboundMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        Long channelId = 10L;
        String reason = "권한이 없습니다";

        ErrorPayload expectedPayload = new ErrorPayload(channelId, reason);
        WebSocketOutboundMessage expectedWebSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.ERROR,
                expectedPayload
        );

        // when
        sender.sendError(channelId, reason);

        // then
        verify(sink).tryEmitNext(expectedWebSocketPayload);
    }

    private ChatMessage createMessage(Long messageId, String content) {
        return ChatMessage.create(
                1L,
                100L,
                messageId,
                content,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private ChannelMembership createManagerMembership(Long membershipId, Long channelId, Long userId) {
        Set<ChannelPermissionType> permissions = EnumSet.of(
                ChannelPermissionType.MESSAGE_EDIT,
                ChannelPermissionType.MEMBER_KICK
        );
        ChannelManagePermissions managePermissions = ChannelManagePermissions.ofManager(permissions);

        return ChannelMembership.create(
                membershipId,
                channelId,
                userId,
                ChannelRole.MANAGER,
                managePermissions,
                LocalDateTime.now()
        );
    }
}
