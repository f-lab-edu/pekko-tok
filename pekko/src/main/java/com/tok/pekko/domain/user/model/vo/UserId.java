package com.tok.pekko.domain.user.model.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class UserId {

    public static final UserId EMPTY_USER_ID = new UserId(null);

    private final Long value;

    public static UserId create(Long value) {
        validateValue(value);

        return new UserId(value);
    }

    private static void validateValue(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("사용자 ID는 양수여야 합니다.");
        }
    }

    private UserId(Long value) {
        this.value = value;
    }

    public boolean isEqualId(Long id) {
        return this.value.equals(id);
    }
}
