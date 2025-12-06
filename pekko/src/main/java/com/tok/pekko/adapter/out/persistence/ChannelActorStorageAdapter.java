package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelBatchPersisted;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncChannel;
import com.tok.pekko.domain.chat.port.out.ChannelActorStoragePort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class ChannelActorStorageAdapter implements ChannelActorStoragePort {

    private static final String NON_ERROR_MESSAGE = "";

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
    public void update(
            Channel channel,
            ActorRef<ChannelEntityCommand> replyTo,
            long batchId,
            List<ChannelDomainEvent> batch
    ) {
        Mono.fromRunnable(() -> channelRepository.update(channel))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(ignored -> replyTo.tell(new ChannelBatchPersisted(batchId, batch, true, NON_ERROR_MESSAGE)))
            .doOnError(error -> replyTo.tell(new ChannelBatchPersisted(batchId, batch, false, error.getMessage())))
            .subscribe();
    }
}
