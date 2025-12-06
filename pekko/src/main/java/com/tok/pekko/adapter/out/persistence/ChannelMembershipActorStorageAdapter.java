package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class ChannelMembershipActorStorageAdapter implements ChannelMembershipActorStoragePort {

    private final ChannelRepository channelRepository;
    private final ChannelMembershipRepository channelMembershipRepository;
    private final ChannelManagePermissionRepository channelManagePermissionRepository;

    @Override
    public void joinChannel(ChannelId channelId, ChannelMembership channelMembership) {
        Mono.fromRunnable(() -> channelMembershipRepository.joinChannel(channelMembership))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    @Override
    public void leaveChannel(ChannelMembership channelMembership) {
        Mono.fromRunnable(() -> {
                channelManagePermissionRepository.deleteAll(channelMembership.getId());
                channelMembershipRepository.leaveChannel(channelMembership.getChannelId(), channelMembership.getUserId());
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    @Override
    public void leaveChannel(ChannelId channelId, UserId userId) {
        Mono.fromRunnable(() -> {
                channelManagePermissionRepository.deleteAll(channelId, userId);
                channelMembershipRepository.leaveChannel(channelId, userId);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    @Override
    public void promoteToManager(ChannelMembership channelMembership) {
        Mono.fromRunnable(() -> {
                channelMembershipRepository.updateRole(channelMembership);
                channelManagePermissionRepository.saveAll(channelMembership);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    @Override
    public void demoteToMember(ChannelMembership channelMembership) {
        Mono.fromRunnable(() -> {
                channelMembershipRepository.updateRole(channelMembership);
                channelManagePermissionRepository.deleteAll(channelMembership.getId());
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    @Override
    public void addPermission(ChannelMembership channelMembership, ChannelPermissionType permission) {
        Mono.fromRunnable(() -> channelManagePermissionRepository.save(channelMembership, permission))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    @Override
    public void removePermission(ChannelMembership channelMembership, ChannelPermissionType permission) {
        Mono.fromRunnable(() -> channelManagePermissionRepository.delete(channelMembership, permission))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    @Override
    public void kickMember(ChannelMembership channelMembership) {
        Mono.fromRunnable(() -> {
                channelMembershipRepository.delete(channelMembership.getChannelId(), channelMembership.getUserId());
                channelManagePermissionRepository.deleteAll(channelMembership.getId());
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    @Override
    public void kickMember(ChannelId channelId, UserId userId) {
        Mono.fromRunnable(() -> {
                channelMembershipRepository.delete(channelId, userId);
                channelManagePermissionRepository.deleteAll(channelId, userId);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }
}
