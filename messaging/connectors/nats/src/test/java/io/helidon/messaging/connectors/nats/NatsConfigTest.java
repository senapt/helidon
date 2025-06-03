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

import java.time.Duration;

import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for NATS connector configuration.
 */
class NatsConfigTest {

    @Test
    void testConfigBuilder() {
        Config config = NatsConnector.configBuilder()
                .url("nats://localhost:4222")
                .subject("test.subject")
                .connectionTimeout(Duration.ofSeconds(10))
                .maxReconnects(5)
                .authToken("test-token")
                .tlsEnabled(true)
                .jetstream(true)
                .stream("TEST_STREAM")
                .build();

        assertThat(config.get("url").asString().orElse(""), is("nats://localhost:4222"));
        assertThat(config.get("subject").asString().orElse(""), is("test.subject"));
        assertThat(config.get("connection-timeout").asString().orElse(""), is("10"));
        assertThat(config.get("max-reconnects").asString().orElse(""), is("5"));
        assertThat(config.get("auth-token").asString().orElse(""), is("test-token"));
        assertThat(config.get("tls-enabled").asString().orElse(""), is("true"));
        assertThat(config.get("jetstream").asString().orElse(""), is("true"));
        assertThat(config.get("stream").asString().orElse(""), is("TEST_STREAM"));
    }

    @Test
    void testConnectorCreation() {
        Config config = NatsConnector.configBuilder()
                .url("nats://localhost:4222")
                .subject("test.subject")
                .build();

        NatsConnector connector = NatsConnector.create(config);
        assertThat(connector.publishers().isEmpty(), is(true));
        assertThat(connector.subscribers().isEmpty(), is(true));
        
        connector.stop();
    }

    @Test
    void testEmptyConnectorCreation() {
        NatsConnector connector = NatsConnector.create();
        assertThat(connector.publishers().isEmpty(), is(true));
        assertThat(connector.subscribers().isEmpty(), is(true));
        
        connector.stop();
    }

    @Test
    void testNatsMessageCreation() {
        String payload = "test message";
        NatsMessage<String> message = NatsMessage.of(payload);
        
        assertThat(message.getPayload(), is(payload));
        assertThat(message.getNatsMessage().isEmpty(), is(true));
        assertThat(message.getSubject().isEmpty(), is(true));
        assertThat(message.getReplyTo().isEmpty(), is(true));
        assertThat(message.getHeaders().isEmpty(), is(true));
    }

    @Test
    void testNatsMessageBuilder() {
        String payload = "test message";
        NatsMessage<String> message = NatsMessage.<String>builder()
                .payload(payload)
                .build();
        
        assertThat(message.getPayload(), is(payload));
    }

    @Test
    void testNatsPublisherBuilderRequiredConfig() {
        assertThrows(IllegalArgumentException.class, () -> 
            NatsPublisher.builder().build());
    }

    @Test
    void testNatsSubscriberBuilderRequiredConfig() {
        assertThrows(IllegalArgumentException.class, () -> 
            NatsSubscriber.builder().build());
    }
}