package com.tok.pekko.global.config.app;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record TimeZoneProperties(ZoneId timezone) {
}
