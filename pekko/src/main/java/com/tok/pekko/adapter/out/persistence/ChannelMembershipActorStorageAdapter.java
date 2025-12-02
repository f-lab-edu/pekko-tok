package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.ChannelEventHandlerCommand;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.EventFailed;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.EventSucceeded;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.NotifyStoredMembership;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorStoragePort;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Component
@RequiredArgsConstructor
public class ChannelMembershipActorStorageAdapter implements ChannelMembershipActorStoragePort {

    private final Scheduler databaseScheduler;
    private final ChannelMembershipRepository channelMembershipRepository;

    @Override
    public void join(
            Long eventId,
            ChannelId channelId,
            ChannelMembership channelMembership,
            ActorRef<ChannelEventHandlerCommand> replyTo
    ) {
        Mono.fromCallable(() -> channelMembershipRepository.joinChannel(channelMembership))
            .subscribeOn(databaseScheduler)
            .doOnSuccess(stored -> {
                replyTo.tell(new NotifyStoredMembership(stored));
                replyTo.tell(new EventSucceeded(eventId));
            })
            .doOnError(throwable -> replyTo.tell(new EventFailed(eventId, throwable)))
            .subscribe();
    }

    @Override
    public void leave(Long eventId, ChannelMembership channelMembership, ActorRef<ChannelEventHandlerCommand> replyTo) {
        Mono.fromRunnable(() -> channelMembershipRepository.leaveChannel(channelMembership))
            .subscribeOn(databaseScheduler)
            .doOnSuccess(ignored -> replyTo.tell(new EventSucceeded(eventId)))
            .doOnError(throwable -> replyTo.tell(new EventFailed(eventId, throwable)))
            .subscribe();
    }

    @Override
    public void inviteUser(
            Long eventId,
            ChannelId channelId,
            ChannelMembership channelMembership,
            ActorRef<ChannelEventHandlerCommand> replyTo
    ) {
        Mono.fromCallable(() -> channelMembershipRepository.joinChannel(channelMembership))
            .subscribeOn(databaseScheduler)
            .doOnSuccess(stored -> {
                replyTo.tell(new NotifyStoredMembership(stored));
                replyTo.tell(new EventSucceeded(eventId));
            })
            .doOnError(throwable -> replyTo.tell(new EventFailed(eventId, throwable)))
            .subscribe();
    }

    @Override
    public void promoteToManager(
            Long eventId,
            ChannelMembership channelMembership,
            ActorRef<ChannelEventHandlerCommand> replyTo
    ) {
        Mono.fromRunnable(() -> channelMembershipRepository.promoteToManager(channelMembership))
            .subscribeOn(databaseScheduler)
            .doOnSuccess(ignored -> replyTo.tell(new EventSucceeded(eventId)))
            .doOnError(throwable -> replyTo.tell(new EventFailed(eventId, throwable)))
            .subscribe();
    }

    @Override
    public void demoteToMember(
            Long eventId,
            ChannelMembership channelMembership,
            ActorRef<ChannelEventHandlerCommand> replyTo
    ) {
        Mono.fromRunnable(() -> channelMembershipRepository.demoteToMember(channelMembership))
            .subscribeOn(databaseScheduler)
            .doOnSuccess(ignored -> replyTo.tell(new EventSucceeded(eventId)))
            .doOnError(throwable -> replyTo.tell(new EventFailed(eventId, throwable)))
            .subscribe();
    }

    @Override
    public void addPermission(
            Long eventId,
            ChannelMembership channelMembership,
            ChannelPermissionType permission,
            ActorRef<ChannelEventHandlerCommand> replyTo
    ) {
        Mono.fromRunnable(() -> channelMembershipRepository.addManagerPermission(channelMembership.getId(), permission))
            .subscribeOn(databaseScheduler)
            .doOnSuccess(ignored -> replyTo.tell(new EventSucceeded(eventId)))
            .doOnError(throwable -> replyTo.tell(new EventFailed(eventId, throwable)))
            .subscribe();
    }

    @Override
    public void removePermission(
            Long eventId,
            ChannelMembership channelMembership,
            ChannelPermissionType permission,
            ActorRef<ChannelEventHandlerCommand> replyTo
    ) {
        Mono.fromRunnable(() -> channelMembershipRepository.deleteManagerPermission(channelMembership.getId(), permission))
            .subscribeOn(databaseScheduler)
            .doOnSuccess(ignored -> replyTo.tell(new EventSucceeded(eventId)))
            .doOnError(throwable -> replyTo.tell(new EventFailed(eventId, throwable)))
            .subscribe();
    }

    @Override
    public void kickMember(
            Long eventId,
            ChannelMembership channelMembership,
            ActorRef<ChannelEventHandlerCommand> replyTo
    ) {
        Mono.fromRunnable(() -> channelMembershipRepository.leaveChannel(channelMembership))
            .subscribeOn(databaseScheduler)
            .doOnSuccess(ignored -> replyTo.tell(new EventSucceeded(eventId)))
            .doOnError(throwable -> replyTo.tell(new EventFailed(eventId, throwable)))
            .subscribe();
    }
}
