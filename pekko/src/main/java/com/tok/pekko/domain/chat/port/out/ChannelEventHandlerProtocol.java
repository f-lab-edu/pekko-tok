package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.global.common.CborSerializable;

public interface ChannelEventHandlerProtocol {

    interface ChannelEventHandlerCommand extends CborSerializable { }

    // ChannelMembership 영속화 시 생성된 PK를 ChannelEntity에서 관리하는 Channel에 반영하기 위한 메시지 : 외부 -> ChannelEventHandlerEntity
    record NotifyStoredMembership(ChannelMembership channelMembership) implements ChannelEventHandlerCommand { }

    // 도메인 이벤트 처리가 성공되었음을 전파받기 위한 메시지 : 외부 -> ChannelEventHandlerEntity
    record EventSucceeded(Long eventId) implements ChannelEventHandlerCommand { }

    // 도메인 이벤트 처리가 실패되었음을 전파받기 위한 메시지 : 외부 -> ChannelEventHandlerEntity
    record EventFailed(Long eventId, Throwable throwable) implements ChannelEventHandlerCommand { }
}
