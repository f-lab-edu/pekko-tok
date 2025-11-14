package com.tok.pekko.application.channel.exception;

public class ChannelNotFoundException extends IllegalArgumentException {

    public ChannelNotFoundException() {
        super("지정한 ID에 해당하는 채널을 찾을 수 없습니다.");
    }
}
