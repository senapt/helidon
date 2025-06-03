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

import java.io.IOException;
import java.time.Duration;

import io.nats.client.Connection;
import io.nats.client.Nats;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abstract base class for NATS integration tests.
 */
@Testcontainers
public abstract class AbstractNatsTest {

    protected static String NATS_SERVER;
    protected static Connection natsConnection;

    @Container
    static GenericContainer<?> natsContainer = new GenericContainer<>("nats:2.10")
            .withExposedPorts(4222)
            .withCommand("--jetstream");

    @BeforeAll
    static void startNats() throws IOException, InterruptedException {
        natsContainer.start();
        NATS_SERVER = "nats://localhost:" + natsContainer.getMappedPort(4222);
        
        // Create a connection for test operations
        natsConnection = Nats.connect(io.nats.client.Options.builder()
                .server(NATS_SERVER)
                .connectionTimeout(Duration.ofSeconds(5))
                .build());
    }

    @AfterAll
    static void stopNats() throws InterruptedException {
        if (natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED) {
            natsConnection.close();
        }
        natsContainer.stop();
    }
}