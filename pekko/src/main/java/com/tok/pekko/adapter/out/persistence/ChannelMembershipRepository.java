package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelMembershipId;
import com.tok.pekko.domain.user.model.vo.UserId;

public interface ChannelMembershipRepository {

    ChannelMembership joinChannel(ChannelMembership channelMembership);

    void leaveChannel(ChannelMembership channelMembership);

    void promoteToManager(ChannelMembership channelMembership);

    void demoteToMember(ChannelMembership channelMembership);

    void delete(ChannelId channelId, UserId userId);

    void addManagerPermission(ChannelMembershipId membershipId, ChannelPermissionType permission);

    void deleteManagerPermission(ChannelMembershipId membershipId, ChannelPermissionType permission);
}
