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
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import io.nats.client.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class NatsConnectionManagerTest {

    @Container
    static GenericContainer<?> natsContainer = new GenericContainer<>(DockerImageName.parse("nats:latest"))
            .withExposedPorts(4222)
            .withCommand("-js");

    private NatsConnectionManager connectionManager;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ScheduledThreadPoolExecutor(2);
        connectionManager = new NatsConnectionManager(scheduler);
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
    void testBasicConnection() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl()
        )));

        Connection connection = connectionManager.getConnection(config);
        assertNotNull(connection);
        assertEquals(Connection.Status.CONNECTED, connection.getStatus());
        assertTrue(connectionManager.isHealthy());
    }

    @Test
    void testConnectionPooling() {
        Config config1 = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl(),
                "username", "user1"
        )));

        Config config2 = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl(),
                "username", "user1"
        )));

        Config config3 = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl(),
                "username", "user2"
        )));

        Connection conn1 = connectionManager.getConnection(config1);
        Connection conn2 = connectionManager.getConnection(config2);
        Connection conn3 = connectionManager.getConnection(config3);

        // Same config should return same connection
        assertSame(conn1, conn2);
        // Different config should return different connection
        assertNotSame(conn1, conn3);
    }

    @Test
    void testHealthCheck() throws InterruptedException {
        Config config = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl()
        )));

        connectionManager.getConnection(config);
        
        // Wait for health check to run
        Thread.sleep(1000);

        Map<String, NatsConnectionManager.ConnectionHealth> healthInfo = connectionManager.getHealthInfo();
        assertFalse(healthInfo.isEmpty());
        
        NatsConnectionManager.ConnectionHealth health = healthInfo.values().iterator().next();
        assertTrue(health.isHealthy());
        assertEquals(Connection.Status.CONNECTED, health.getStatus());
        assertNull(health.getLastError());
        assertTrue(health.getLastHealthCheck() > 0);
    }

    @Test
    void testConnectionReconnect() throws InterruptedException {
        Config config = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl(),
                "max-reconnects", "3",
                "reconnect-wait", "1"
        )));

        Connection connection = connectionManager.getConnection(config);
        assertNotNull(connection);

        // Force close the connection
        connection.close();

        // Wait a bit
        Thread.sleep(100);

        // Get connection again - should create new one
        Connection newConnection = connectionManager.getConnection(config);
        assertNotNull(newConnection);
        assertEquals(Connection.Status.CONNECTED, newConnection.getStatus());
    }

    @Test
    void testStopManager() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl()
        )));

        connectionManager.getConnection(config);
        assertTrue(connectionManager.isHealthy());

        connectionManager.stop();
        assertFalse(connectionManager.isHealthy());

        // Should throw exception when trying to get connection after stop
        assertThrows(IllegalStateException.class, () -> connectionManager.getConnection(config));
    }

    @Test
    void testAuthenticationConfig() {
        // Test token auth
        Config tokenConfig = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl(),
                "auth-token", "test-token"
        )));

        Connection tokenConn = connectionManager.getConnection(tokenConfig);
        assertNotNull(tokenConn);

        // Test user/pass auth
        Config userPassConfig = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl(),
                "username", "testuser",
                "password", "testpass"
        )));

        Connection userPassConn = connectionManager.getConnection(userPassConfig);
        assertNotNull(userPassConn);

        // Test JWT auth
        Config jwtConfig = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl(),
                "jwt", "test-jwt",
                "nkey", "test-nkey"
        )));

        Connection jwtConn = connectionManager.getConnection(jwtConfig);
        assertNotNull(jwtConn);

        // All should be different connections
        assertNotSame(tokenConn, userPassConn);
        assertNotSame(tokenConn, jwtConn);
        assertNotSame(userPassConn, jwtConn);
    }

    @Test
    void testTlsConfig() {
        Config tlsConfig = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl(),
                "tls-enabled", "true"
        )));

        // Should connect even without actual TLS server for this test
        Connection tlsConn = connectionManager.getConnection(tlsConfig);
        assertNotNull(tlsConn);
    }

    @Test
    void testJetStreamConfig() {
        Config jsConfig = Config.just(ConfigSources.create(Map.of(
                "url", getNatsUrl(),
                "jetstream", "true"
        )));

        Connection jsConn = connectionManager.getConnection(jsConfig);
        assertNotNull(jsConn);
        assertEquals(Connection.Status.CONNECTED, jsConn.getStatus());
    }

    private String getNatsUrl() {
        return String.format("nats://localhost:%d", natsContainer.getMappedPort(4222));
    }
}