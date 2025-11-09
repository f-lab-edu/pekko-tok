package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.chat.port.out.ChannelMembershipPort;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundRegisteredChannelIds;
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
}
