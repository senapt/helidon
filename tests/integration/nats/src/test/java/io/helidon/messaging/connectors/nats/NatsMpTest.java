/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.messaging.connectors.nats;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.mp.MpConfigProviderResolver;
import io.helidon.microprofile.messaging.MessagingCdiExtension;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.CDI;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test NATS connector with MicroProfile (CDI).
 */
public class NatsMpTest extends AbstractNatsTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String TEST_SUBJECT_MP = "test.mp.subject";

    protected static final Connector NATS_CONNECTOR_LITERAL = new Connector() {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Connector.class;
        }

        @Override
        public String value() {
            return NatsConnector.CONNECTOR_NAME;
        }
    };

    private static SeContainer cdiContainer;

    @BeforeAll
    static void startCdi() {
        Config config = Config.builder()
                .sources(ConfigSources.create(
                        "mp.messaging.connector.helidon-nats.url", NATS_SERVER,
                        "mp.messaging.outgoing.to-nats.connector", "helidon-nats",
                        "mp.messaging.outgoing.to-nats.subject", TEST_SUBJECT_MP,
                        "mp.messaging.incoming.from-nats.connector", "helidon-nats",
                        "mp.messaging.incoming.from-nats.subject", TEST_SUBJECT_MP
                ))
                .build();

        MpConfigProviderResolver.instance().registerConfig(config, Thread.currentThread().getContextClassLoader());

        cdiContainer = SeContainerInitializer.newInstance()
                .addExtensions(MessagingCdiExtension.class)
                .addBeanClasses(TestBean.class)
                .initialize();
    }

    @AfterAll
    static void stopCdi() {
        if (cdiContainer != null) {
            cdiContainer.close();
        }
        ConfigProviderResolver.instance().releaseConfig(
                ConfigProviderResolver.instance().getConfig());
    }

    @Test
    void testConnectorAvailability() {
        NatsConnector natsConnector = CDI.current()
                .select(NatsConnector.class, NATS_CONNECTOR_LITERAL)
                .get();

        assertThat(natsConnector, is(notNullValue()));
        assertThat(natsConnector.publishers(), is(notNullValue()));
        assertThat(natsConnector.subscribers(), is(notNullValue()));
    }

    @Test
    void testMpMessaging() throws InterruptedException {
        TestBean testBean = cdiContainer.select(TestBean.class).get();

        // Wait for some messages to be processed
        await().atMost(TIMEOUT).until(() -> testBean.getReceivedMessages().size() >= 3);

        assertThat(testBean.getReceivedMessages(), hasSize(3));
    }

    /**
     * Test bean for MicroProfile messaging.
     */
    public static class TestBean {
        private final List<String> receivedMessages = new ArrayList<>();
        private final CountDownLatch latch = new CountDownLatch(3);

        @Outgoing("to-nats")
        public PublisherBuilder<String> generate() {
            return ReactiveStreams.of("MP Message 1", "MP Message 2", "MP Message 3");
        }

        @Incoming("from-nats")
        public void consume(String message) {
            receivedMessages.add(message);
            latch.countDown();
        }

        public List<String> getReceivedMessages() {
            return new ArrayList<>(receivedMessages);
        }

        public boolean await(Duration timeout) throws InterruptedException {
            return latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}