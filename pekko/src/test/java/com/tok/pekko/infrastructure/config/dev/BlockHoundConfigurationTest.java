package com.tok.pekko.infrastructure.config.dev;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BlockHoundConfigurationTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() {
        BlockHoundTestInstallUtils.install();

        Config config = ConfigFactory.load();
        testKit = ActorTestKit.create(config);
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void BlockHound가_설치되어_있다() {
        // when & then
        assertThatThrownBy(() ->
                Mono.fromCallable(
                            () -> {
                                Thread.sleep(10);
                                return "done";
                            }
                    )
                    .subscribeOn(Schedulers.parallel())
                    .block()).hasCauseInstanceOf(BlockingOperationError.class);
    }

    @Test
    void Pekko_default_dispatcher_스레드는_블로킹이_허용된다() {
        // given
        CompletableFuture<String> result = new CompletableFuture<>();

        Behavior<String> testActor = Behaviors.receive((context, message) -> {
            try {
                String threadName = Thread.currentThread().getName();
                Thread.sleep(10);
                result.complete(threadName);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
            return Behaviors.stopped();
        });

        // when
        ActorRef<String> actor = testKit.spawn(testActor);
        actor.tell("test");

        // then
        assertThatCode(() -> {
            String threadName = result.get(3, TimeUnit.SECONDS);
            assertThat(threadName).contains("pekko");
        }).doesNotThrowAnyException();
    }

    @Test
    void persistence_dispatcher_스레드는_블로킹이_허용된다() {
        // given
        CompletableFuture<String> result = new CompletableFuture<>();

        Behavior<String> testActor = Behaviors.receive((context, message) -> {
            try {
                String threadName = Thread.currentThread().getName();
                Thread.sleep(10);
                result.complete(threadName);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
            return Behaviors.stopped();
        });

        // when
        ActorRef<String> actor = testKit.spawn(
                testActor,
                "persistence-test-actor",
                org.apache.pekko.actor.typed.DispatcherSelector.fromConfig("persistence-dispatcher")
        );
        actor.tell("test");

        // then
        assertThatCode(() -> {
            String threadName = result.get(3, TimeUnit.SECONDS);
            assertThat(threadName).contains("persistence-dispatcher");
        }).doesNotThrowAnyException();
    }

    @Test
    void Reactor_parallel_스레드는_블로킹이_감지된다() {
        // when & then
        assertThatThrownBy(
                () -> Mono.fromCallable(
                                  () -> {
                                      Thread.sleep(10);
                                      return "done";
                                  }
                          )
                          .subscribeOn(Schedulers.parallel())
                          .block()
        ).hasCauseInstanceOf(BlockingOperationError.class)
         .hasMessageContaining("Blocking call!");
    }

    @Test
    void Reactor_single_스레드는_블로킹이_감지된다() {
        // when & then
        assertThatThrownBy(
                () -> Mono.fromCallable(
                                  () -> {
                                      Thread.sleep(10);
                                      return "done";
                                  }
                          )
                          .subscribeOn(Schedulers.single())
                          .block()
        ).hasCauseInstanceOf(BlockingOperationError.class)
         .hasMessageContaining("Blocking call!");
    }

    @Test
    void boundedElastic_스레드는_블로킹이_허용된다() {
        // when & then
        assertThatCode(
                () -> Mono.fromCallable(
                                  () -> {
                                      Thread.sleep(10);
                                      return "done";
                                  }
                          )
                          .subscribeOn(Schedulers.boundedElastic())
                          .block()
        ).doesNotThrowAnyException();
    }
}
