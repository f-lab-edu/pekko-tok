package com.tok.pekko.global.config.app;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TimeZoneProperties.class)
public class AppConfig {

    private final TimeZoneProperties timeZoneProperties;

    @Bean
    public Clock clock() {
        return Clock.system(timeZoneProperties.timezone());
    }
}
