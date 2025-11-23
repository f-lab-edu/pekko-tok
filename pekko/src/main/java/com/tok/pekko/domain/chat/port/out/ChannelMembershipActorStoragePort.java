package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.ChannelEventHandlerCommand;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelMembershipActorStoragePort {

    void join(Long eventId, ChannelId channelId, ChannelMembership channelMembership, ActorRef<ChannelEventHandlerCommand> replyTo);

    void leave(Long eventId, ChannelMembership channelMembership, ActorRef<ChannelEventHandlerCommand> replyTo);

    void inviteUser(Long eventId, ChannelId channelId, ChannelMembership channelMembership, ActorRef<ChannelEventHandlerCommand> replyTo);

    void promoteToManager(Long eventId, ChannelMembership channelMembership, ActorRef<ChannelEventHandlerCommand> replyTo);

    void demoteToMember(Long eventId, ChannelMembership channelMembership, ActorRef<ChannelEventHandlerCommand> replyTo);

    void addPermission(Long eventId, ChannelMembership channelMembership, ChannelPermissionType permission, ActorRef<ChannelEventHandlerCommand> replyTo);

    void removePermission(Long eventId, ChannelMembership channelMembership, ChannelPermissionType permission, ActorRef<ChannelEventHandlerCommand> replyTo);

    void kickMember(Long eventId, ChannelMembership channelMembership, ActorRef<ChannelEventHandlerCommand> replyTo);
}
