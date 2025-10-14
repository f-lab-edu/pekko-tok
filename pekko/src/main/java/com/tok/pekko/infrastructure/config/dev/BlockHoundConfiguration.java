package com.tok.pekko.infrastructure.config.dev;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.blockhound.BlockHound;

@Slf4j
@Configuration
@Profile("dev")
public class BlockHoundConfiguration {

    @PostConstruct
    public void initialize() {
        BlockHound.install();
    }
}
