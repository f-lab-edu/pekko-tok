package com.tok.pekko.domain.user.model;

import java.util.Arrays;

public enum RegistrationId {

    KAKAO("kakao");

    private final String name;

    RegistrationId(String name) {
        this.name = name;
    }

    public static RegistrationId findBy(String name) {
        return Arrays.stream(RegistrationId.values())
                     .filter(id -> id.name.equalsIgnoreCase(name))
                     .findAny()
                     .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 소셜 로그인 서비스입니다."));
    }
}
