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

import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.config.Config;
import io.helidon.messaging.Stoppable;
import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.ErrorListener;
import io.nats.client.Nats;
import io.nats.client.Options;

import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * NATS publisher for outgoing messages.
 */
public class NatsPublisher implements Flow.Publisher<Message<?>>, Stoppable {

    private static final System.Logger LOGGER = System.getLogger(NatsPublisher.class.getName());

    private final Config config;
    private final ScheduledExecutorService scheduler;
    private final String subject;
    private final String url;
    private final boolean tlsEnabled;
    private final String authToken;
    private final String username;
    private final String password;
    private final Duration connectionTimeout;
    private final int maxReconnects;
    private final Duration reconnectWait;

    private Connection natsConnection;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private NatsPublisher(Builder builder) {
        this.config = builder.config;
        this.scheduler = builder.scheduler;
        this.subject = config.get("subject").asString().orElseThrow(
                () -> new IllegalArgumentException("NATS subject is required"));
        this.url = config.get("url").asString().orElseThrow(
                () -> new IllegalArgumentException("NATS URL is required"));
        this.tlsEnabled = config.get("tls-enabled").asBoolean().orElse(false);
        this.authToken = config.get("auth-token").asString().orElse(null);
        this.username = config.get("username").asString().orElse(null);
        this.password = config.get("password").asString().orElse(null);
        this.connectionTimeout = Duration.ofSeconds(config.get("connection-timeout").asInt().orElse(5));
        this.maxReconnects = config.get("max-reconnects").asInt().orElse(10);
        this.reconnectWait = Duration.ofSeconds(config.get("reconnect-wait").asInt().orElse(1));

        initializeConnection();
    }

    /**
     * Create a new builder for NATS publisher.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Message<?>> subscriber) {
        if (stopped.get()) {
            subscriber.onError(new IllegalStateException("Publisher is stopped"));
            return;
        }

        // For now, we'll create a simple subscription that reads from NATS
        // In a real implementation, this would be more sophisticated
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                // For outgoing connector, we typically don't generate messages
                // This would be implemented for incoming messages
            }

            @Override
            public void cancel() {
                // Handle cancellation
            }
        });
    }

    /**
     * Publish a message to NATS.
     *
     * @param message the message to publish
     */
    public void publish(Message<?> message) {
        if (stopped.get()) {
            throw new IllegalStateException("Publisher is stopped");
        }

        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            throw new IllegalStateException("NATS connection is not available");
        }

        try {
            byte[] data = convertToBytes(message.getPayload());
            natsConnection.publish(subject, data);
            LOGGER.log(Level.DEBUG, () -> String.format("Published message to subject: %s", subject));
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Failed to publish message", e);
            throw new RuntimeException("Failed to publish message", e);
        }
    }

    @Override
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            LOGGER.log(Level.DEBUG, "Stopping NATS publisher");
            if (natsConnection != null) {
                try {
                    natsConnection.close();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.log(Level.WARNING, "Interrupted while closing NATS connection", e);
                }
            }
        }
    }

    private void initializeConnection() {
        try {
            Options.Builder optionsBuilder = new Options.Builder()
                    .server(url)
                    .connectionTimeout(connectionTimeout)
                    .maxReconnects(maxReconnects)
                    .reconnectWait(reconnectWait)
                    .connectionListener(new ConnectionListener() {
                        @Override
                        public void connectionEvent(Connection conn, Events type) {
                            LOGGER.log(Level.INFO, () -> String.format("NATS connection event: %s", type));
                        }
                    })
                    .errorListener(new ErrorListener() {
                        @Override
                        public void errorOccurred(Connection conn, String error) {
                            LOGGER.log(Level.ERROR, () -> String.format("NATS error: %s", error));
                        }

                        @Override
                        public void exceptionOccurred(Connection conn, Exception exp) {
                            LOGGER.log(Level.ERROR, "NATS exception occurred", exp);
                        }

                        @Override
                        public void slowConsumerDetected(Connection conn, io.nats.client.Consumer consumer) {
                            LOGGER.log(Level.WARNING, "NATS slow consumer detected");
                        }
                    });

            // Configure authentication
            if (authToken != null && !authToken.trim().isEmpty()) {
                optionsBuilder.token(authToken.toCharArray());
            } else if (username != null && password != null) {
                optionsBuilder.userInfo(username, password);
            }

            // Configure TLS
            if (tlsEnabled) {
                optionsBuilder.secure();
            }

            natsConnection = Nats.connect(optionsBuilder.build());
            LOGGER.log(Level.INFO, () -> String.format("Connected to NATS server: %s", url));

        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Failed to connect to NATS server", e);
            throw new RuntimeException("Failed to connect to NATS server", e);
        }
    }

    private byte[] convertToBytes(Object payload) {
        if (payload instanceof byte[]) {
            return (byte[]) payload;
        } else if (payload instanceof String) {
            return ((String) payload).getBytes(StandardCharsets.UTF_8);
        } else {
            // For other types, convert to string first
            return payload.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Builder for NATS publisher.
     */
    public static class Builder {
        private Config config;
        private ScheduledExecutorService scheduler;

        /**
         * Set the configuration.
         *
         * @param config the configuration
         * @return this builder
         */
        public Builder config(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Set the scheduler.
         *
         * @param scheduler the scheduler
         * @return this builder
         */
        public Builder scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        /**
         * Build the NATS publisher.
         *
         * @return the built publisher
         */
        public NatsPublisher build() {
            if (config == null) {
                throw new IllegalArgumentException("Config is required");
            }
            if (scheduler == null) {
                throw new IllegalArgumentException("Scheduler is required");
            }
            return new NatsPublisher(this);
        }
    }
}