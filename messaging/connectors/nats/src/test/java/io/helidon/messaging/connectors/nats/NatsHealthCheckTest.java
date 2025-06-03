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

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class NatsHealthCheckTest {

    @Container
    static GenericContainer<?> natsContainer = new GenericContainer<>(DockerImageName.parse("nats:latest"))
            .withExposedPorts(4222)
            .withCommand("-js");

    private NatsConnectionManager connectionManager;
    private NatsHealthCheck healthCheck;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ScheduledThreadPoolExecutor(2);
        connectionManager = new NatsConnectionManager(scheduler);
        healthCheck = new NatsHealthCheck(connectionManager);
    }

    @AfterEach
    void tearDown() {
        if (connectionManager != null) {
            connectionManager.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void testHealthCheckWithNoConnections() {
        HealthCheckResponse response = healthCheck.call();
        
        assertNotNull(response);
        assertEquals("nats", response.name());
        assertEquals(HealthCheckResponse.Status.UP, response.status());
        assertEquals("No NATS connections configured", response.details().get("message"));
    }

    @Test
    void testHealthCheckWithHealthyConnection() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl()
        )));

        connectionManager.getConnection(config);
        
        HealthCheckResponse response = healthCheck.call();
        
        assertNotNull(response);
        assertEquals("nats", response.name());
        assertEquals(HealthCheckResponse.Status.UP, response.status());
        assertEquals("All 1 NATS connections are healthy", response.details().get("message"));
        assertEquals(1L, response.details().get("connections.total"));
        assertEquals(1L, response.details().get("connections.healthy"));
    }

    @Test
    void testHealthCheckWithMultipleConnections() {
        Config config1 = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl(),
                "username", "user1"
        )));

        Config config2 = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl(),
                "username", "user2"
        )));

        connectionManager.getConnection(config1);
        connectionManager.getConnection(config2);
        
        HealthCheckResponse response = healthCheck.call();
        
        assertNotNull(response);
        assertEquals("nats", response.name());
        assertEquals(HealthCheckResponse.Status.UP, response.status());
        assertEquals("All 2 NATS connections are healthy", response.details().get("message"));
        assertEquals(2L, response.details().get("connections.total"));
        assertEquals(2L, response.details().get("connections.healthy"));
    }

    @Test
    void testHealthCheckType() {
        assertEquals(HealthCheckType.READINESS, healthCheck.type());
    }

    @Test
    void testHealthCheckName() {
        assertEquals("nats", healthCheck.name());
        
        // Test custom name
        NatsHealthCheck customHealthCheck = new NatsHealthCheck(connectionManager, "custom-nats");
        assertEquals("custom-nats", customHealthCheck.name());
    }

    @Test
    void testHealthCheckAfterStop() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl()
        )));

        connectionManager.getConnection(config);
        connectionManager.stop();
        
        HealthCheckResponse response = healthCheck.call();
        
        assertNotNull(response);
        assertEquals(HealthCheckResponse.Status.DOWN, response.status());
    }

    @Test
    void testHealthCheckDetails() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl(),
                "jetstream", "true"
        )));

        connectionManager.getConnection(config);
        
        HealthCheckResponse response = healthCheck.call();
        
        assertNotNull(response);
        Map<String, Object> details = response.details();
        
        // Check for connection-specific details
        assertTrue(details.keySet().stream().anyMatch(key -> key.startsWith("connection.") && key.endsWith(".status")));
        assertTrue(details.keySet().stream().anyMatch(key -> key.startsWith("connection.") && key.endsWith(".healthy")));
    }

    private String getNatsUrl() {
        return String.format("nats://localhost:%d", natsContainer.getMappedPort(4222));
    }
}