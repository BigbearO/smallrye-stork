package io.smallrye.stork.loadbalancer.leastresponsetime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.stork.Stork;
import io.smallrye.stork.api.NoServiceInstanceFoundException;
import io.smallrye.stork.api.Service;
import io.smallrye.stork.api.ServiceDefinition;
import io.smallrye.stork.api.ServiceInstance;
import io.smallrye.stork.loadbalancer.random.RandomConfiguration;
import io.smallrye.stork.servicediscovery.staticlist.StaticConfiguration;
import io.smallrye.stork.test.EmptyServicesConfiguration;
import io.smallrye.stork.test.StorkTestUtils;
import io.smallrye.stork.test.TestConfigProvider;

public class RandomLoadBalancerProgrammaticApiTest {
    private static final Logger log = LoggerFactory.getLogger(RandomLoadBalancerProgrammaticApiTest.class);

    public static final String FST_SRVC_1 = "localhost:8080";
    public static final String FST_SRVC_2 = "localhost:8081";
    private Stork stork;

    @BeforeEach
    void setUp() {
        TestConfigProvider.clear();
        stork = StorkTestUtils.getNewStorkInstance();
        String listOfServices = String.format("%s,%s", FST_SRVC_1, FST_SRVC_2);
        stork.defineIfAbsent("first-service", ServiceDefinition.of(
                new StaticConfiguration().withAddressList(listOfServices), new RandomConfiguration()));
        stork.defineIfAbsent("first-service-secure-random", ServiceDefinition.of(
                new StaticConfiguration().withAddressList(listOfServices),
                new RandomConfiguration().withUseSecureRandom("true")));
        stork.defineIfAbsent("singleton-service", ServiceDefinition.of(
                new StaticConfiguration().withAddressList(FST_SRVC_1), new RandomConfiguration()));
        stork.defineIfAbsent("without-instances", ServiceDefinition.of(
                new EmptyServicesConfiguration(), new RandomConfiguration()));
    }

    @Test
    void shouldPickBothService() {
        Service service = stork.getService("first-service");

        Set<String> instances = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            instances.add(asString(selectInstance(service)));
        }

        assertThat(instances).hasSize(2).contains(FST_SRVC_1, FST_SRVC_2);
    }

    @Test
    void testWithSecureRandom() {
        Service service = stork.getService("first-service-secure-random");

        Set<String> instances = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            instances.add(asString(selectInstance(service)));
        }

        assertThat(instances).hasSize(2).contains(FST_SRVC_1, FST_SRVC_2);
    }

    @Test
    void shouldPickTheServiceWhenOnlyOne() {
        Service service = stork.getService("singleton-service");

        Set<String> instances = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            instances.add(asString(selectInstance(service)));
        }

        assertThat(instances).hasSize(1).contains(FST_SRVC_1);
    }

    @Test
    void shouldThrowNoServiceInstanceOnNoInstances() throws ExecutionException, InterruptedException {
        Service service = stork.getService("without-instances");

        CompletableFuture<Throwable> result = new CompletableFuture<>();

        service.selectInstance().subscribe().with(v -> log.error("Unexpected successful result: {}", v),
                result::complete);

        await().atMost(Duration.ofSeconds(10)).until(result::isDone);
        assertThat(result.get()).isInstanceOf(NoServiceInstanceFoundException.class);
    }

    private ServiceInstance selectInstance(Service service) {
        return service.selectInstance().await().atMost(Duration.ofSeconds(5));
    }

    private String asString(ServiceInstance serviceInstance) {
        try {
            return String.format("%s:%s", serviceInstance.getHost(), serviceInstance.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
