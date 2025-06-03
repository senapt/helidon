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

import io.helidon.messaging.ConnectorConfigBuilder;

import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;


/**
 * Builder for NATS connector configuration.
 */
public final class NatsConfigBuilder extends ConnectorConfigBuilder {

    /**
     * Create a new config builder.
     */
    public NatsConfigBuilder() {
        super();
        super.property(ConnectorFactory.CONNECTOR_ATTRIBUTE, NatsConnector.CONNECTOR_NAME);
    }

    /**
     * Set the NATS server URL.
     *
     * @param url the server URL
     * @return this builder
     */
    public NatsConfigBuilder url(String url) {
        super.property("url", url);
        return this;
    }

    /**
     * Set the NATS subject.
     *
     * @param subject the subject
     * @return this builder
     */
    public NatsConfigBuilder subject(String subject) {
        super.property("subject", subject);
        return this;
    }

    /**
     * Set the connection timeout.
     *
     * @param timeout the timeout duration
     * @return this builder
     */
    public NatsConfigBuilder connectionTimeout(Duration timeout) {
        super.property("connection-timeout", String.valueOf(timeout.toSeconds()));
        return this;
    }

    /**
     * Set the maximum number of reconnection attempts.
     *
     * @param maxReconnects the maximum reconnects
     * @return this builder
     */
    public NatsConfigBuilder maxReconnects(int maxReconnects) {
        super.property("max-reconnects", String.valueOf(maxReconnects));
        return this;
    }

    /**
     * Set the reconnection wait time.
     *
     * @param wait the wait duration
     * @return this builder
     */
    public NatsConfigBuilder reconnectWait(Duration wait) {
        super.property("reconnect-wait", String.valueOf(wait.toSeconds()));
        return this;
    }

    /**
     * Set the authentication token.
     *
     * @param token the auth token
     * @return this builder
     */
    public NatsConfigBuilder authToken(String token) {
        super.property("auth-token", token);
        return this;
    }

    /**
     * Set the username for authentication.
     *
     * @param username the username
     * @return this builder
     */
    public NatsConfigBuilder username(String username) {
        super.property("username", username);
        return this;
    }

    /**
     * Set the password for authentication.
     *
     * @param password the password
     * @return this builder
     */
    public NatsConfigBuilder password(String password) {
        super.property("password", password);
        return this;
    }

    /**
     * Enable or disable TLS.
     *
     * @param tlsEnabled true to enable TLS
     * @return this builder
     */
    public NatsConfigBuilder tlsEnabled(boolean tlsEnabled) {
        super.property("tls-enabled", String.valueOf(tlsEnabled));
        return this;
    }

    /**
     * Set the queue group for load balancing.
     *
     * @param queueGroup the queue group name
     * @return this builder
     */
    public NatsConfigBuilder queueGroup(String queueGroup) {
        super.property("queue-group", queueGroup);
        return this;
    }

    /**
     * Enable or disable JetStream.
     *
     * @param jetstream true to enable JetStream
     * @return this builder
     */
    public NatsConfigBuilder jetstream(boolean jetstream) {
        super.property("jetstream", String.valueOf(jetstream));
        return this;
    }

    /**
     * Set the JetStream stream name.
     *
     * @param stream the stream name
     * @return this builder
     */
    public NatsConfigBuilder stream(String stream) {
        super.property("stream", stream);
        return this;
    }

    /**
     * Set the JetStream consumer name.
     *
     * @param consumer the consumer name
     * @return this builder
     */
    public NatsConfigBuilder consumer(String consumer) {
        super.property("consumer", consumer);
        return this;
    }

    /**
     * Set the JetStream acknowledgment wait time.
     *
     * @param ackWait the acknowledgment wait duration
     * @return this builder
     */
    public NatsConfigBuilder ackWait(Duration ackWait) {
        super.property("ack-wait", String.valueOf(ackWait.toSeconds()));
        return this;
    }

    /**
     * Set the JWT for authentication.
     *
     * @param jwt the JWT token
     * @return this builder
     */
    public NatsConfigBuilder jwt(String jwt) {
        super.property("jwt", jwt);
        return this;
    }

    /**
     * Set the NKey seed for authentication.
     *
     * @param nkey the NKey seed
     * @return this builder
     */
    public NatsConfigBuilder nkey(String nkey) {
        super.property("nkey", nkey);
        return this;
    }

    /**
     * Set the credentials file path.
     *
     * @param credentialsFile the credentials file path
     * @return this builder
     */
    public NatsConfigBuilder credentialsFile(String credentialsFile) {
        super.property("credentials-file", credentialsFile);
        return this;
    }

    /**
     * Set the TLS keystore path.
     *
     * @param keystore the keystore path
     * @return this builder
     */
    public NatsConfigBuilder tlsKeystore(String keystore) {
        super.property("tls-keystore", keystore);
        return this;
    }

    /**
     * Set the TLS keystore password.
     *
     * @param password the keystore password
     * @return this builder
     */
    public NatsConfigBuilder tlsKeystorePassword(String password) {
        super.property("tls-keystore-password", password);
        return this;
    }

    /**
     * Set the TLS truststore path.
     *
     * @param truststore the truststore path
     * @return this builder
     */
    public NatsConfigBuilder tlsTruststore(String truststore) {
        super.property("tls-truststore", truststore);
        return this;
    }

    /**
     * Set the TLS truststore password.
     *
     * @param password the truststore password
     * @return this builder
     */
    public NatsConfigBuilder tlsTruststorePassword(String password) {
        super.property("tls-truststore-password", password);
        return this;
    }
}
