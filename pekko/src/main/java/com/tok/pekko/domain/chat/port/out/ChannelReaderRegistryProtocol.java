package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.global.common.CborSerializable;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelReaderRegistryProtocol {

    interface ChannelReaderRegistryCommand extends CborSerializable { }

    // ChannelReaderActor가 생성 직후 자신을 ChannelReaderRegistryActor에 등록하고 ChannelEntity에 전달할 readerName과 ActorRef를 공유하기 위한 메시지
    record SpawnedChannelReaderActor(Long channelId, ActorRef<ChannelReaderCommand> reader, String readerName) implements ChannelReaderRegistryCommand { }

    // ClientSessionActor가 종료되며 자신이 구독하던 channelId 목록을 Registry에 넘겨 ChannelReaderActor와의 매핑을 해제하도록 요청하는 메시지
    record ReleaseChannelReaderActor(Long userId, List<Long> channelIds) implements ChannelReaderRegistryCommand { }

    // 헬스체크 실패 등으로 특정 ChannelReaderActor를 재생성해야 함을 Registry에 보고하는 메시지
    record ReportUnhealthyChannelReader(List<Long> channelIds) implements ChannelReaderRegistryCommand { }

    // ChannelReaderActor가 비정상 세션을 감지했을 때 Registry에 해당 세션-채널 매핑 제거를 요청하는 메시지
    record ReportUnhealthyClientSession(Long userId, Long channelId) implements ChannelReaderRegistryCommand { }
}
