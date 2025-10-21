package com.tok.pekko.global.config.dev;

import reactor.blockhound.BlockHound;

public class BlockHoundTestInstallUtils {

    public static void install() {
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
