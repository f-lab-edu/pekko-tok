package com.tok.pekko.global.config.dev;

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
                  .allowBlockingCallsInside(
                          "com.tok.pekko.adapter.out.persistence.JdbcChannelManagePermissionRepository",
                          ""
                  )
                  .allowBlockingCallsInside(
                          "com.tok.pekko.adapter.out.persistence.JdbcChannelMembershipRepository",
                          ""
                  )
                  .allowBlockingCallsInside(
                          "com.tok.pekko.adapter.out.persistence.JdbcChannelMemberViewRepository",
                          ""
                  )
                  .allowBlockingCallsInside(
                          "com.tok.pekko.adapter.out.persistence.JdbcChannelRepository",
                          ""
                  )
                  .allowBlockingCallsInside(
                          "com.tok.pekko.adapter.out.persistence.JdbcMessageRepository",
                          ""
                  )
                  .allowBlockingCallsInside(
                          "com.tok.pekko.adapter.out.persistence.JdbcParticipatingChannelRepository",
                          ""
                  )
                  .allowBlockingCallsInside(
                          "io.netty.channel.nio.SelectedSelectionKeySet",
                          ""
                  )
                  .allowBlockingCallsInside(
                          "io.netty.channel.nio.NioEventLoop",
                          ""
                  )
                  .allowBlockingCallsInside(
                          "org.springframework.http.server.reactive.ReactorHttpHandlerAdapter",
                          ""
                  )
                  .allowBlockingCallsInside(
                          "org.springframework.boot.web.embedded.netty.GracefulShutdown",
                          ""
                  )
                  .allowBlockingCallsInside(
                          "org.springframework.context.support.DefaultLifecycleProcessor",
                          ""
                  )
                  .install();
    }
}
