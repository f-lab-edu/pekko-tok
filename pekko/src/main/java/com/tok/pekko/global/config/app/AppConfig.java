package com.tok.pekko.global.config.app;

import java.time.Clock;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TimeZoneProperties.class)
public class AppConfig {

    private final TimeZoneProperties timeZoneProperties;

    @Bean
    public Clock clock() {
        return Clock.system(timeZoneProperties.timezone());
    }

    @Bean(name = "databaseScheduler", destroyMethod = "dispose")
    public Scheduler databaseScheduler() {
        int poolSize = Runtime.getRuntime().availableProcessors() * 2;
        return Schedulers.fromExecutor(
                Executors.newFixedThreadPool(
                        poolSize,
                        runnable -> {
                            Thread thread = new Thread(runnable);

                            thread.setName("db-pool-" + thread.getId());
                            thread.setDaemon(false);
                            return thread;
                        }
                )
        );
    }
}
