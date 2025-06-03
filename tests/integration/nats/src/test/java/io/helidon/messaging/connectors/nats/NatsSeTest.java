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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import io.helidon.common.reactive.Multi;
import io.helidon.config.Config;
import io.helidon.messaging.Channel;
import io.helidon.messaging.Messaging;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Test NATS connector with Helidon SE.
 */
public class NatsSeTest extends AbstractNatsTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String TEST_SUBJECT_1 = "test.se.subject.1";
    private static final String TEST_SUBJECT_2 = "test.se.subject.2";

    @Test
    void testBasicPubSub() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(5);
        List<String> receivedMessages = new ArrayList<>();

        // Configure outgoing channel
        Channel<String> toNats = Channel.<String>builder()
                .name("to-nats")
                .subscriberConfig(NatsConnector.configBuilder()
                        .url(NATS_SERVER)
                        .subject(TEST_SUBJECT_1)
                        .build())
                .build();

        // Configure incoming channel
        Channel<String> fromNats = Channel.<String>builder()
                .name("from-nats")
                .publisherConfig(NatsConnector.configBuilder()
                        .url(NATS_SERVER)
                        .subject(TEST_SUBJECT_1)
                        .build())
                .build();

        NatsConnector natsConnector = NatsConnector.create(Config.empty());

        Messaging messaging = Messaging.builder()
                .connector(natsConnector)
                .publisher(toNats,
                        Multi.create(IntStream.rangeClosed(1, 5).boxed()
                                .map(i -> "Message " + i))
                                .map(Message::of))
                .listener(fromNats, payload -> {
                    receivedMessages.add(payload);
                    countDownLatch.countDown();
                })
                .build();

        try {
            messaging.start();

            // Wait for messages to be received
            assertThat("Messages were received within timeout", 
                      countDownLatch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS), 
                      is(true));
            
            assertThat(receivedMessages, hasSize(5));
            assertThat(receivedMessages, containsInAnyOrder(
                    "Message 1", "Message 2", "Message 3", "Message 4", "Message 5"));
        } finally {
            messaging.stop();
        }
    }

    @Test
    void testConnectorConfiguration() {
        Config config = NatsConnector.configBuilder()
                .url(NATS_SERVER)
                .subject(TEST_SUBJECT_2)
                .connectionTimeout(Duration.ofSeconds(10))
                .maxReconnects(5)
                .build();

        NatsConnector connector = NatsConnector.create(config);
        
        try {
            // Just verify the connector can be created and stopped
            assertThat(connector.publishers().isEmpty(), is(true));
            assertThat(connector.subscribers().isEmpty(), is(true));
        } finally {
            connector.stop();
        }
    }

    @Test
    void testDirectNatsPublish() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> received = new ArrayList<>();

        // Configure incoming channel only
        Channel<String> fromNats = Channel.<String>builder()
                .name("from-nats")
                .publisherConfig(NatsConnector.configBuilder()
                        .url(NATS_SERVER)
                        .subject(TEST_SUBJECT_2)
                        .build())
                .build();

        NatsConnector natsConnector = NatsConnector.create(Config.empty());

        Messaging messaging = Messaging.builder()
                .connector(natsConnector)
                .listener(fromNats, payload -> {
                    received.add(payload);
                    latch.countDown();
                })
                .build();

        try {
            messaging.start();

            // Publish directly via NATS client
            natsConnection.publish(TEST_SUBJECT_2, "Direct message".getBytes(StandardCharsets.UTF_8));

            // Wait for message
            assertThat("Message received within timeout",
                      latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                      is(true));

            assertThat(received, hasSize(1));
            assertThat(received.get(0), is("Direct message"));
        } finally {
            messaging.stop();
        }
    }
}