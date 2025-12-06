package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.user.model.vo.UserId;

public interface ChannelMembershipActorStoragePort {

    void joinChannel(ChannelId channelId, ChannelMembership channelMembership);

    void leaveChannel(ChannelMembership channelMembership);

    void leaveChannel(ChannelId channelId, UserId userId);

    void promoteToManager(ChannelMembership channelMembership);

    void demoteToMember(ChannelMembership channelMembership);

    void addPermission(ChannelMembership channelMembership, ChannelPermissionType permission);

    void removePermission(ChannelMembership channelMembership, ChannelPermissionType permission);

    void kickMember(ChannelMembership channelMembership);

    void kickMember(ChannelId channelId, UserId userId);
}
