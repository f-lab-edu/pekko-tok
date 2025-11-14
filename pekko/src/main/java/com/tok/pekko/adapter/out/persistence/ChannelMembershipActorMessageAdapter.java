package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorMessagePort;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundRegisteredChannelIds;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class ChannelMembershipActorMessageAdapter implements ChannelMembershipActorMessagePort {

    private final ParticipatingChannelRepository participatingChannelRepository;

    @Override
    public void sendParticipatingChannels(Long userId, ActorRef<ClientSessionCommand> replyTo) {
        Mono.fromCallable(() -> participatingChannelRepository.findAllChannelIds(userId))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(channelIds -> replyTo.tell(new FoundRegisteredChannelIds(channelIds)))
            .subscribe();
    }
}
