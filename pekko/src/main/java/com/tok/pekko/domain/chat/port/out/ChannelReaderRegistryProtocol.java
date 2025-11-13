package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.global.common.CborSerializable;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelReaderRegistryProtocol {

    interface ChannelReaderRegistryCommand extends CborSerializable { }

    // ChannelReaderActor가 생성 직후 자신을 ChannelReaderRegistryActor에 등록하고 ChannelEntity에 전달할 readerName과 ActorRef를 공유하기 위한 메시지 : ChannelReaderActor -> ChannelReaderRegistryActor
    record SpawnedChannelReaderActor(Long channelId, ActorRef<ChannelReaderCommand> reader, String readerName) implements ChannelReaderRegistryCommand { }

    // ClientSessionActor가 종료되며 자신이 구독하던 channelId 목록을 Registry에 넘겨 ChannelReaderActor와의 매핑을 해제하도록 요청하는 메시지 : ClientSessionActor -> ChannelReaderRegistryActor
    record ReleaseClientSessionActor(Long userId, List<Long> channelIds) implements ChannelReaderRegistryCommand { }

}
