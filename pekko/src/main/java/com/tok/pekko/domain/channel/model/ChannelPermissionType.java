package com.tok.pekko.domain.channel.model;

import lombok.Getter;

@Getter
public enum ChannelPermissionType {

    EDIT_CHANNEL_NAME("채널 이름 변경"),

    MESSAGE_EDIT("메시지 수정"),
    MESSAGE_DELETE("메시지 삭제"),

    MEMBER_INVITE("멤버 초대"),
    MEMBER_KICK("멤버 강퇴");

    private final String description;

    ChannelPermissionType(String description) {
        this.description = description;
    }
}
