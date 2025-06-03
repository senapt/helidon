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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * NATS implementation of MicroProfile Reactive Messaging Message.
 *
 * @param <T> the type of the message payload
 */
public class NatsMessage<T> implements Message<T> {

    private final T payload;
    private final io.nats.client.Message natsMessage;
    private final Supplier<CompletionStage<Void>> ackSupplier;
    private final NatsNackHandler nackHandler;

    private NatsMessage(Builder<T> builder) {
        this.payload = builder.payload;
        this.natsMessage = builder.natsMessage;
        this.ackSupplier = builder.ackSupplier;
        this.nackHandler = builder.nackHandler;
    }

    /**
     * Create a new NATS message with the given payload.
     *
     * @param payload the message payload
     * @param <T> the type of the payload
     * @return new NATS message
     */
    public static <T> NatsMessage<T> of(T payload) {
        return NatsMessage.<T>builder()
                .payload(payload)
                .build();
    }

    /**
     * Create a new NATS message from a NATS client message.
     *
     * @param natsMessage the NATS client message
     * @param payload the converted payload
     * @param <T> the type of the payload
     * @return new NATS message
     */
    public static <T> NatsMessage<T> of(io.nats.client.Message natsMessage, T payload) {
        return NatsMessage.<T>builder()
                .natsMessage(natsMessage)
                .payload(payload)
                .build();
    }

    /**
     * Create a new builder for NATS message.
     *
     * @param <T> the type of the payload
     * @return new builder
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public T getPayload() {
        return payload;
    }

    @Override
    public CompletionStage<Void> ack() {
        if (ackSupplier != null) {
            return ackSupplier.get();
        }
        return CompletableFuture.completedFuture(null);
    }


    /**
     * Get the underlying NATS message.
     *
     * @return the NATS message, or empty if this was created from payload only
     */
    public Optional<io.nats.client.Message> getNatsMessage() {
        return Optional.ofNullable(natsMessage);
    }

    /**
     * Get the NATS subject.
     *
     * @return the subject, or empty if no underlying NATS message
     */
    public Optional<String> getSubject() {
        return Optional.ofNullable(natsMessage).map(io.nats.client.Message::getSubject);
    }

    /**
     * Get the NATS reply subject.
     *
     * @return the reply subject, or empty if no underlying NATS message or no reply subject
     */
    public Optional<String> getReplyTo() {
        return Optional.ofNullable(natsMessage).map(io.nats.client.Message::getReplyTo);
    }

    /**
     * Get the NATS headers.
     *
     * @return the headers, or empty if no underlying NATS message or no headers
     */
    public Optional<io.nats.client.impl.Headers> getHeaders() {
        return Optional.ofNullable(natsMessage).map(io.nats.client.Message::getHeaders);
    }

    /**
     * Builder for NATS message.
     *
     * @param <T> the type of the payload
     */
    public static class Builder<T> {
        private T payload;
        private io.nats.client.Message natsMessage;
        private Supplier<CompletionStage<Void>> ackSupplier;
        private NatsNackHandler nackHandler;

        /**
         * Set the message payload.
         *
         * @param payload the payload
         * @return this builder
         */
        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        /**
         * Set the underlying NATS message.
         *
         * @param natsMessage the NATS message
         * @return this builder
         */
        public Builder<T> natsMessage(io.nats.client.Message natsMessage) {
            this.natsMessage = natsMessage;
            return this;
        }

        /**
         * Set the acknowledgment supplier.
         *
         * @param ackSupplier the acknowledgment supplier
         * @return this builder
         */
        public Builder<T> ackSupplier(Supplier<CompletionStage<Void>> ackSupplier) {
            this.ackSupplier = ackSupplier;
            return this;
        }

        /**
         * Set the negative acknowledgment handler.
         *
         * @param nackHandler the nack handler
         * @return this builder
         */
        public Builder<T> nackHandler(NatsNackHandler nackHandler) {
            this.nackHandler = nackHandler;
            return this;
        }

        /**
         * Build the NATS message.
         *
         * @return the built message
         */
        public NatsMessage<T> build() {
            return new NatsMessage<>(this);
        }
    }
}
