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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.messaging.Stoppable;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorAttribute;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;

/**
 * Implementation of NATS Connector as described in the MicroProfile Reactive Messaging Specification.
 */
@ApplicationScoped
@Connector(NatsConnector.CONNECTOR_NAME)
@ConnectorAttribute(name = "url",
        description = "NATS server URL (nats://host:port) or comma-separated list of URLs for clustering.",
        mandatory = true,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = "subject",
        description = "NATS subject to publish to or subscribe from.",
        mandatory = true,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = "connection-timeout",
        description = "Connection timeout in seconds.",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        defaultValue = "5",
        type = "int")
@ConnectorAttribute(name = "max-reconnects",
        description = "Maximum number of reconnection attempts. -1 for unlimited.",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        defaultValue = "10",
        type = "int")
@ConnectorAttribute(name = "reconnect-wait",
        description = "Time to wait between reconnection attempts in seconds.",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        defaultValue = "1",
        type = "int")
@ConnectorAttribute(name = "auth-token",
        description = "Authentication token for NATS server.",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = "username",
        description = "Username for NATS server authentication.",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = "password",
        description = "Password for NATS server authentication.",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = "tls-enabled",
        description = "Enable TLS/SSL connection to NATS server.",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        defaultValue = "false",
        type = "boolean")
@ConnectorAttribute(name = "queue-group",
        description = "Queue group name for load balancing among consumers.",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = "jetstream",
        description = "Enable JetStream functionality.",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        defaultValue = "false",
        type = "boolean")
@ConnectorAttribute(name = "stream",
        description = "JetStream stream name.",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = "consumer",
        description = "JetStream consumer name.",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = "ack-wait",
        description = "JetStream acknowledgment wait time in seconds.",
        direction = ConnectorAttribute.Direction.INCOMING,
        defaultValue = "30",
        type = "int")
public class NatsConnector implements IncomingConnectorFactory, OutgoingConnectorFactory, Stoppable {

    private static final System.Logger LOGGER = System.getLogger(NatsConnector.class.getName());
    
    /**
     * MicroProfile messaging NATS connector name.
     */
    static final String CONNECTOR_NAME = "helidon-nats";

    private final ScheduledExecutorService scheduler;
    private final Queue<NatsPublisher> publishers = new LinkedList<>();
    private final Queue<NatsSubscriber> subscribers = new LinkedList<>();

    /**
     * Constructor to instantiate NatsConnector.
     *
     * @param config Helidon {@link io.helidon.config.Config config}
     */
    @Inject
    NatsConnector(Config config) {
        scheduler = ScheduledThreadPoolSupplier.builder()
                .threadNamePrefix("nats-")
                .config(config)
                .build()
                .get();
    }

    /**
     * Called when container is terminated. If it is not running in a container it must be explicitly invoked
     * to terminate the messaging and release NATS connections.
     *
     * @param event termination event
     */
    void terminate(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
        stop();
    }

    /**
     * Gets the open publisher resources for testing verification purposes.
     *
     * @return the opened publisher resources
     */
    Queue<NatsPublisher> publishers() {
        return publishers;
    }

    /**
     * Gets the open subscriber resources for testing verification purposes.
     *
     * @return the opened subscriber resources
     */
    Queue<NatsSubscriber> subscribers() {
        return subscribers;
    }

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(org.eclipse.microprofile.config.Config config) {
        NatsPublisher publisher = NatsPublisher.builder()
                .config(MpConfig.toHelidonConfig(config))
                .scheduler(scheduler)
                .build();
        LOGGER.log(Level.DEBUG, () -> String.format("Publisher resource %s added", publisher));
        publishers.add(publisher);
        return ReactiveStreams.fromPublisher(publisher);
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(org.eclipse.microprofile.config.Config config) {
        NatsSubscriber subscriber = NatsSubscriber.builder()
                .config(MpConfig.toHelidonConfig(config))
                .scheduler(scheduler)
                .build();
        LOGGER.log(Level.DEBUG, () -> String.format("Subscriber resource %s added", subscriber));
        subscribers.add(subscriber);
        return ReactiveStreams.fromSubscriber(subscriber);
    }

    /**
     * Creates a new instance of NatsConnector with the required configuration.
     *
     * @param config Helidon {@link io.helidon.config.Config config}
     * @return the new instance
     */
    public static NatsConnector create(Config config) {
        return new NatsConnector(config);
    }

    /**
     * Creates a new instance of NatsConnector with empty configuration.
     *
     * @return the new instance
     */
    public static NatsConnector create() {
        return new NatsConnector(Config.empty());
    }

    /**
     * Stops the NatsConnector and all the jobs and resources related to it.
     */
    @Override
    public void stop() {
        LOGGER.log(Level.DEBUG, () -> "Terminating NatsConnector...");
        
        // Stops the scheduler first to make sure no new task will be triggered meanwhile resources are closing
        scheduler.shutdown();
        
        List<Exception> failed = new LinkedList<>();
        
        // Stop all publishers
        NatsPublisher publisher;
        while ((publisher = publishers.poll()) != null) {
            try {
                publisher.stop();
            } catch (Exception e) {
                // Continue closing
                failed.add(e);
            }
        }
        
        // Stop all subscribers
        NatsSubscriber subscriber;
        while ((subscriber = subscribers.poll()) != null) {
            try {
                subscriber.stop();
            } catch (Exception e) {
                // Continue closing
                failed.add(e);
            }
        }
        
        if (failed.isEmpty()) {
            LOGGER.log(Level.DEBUG, "NatsConnector terminated successfully");
        } else {
            // Inform about the errors
            failed.forEach(e -> LOGGER.log(Level.ERROR, "An error happened closing resource", e));
        }
    }

    /**
     * Custom config builder for NATS connector.
     *
     * @return new NATS specific config builder
     */
    public static NatsConfigBuilder configBuilder() {
        return new NatsConfigBuilder();
    }
}