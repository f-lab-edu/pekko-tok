package com.tok.pekko.infrastructure.config.dev;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.blockhound.BlockHound;

@Configuration
@Profile("dev")
public class BlockHoundConfiguration {

    @PostConstruct
    public void initialize() {
        BlockHound.builder()
                  .nonBlockingThreadPredicate(
                          current ->
                                  thread -> {
                                      String name = thread.getName();

                                      if (name.contains("ChatCluster") ||
                                              name.contains("pekko") ||
                                              name.contains("akka")) {
                                          return false;
                                      }

                                      return name.startsWith("reactor-http-nio-") ||
                                              name.startsWith("parallel-") ||
                                              name.startsWith("elastic-") ||
                                              name.startsWith("netty-") ||
                                              name.startsWith("single-");
                                  }
                  )
                  .allowBlockingCallsInside(
                          "java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue",
                          "take"
                  )
                  .allowBlockingCallsInside(
                          "java.util.concurrent.locks.ReentrantLock$Sync",
                          "tryRelease"
                  )
                  .allowBlockingCallsInside(
                          "java.util.concurrent.locks.AbstractQueuedSynchronizer",
                          "release"
                  )
                  .allowBlockingCallsInside(
                          "java.util.concurrent.locks.ReentrantLock",
                          "unlock"
                  )
                  .install();
    }
}
