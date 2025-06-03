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
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.config.Config;
import io.helidon.messaging.Stoppable;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import org.reactivestreams.Subscriber;

/**
 * NATS subscriber for incoming messages.
 */
public class NatsSubscriber implements Subscriber<org.eclipse.microprofile.reactive.messaging.Message<?>>, Stoppable {

    private static final System.Logger LOGGER = System.getLogger(NatsSubscriber.class.getName());

    private final Config config;
    private final ScheduledExecutorService scheduler;
    private final NatsConnectionManager connectionManager;
    private final String subject;
    private final String queueGroup;
    private Dispatcher dispatcher;
    private io.nats.client.Subscription natsSubscription;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicLong requested = new AtomicLong(0);
    private org.reactivestreams.Subscription upstreamSubscription;

    private NatsSubscriber(Builder builder) {
        this.config = builder.config;
        this.scheduler = builder.scheduler;
        this.connectionManager = builder.connectionManager;
        this.subject = config.get("subject").asString().orElseThrow(
                () -> new IllegalArgumentException("NATS subject is required"));
        this.queueGroup = config.get("queue-group").asString().orElse(null);

        initializeSubscription();
    }

    /**
     * Create a new builder for NATS subscriber.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void onSubscribe(org.reactivestreams.Subscription subscription) {
        this.upstreamSubscription = subscription;
        if (!stopped.get()) {
            subscription.request(Long.MAX_VALUE); // Request all available messages
        }
    }

    @Override
    public void onNext(org.eclipse.microprofile.reactive.messaging.Message<?> message) {
        if (stopped.get()) {
            return;
        }

        try {
            // Convert and publish to NATS
            Connection connection = connectionManager.getConnection(config);
            byte[] data = convertToBytes(message.getPayload());
            connection.publish(subject, data);

            // Acknowledge the message
            message.ack();

            LOGGER.log(Level.DEBUG, () -> String.format("Forwarded message to NATS subject: %s", subject));
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Failed to forward message to NATS", e);
            message.nack(e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        LOGGER.log(Level.ERROR, "Error in NATS subscriber", throwable);
        stop();
    }

    @Override
    public void onComplete() {
        LOGGER.log(Level.DEBUG, "NATS subscriber completed");
        stop();
    }

    @Override
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            LOGGER.log(Level.DEBUG, "Stopping NATS subscriber");

            if (natsSubscription != null && natsSubscription.isActive()) {
                natsSubscription.unsubscribe();
            }

            if (dispatcher != null) {
                dispatcher.unsubscribe(subject);
            }

            // Connection manager handles connection lifecycle

            if (upstreamSubscription != null) {
                upstreamSubscription.cancel();
            }
        }
    }

    /**
     * Create a message handler for incoming NATS messages.
     * This would be used in a publisher scenario where we're receiving from NATS.
     *
     * @param messageConsumer consumer for received messages
     * @return message handler
     */
    public MessageHandler createMessageHandler(java.util.function.Consumer<NatsMessage<String>> messageConsumer) {
        return new MessageHandler() {
            @Override
            public void onMessage(Message msg) throws InterruptedException {
                if (stopped.get()) {
                    return;
                }

                try {
                    String payload = new String(msg.getData(), StandardCharsets.UTF_8);
                    NatsMessage<String> natsMessage = NatsMessage.of(msg, payload);
                    messageConsumer.accept(natsMessage);

                    LOGGER.log(Level.DEBUG, () -> String.format("Received message from subject: %s", msg.getSubject()));
                } catch (Exception e) {
                    LOGGER.log(Level.ERROR, "Error processing received message", e);
                }
            }
        };
    }

    private void initializeSubscription() {
        try {
            Connection connection = connectionManager.getConnection(config);
            
            // Create dispatcher for handling incoming messages
            dispatcher = connection.createDispatcher();

            LOGGER.log(Level.INFO, () -> String.format("Initialized NATS subscription for subject: %s", subject));

        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Failed to initialize NATS subscription", e);
            throw new RuntimeException("Failed to initialize NATS subscription", e);
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
     * Builder for NATS subscriber.
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
         * Build the NATS subscriber.
         *
         * @return the built subscriber
         */
        public NatsSubscriber build() {
            if (config == null) {
                throw new IllegalArgumentException("Config is required");
            }
            if (scheduler == null) {
                throw new IllegalArgumentException("Scheduler is required");
            }
            if (connectionManager == null) {
                throw new IllegalArgumentException("Connection manager is required");
            }
            return new NatsSubscriber(this);
        }
    }
}
