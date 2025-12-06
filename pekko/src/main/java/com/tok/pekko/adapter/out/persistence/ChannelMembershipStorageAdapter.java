package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.port.out.ChannelMembershipStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChannelMembershipStorageAdapter implements ChannelMembershipStoragePort {

    private final ChannelMembershipRepository channelMembershipRepository;
    private final ChannelManagePermissionRepository channelManagePermissionRepository;

    @Override
    public void joinChannel(ChannelId channelId, ChannelMembership channelMembership) {
        channelMembershipRepository.joinChannel(channelMembership);
    }

    @Override
    public void leaveChannel(ChannelMembership channelMembership) {
        channelManagePermissionRepository.deleteAll(channelMembership.getId());
        channelMembershipRepository.leaveChannel(channelMembership.getChannelId(), channelMembership.getUserId());
    }

    @Override
    public void promoteToManager(ChannelMembership channelMembership) {
        channelMembershipRepository.updateRole(channelMembership);
        channelManagePermissionRepository.saveAll(channelMembership);
    }

    @Override
    public void demoteToMember(ChannelMembership channelMembership) {
        channelMembershipRepository.updateRole(channelMembership);
        channelManagePermissionRepository.deleteAll(channelMembership.getId());
    }

    @Override
    public void addPermission(ChannelMembership channelMembership, ChannelPermissionType permission) {
        channelManagePermissionRepository.save(channelMembership, permission);
    }

    @Override
    public void removePermission(ChannelMembership channelMembership, ChannelPermissionType permission) {
        channelManagePermissionRepository.delete(channelMembership, permission);
    }

    @Override
    public void kickMember(ChannelMembership channelMembership) {
        channelMembershipRepository.delete(channelMembership.getChannelId(), channelMembership.getUserId());
        channelManagePermissionRepository.deleteAll(channelMembership.getId());
    }
}
