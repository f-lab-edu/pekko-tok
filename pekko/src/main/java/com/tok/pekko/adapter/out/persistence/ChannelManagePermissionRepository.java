package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.vo.ChannelMembershipId;

public interface ChannelManagePermissionRepository {

    void save(ChannelMembership channelMembership, ChannelPermissionType permission);

    void saveAll(ChannelMembership channelMembership);

    void delete(ChannelMembership channelMembership, ChannelPermissionType permission);

    void deleteAll(ChannelMembershipId channelMembershipId);
}
