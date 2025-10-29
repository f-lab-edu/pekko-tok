package com.tok.pekko.global.config.actor;

import com.tok.pekko.domain.chat.model.ChatChannelEntity;
import com.tok.pekko.domain.chat.model.ChatMessages;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.tok.pekko.global.actor.GuardianActor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.Address;
import org.apache.pekko.actor.AddressFromURIString;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.JoinSeedNodes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("!test")
@RequiredArgsConstructor
public class ClusterConfig {

    private final Environment environment;
    private final Clock clock;

    @Bean
    public ClusterSharding clusterSharding(MessageStoragePort messageStoragePort) {
        ClusterSharding clusterSharding = ClusterSharding.get(actorSystem());

        clusterSharding.init(
                Entity.of(
                        ChatChannelEntity.ENTITY_TYPE_KEY,
                        entityContext -> ChatChannelEntity.create(
                                clock,
                                Long.valueOf(entityContext.getEntityId()),
                                new ChatMessages(),
                                messageStoragePort
                        )
                )
        );

        return ClusterSharding.get(actorSystem());
    }

    @Bean(destroyMethod = "terminate")
    public ActorSystem<SpawnProtocol.Command> actorSystem() {
        Config config = buildConfig();
        ActorSystem<SpawnProtocol.Command> system = ActorSystem.create(
                GuardianActor.create(),
                "ChatCluster",
                config
        );

        joinClusterSeeds(system);
        logClusterInfo(system);

        return system;
    }

    private Config buildConfig() {
        Config base = ConfigFactory.load();
        Config profileConfig = loadProfileBasedConfig();
        return profileConfig.withFallback(base);
    }

    private Config loadProfileBasedConfig() {
        try {
            return ConfigFactory.load(calculateConfigName());
        } catch (Exception ignored) {
            return ConfigFactory.empty();
        }
    }

    private String calculateConfigName() {
        return "application-" + getActiveProfile();
    }

    private String getActiveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();

        if (activeProfiles.length > 0) {
            return activeProfiles[0];
        }

        String[] defaultProfiles = environment.getDefaultProfiles();

        if (defaultProfiles.length > 0) {
            return defaultProfiles[0];
        }

        return "default";
    }

    private String getEnvironmentVariable(String key) {
        String systemProp = System.getProperty(key);
        return systemProp != null ? systemProp : System.getenv(key);
    }

    private void joinClusterSeeds(ActorSystem<SpawnProtocol.Command> system) {
        String joinSeeds = getEnvironmentVariable("PEKKO_JOIN_SEEDS");

        if (joinSeeds == null || joinSeeds.isBlank()) {
            return;
        }

        try {
            List<Address> seedAddresses = parseSeedAddresses(joinSeeds);
            Cluster cluster = Cluster.get(system);

            cluster.manager().tell(
                    new JoinSeedNodes(
                            scala.jdk.javaapi.CollectionConverters
                                    .asScala(seedAddresses)
                                    .toSeq()
                    )
            );
        } catch (Exception e) {
            system.log().warn(
                    "Failed to parse PEKKO_JOIN_SEEDS='{}': {}",
                    joinSeeds,
                    e.toString()
            );
        }
    }

    private List<Address> parseSeedAddresses(String joinSeeds) {
        String[] addresses = joinSeeds.split(",");
        List<Address> seedAddresses = new ArrayList<>();

        for (String address : addresses) {
            seedAddresses.add(AddressFromURIString.parse(address.trim()));
        }

        return seedAddresses;
    }

    private void logClusterInfo(ActorSystem<SpawnProtocol.Command> system) {
        Cluster cluster = Cluster.get(system);
        Config config = system.settings()
                              .config();

        system.log()
              .info(
                      "Pekko Typed cluster started on {}:{}; selfAddress={} roles={}",
                      config.getString("pekko.remote.artery.canonical.hostname"),
                      config.getInt("pekko.remote.artery.canonical.port"),
                      cluster.selfMember().address(),
                      cluster.selfMember().roles()
              );
    }
}
