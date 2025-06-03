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
import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * NATS publisher for outgoing messages.
 */
public class NatsPublisher implements Publisher<Message<?>>, Stoppable {

    private static final System.Logger LOGGER = System.getLogger(NatsPublisher.class.getName());

    private final Config config;
    private final ScheduledExecutorService scheduler;
    private final NatsConnectionManager connectionManager;
    private final String subject;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private NatsPublisher(Builder builder) {
        this.config = builder.config;
        this.scheduler = builder.scheduler;
        this.connectionManager = builder.connectionManager;
        this.subject = config.get("subject").asString().orElseThrow(
                () -> new IllegalArgumentException("NATS subject is required"));
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
    public void subscribe(Subscriber<? super Message<?>> subscriber) {
        if (stopped.get()) {
            subscriber.onError(new IllegalStateException("Publisher is stopped"));
            return;
        }

        // For now, we'll create a simple subscription that reads from NATS
        // In a real implementation, this would be more sophisticated
        subscriber.onSubscribe(new org.reactivestreams.Subscription() {
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

        try {
            Connection connection = connectionManager.getConnection(config);
            byte[] data = convertToBytes(message.getPayload());
            connection.publish(subject, data);
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
            // Connection manager handles connection lifecycle
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
        private NatsConnectionManager connectionManager;

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
         * Set the connection manager.
         *
         * @param connectionManager the connection manager
         * @return this builder
         */
        public Builder connectionManager(NatsConnectionManager connectionManager) {
            this.connectionManager = connectionManager;
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
            if (connectionManager == null) {
                throw new IllegalArgumentException("Connection manager is required");
            }
            return new NatsPublisher(this);
        }
    }
}
