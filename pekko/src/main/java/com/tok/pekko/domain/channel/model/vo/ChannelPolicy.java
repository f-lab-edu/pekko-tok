package com.tok.pekko.domain.channel.model.vo;

public record ChannelPolicy(boolean canEditOwnMessage, boolean canDeleteOwnMessage, boolean isPublic) {

    public static ChannelPolicy defaultPolicy() {
        return new ChannelPolicy(true, true, true);
    }

    public ChannelPolicy updateEditOwnMessage(boolean canEdit) {
        return new ChannelPolicy(canEdit, this.canDeleteOwnMessage, this.isPublic);
    }

    public ChannelPolicy updateDeleteOwnMessage(boolean canDelete) {
        return new ChannelPolicy(this.canEditOwnMessage, canDelete, this.isPublic);
    }

    public ChannelPolicy updatePublic(boolean isPublic) {
        return new ChannelPolicy(this.canEditOwnMessage, this.canDeleteOwnMessage, isPublic);
    }
}
