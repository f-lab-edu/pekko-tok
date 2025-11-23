package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.global.common.CborSerializable;

public interface InviteUserEventProtocol {

    interface InviteUserEventCommand extends CborSerializable { }

    record Invited(Long channelId, Long inviteeId) implements InviteUserEventCommand { }
}
