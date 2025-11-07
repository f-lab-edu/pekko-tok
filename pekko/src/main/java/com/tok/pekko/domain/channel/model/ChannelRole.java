package com.tok.pekko.domain.channel.model;

import java.util.Arrays;

public enum ChannelRole {
    OWNER, MANAGER, MEMBER;

    public static ChannelRole find(String name) {
        return Arrays.stream(ChannelRole.values())
                     .filter(role -> role.name().equalsIgnoreCase(name))
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 역할 입니다."));
    }

    public boolean isOwner() {
        return this == ChannelRole.OWNER;
    }

    public boolean isManager() {
        return this == ChannelRole.MANAGER;
    }

    public boolean isMember() {
        return this == ChannelRole.MEMBER;
    }
}
