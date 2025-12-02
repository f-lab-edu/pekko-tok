package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncChannel;
import com.tok.pekko.domain.chat.port.out.ChannelActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.ChannelEventHandlerCommand;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.EventFailed;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.EventSucceeded;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Component
@RequiredArgsConstructor
public class ChannelActorStorageAdapter implements ChannelActorStoragePort {

    private final Scheduler databaseScheduler;
    private final ChannelRepository channelRepository;

    @Override
    public void find(Long channelId, ActorRef<ChannelEntityCommand> replyTo) {
        Mono.fromCallable(() -> channelRepository.findById(channelId))
            .subscribeOn(databaseScheduler)
            .doOnNext(
                    target -> target.ifPresentOrElse(
                            channel -> replyTo.tell(new SyncChannel(channel)),
                            () -> replyTo.tell(new Shutdown())
                    )
            )
            .subscribe();
    }

    @Override
    public void update(Channel channel, Long eventId, ActorRef<ChannelEventHandlerCommand> replyTo) {
        Mono.fromRunnable(() -> channelRepository.update(channel))
            .subscribeOn(databaseScheduler)
            .doOnSuccess(ignored -> replyTo.tell(new EventSucceeded(eventId)))
            .doOnError(throwable -> replyTo.tell(new EventFailed(eventId, throwable)))
            .subscribe();
    }
}
