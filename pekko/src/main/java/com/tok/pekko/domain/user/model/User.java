package com.tok.pekko.domain.user.model;

import com.tok.pekko.domain.user.model.vo.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(of = "id")
public class User {

    private final UserId id;
    private final RegistrationId registrationId;
    private final String socialId;

    public static User create(RegistrationId registrationId, String socialId) {
        return new User(UserId.EMPTY_USER_ID, registrationId, socialId);
    }

    public static User create(Long id, RegistrationId registrationId, String socialId) {
        return new User(UserId.create(id), registrationId, socialId);
    }

    private User(UserId id, RegistrationId registrationId, String socialId) {
        this.id = id;
        this.registrationId = registrationId;
        this.socialId = socialId;
    }

    public User withAssignedId(Long id) {
        return new User(UserId.create(id), this.registrationId, this.socialId);
    }
}
