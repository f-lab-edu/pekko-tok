package com.tok.pekko.domain.chat.model;

public class MessageSequenceGenerator {

    private long nextSequence;

    public MessageSequenceGenerator(long nextSequence) {
        this.nextSequence = nextSequence;
    }

    public long getNextSequence() {
        return ++nextSequence;
    }
}
