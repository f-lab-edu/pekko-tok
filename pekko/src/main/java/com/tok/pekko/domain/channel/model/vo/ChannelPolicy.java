package com.tok.pekko.domain.channel.model.vo;

public record ChannelPolicy(boolean canEditOwnMessage, boolean canDeleteOwnMessage) {

    public static ChannelPolicy defaultPolicy() {
        return new ChannelPolicy(true, true);
    }

    public ChannelPolicy updateEditOwnMessage(boolean canEdit) {
        return new ChannelPolicy(canEdit, this.canDeleteOwnMessage);
    }

    public ChannelPolicy updateDeleteOwnMessage(boolean canDelete) {
        return new ChannelPolicy(this.canEditOwnMessage, canDelete);
    }
}
