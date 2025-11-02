package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.chat.port.out.ChannelMembershipPort;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundRegisteredChannelIds;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncJoinChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncLeaveChannel;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class ChannelMembershipAdapter implements ChannelMembershipPort {

    private final ChannelMembershipRepository channelMembershipRepository;

    @Override
    public void findParticipatingChannels(Long userId, ActorRef<ClientSessionCommand> replyTo) {
        Mono.fromCallable(() -> channelMembershipRepository.findAllIChannelIds(userId))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(channelIds -> replyTo.tell(new FoundRegisteredChannelIds(channelIds)))
            .subscribe();
    }

    @Override
    public void joinChannel(Long userId, Long channelId, ActorRef<ClientSessionCommand> replyTo) {
        Mono.fromRunnable(() -> channelMembershipRepository.save(userId, channelId))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(ignored -> replyTo.tell(new SyncJoinChannel(channelId)))
            .subscribe();
    }

    @Override
    public void leaveChannel(Long userId, Long channelId, ActorRef<ClientSessionCommand> replyTo) {
        Mono.fromRunnable(() -> channelMembershipRepository.save(userId, channelId))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(ignored -> replyTo.tell(new SyncLeaveChannel(channelId)))
            .subscribe();
    }
}
