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
import io.helidon.config.ConfigSources;

/**
 * Builder for NATS connector configuration.
 */
public class NatsConfigBuilder {

    private final Config.Builder configBuilder;

    /**
     * Create a new config builder.
     */
    public NatsConfigBuilder() {
        this.configBuilder = Config.builder();
    }

    /**
     * Set the NATS server URL.
     *
     * @param url the server URL
     * @return this builder
     */
    public NatsConfigBuilder url(String url) {
        configBuilder.addSource(ConfigSources.create("url", url));
        return this;
    }

    /**
     * Set the NATS subject.
     *
     * @param subject the subject
     * @return this builder
     */
    public NatsConfigBuilder subject(String subject) {
        configBuilder.addSource(ConfigSources.create("subject", subject));
        return this;
    }

    /**
     * Set the connection timeout.
     *
     * @param timeout the timeout duration
     * @return this builder
     */
    public NatsConfigBuilder connectionTimeout(Duration timeout) {
        configBuilder.addSource(ConfigSources.create("connection-timeout", String.valueOf(timeout.toSeconds())));
        return this;
    }

    /**
     * Set the maximum number of reconnection attempts.
     *
     * @param maxReconnects the maximum reconnects
     * @return this builder
     */
    public NatsConfigBuilder maxReconnects(int maxReconnects) {
        configBuilder.addSource(ConfigSources.create("max-reconnects", String.valueOf(maxReconnects)));
        return this;
    }

    /**
     * Set the reconnection wait time.
     *
     * @param wait the wait duration
     * @return this builder
     */
    public NatsConfigBuilder reconnectWait(Duration wait) {
        configBuilder.addSource(ConfigSources.create("reconnect-wait", String.valueOf(wait.toSeconds())));
        return this;
    }

    /**
     * Set the authentication token.
     *
     * @param token the auth token
     * @return this builder
     */
    public NatsConfigBuilder authToken(String token) {
        configBuilder.addSource(ConfigSources.create("auth-token", token));
        return this;
    }

    /**
     * Set the username for authentication.
     *
     * @param username the username
     * @return this builder
     */
    public NatsConfigBuilder username(String username) {
        configBuilder.addSource(ConfigSources.create("username", username));
        return this;
    }

    /**
     * Set the password for authentication.
     *
     * @param password the password
     * @return this builder
     */
    public NatsConfigBuilder password(String password) {
        configBuilder.addSource(ConfigSources.create("password", password));
        return this;
    }

    /**
     * Enable or disable TLS.
     *
     * @param tlsEnabled true to enable TLS
     * @return this builder
     */
    public NatsConfigBuilder tlsEnabled(boolean tlsEnabled) {
        configBuilder.addSource(ConfigSources.create("tls-enabled", String.valueOf(tlsEnabled)));
        return this;
    }

    /**
     * Set the queue group for load balancing.
     *
     * @param queueGroup the queue group name
     * @return this builder
     */
    public NatsConfigBuilder queueGroup(String queueGroup) {
        configBuilder.addSource(ConfigSources.create("queue-group", queueGroup));
        return this;
    }

    /**
     * Enable or disable JetStream.
     *
     * @param jetstream true to enable JetStream
     * @return this builder
     */
    public NatsConfigBuilder jetstream(boolean jetstream) {
        configBuilder.addSource(ConfigSources.create("jetstream", String.valueOf(jetstream)));
        return this;
    }

    /**
     * Set the JetStream stream name.
     *
     * @param stream the stream name
     * @return this builder
     */
    public NatsConfigBuilder stream(String stream) {
        configBuilder.addSource(ConfigSources.create("stream", stream));
        return this;
    }

    /**
     * Set the JetStream consumer name.
     *
     * @param consumer the consumer name
     * @return this builder
     */
    public NatsConfigBuilder consumer(String consumer) {
        configBuilder.addSource(ConfigSources.create("consumer", consumer));
        return this;
    }

    /**
     * Set the JetStream acknowledgment wait time.
     *
     * @param ackWait the acknowledgment wait duration
     * @return this builder
     */
    public NatsConfigBuilder ackWait(Duration ackWait) {
        configBuilder.addSource(ConfigSources.create("ack-wait", String.valueOf(ackWait.toSeconds())));
        return this;
    }

    /**
     * Build the configuration.
     *
     * @return the built configuration
     */
    public Config build() {
        return configBuilder.build();
    }
}
