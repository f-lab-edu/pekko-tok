package com.tok.pekko.domain.channel.port.out;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.vo.ChannelId;

public interface ChannelMembershipStoragePort {

    void joinChannel(ChannelId channelId, ChannelMembership channelMembership);

    void leaveChannel(ChannelMembership channelMembership);

    void promoteToManager(ChannelMembership channelMembership);

    void demoteToMember(ChannelMembership channelMembership);

    void addPermission(ChannelMembership channelMembership, ChannelPermissionType permission);

    void removePermission(ChannelMembership channelMembership, ChannelPermissionType permission);

    void kickMember(ChannelMembership channelMembership);
}
