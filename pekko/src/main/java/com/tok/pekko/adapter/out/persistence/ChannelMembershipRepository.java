package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.user.model.vo.UserId;

public interface ChannelMembershipRepository {

    void joinChannel(ChannelMembership channelMembership);

    void leaveChannel(ChannelId channelId, UserId userId);

    void updateRole(ChannelMembership channelMembership);

    void delete(ChannelId channelId, UserId userId);
}
