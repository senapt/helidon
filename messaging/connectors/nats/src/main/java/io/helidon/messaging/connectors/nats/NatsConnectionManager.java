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
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.config.Config;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.ErrorListener;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.ServerInfo;

/**
 * Manages NATS connections with pooling, health checks, and lifecycle management.
 */
class NatsConnectionManager {

    private static final System.Logger LOGGER = System.getLogger(NatsConnectionManager.class.getName());
    private static final String CONNECTION_KEY_FORMAT = "%s#%s#%s"; // url#user#jetstream
    private static final Duration HEALTH_CHECK_INTERVAL = Duration.ofSeconds(30);

    private final Map<String, ManagedConnection> connectionPool = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private ScheduledFuture<?> healthCheckTask;

    /**
     * Create a new connection manager.
     *
     * @param scheduler the scheduler for background tasks
     */
    NatsConnectionManager(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
        startHealthChecks();
    }

    /**
     * Get or create a NATS connection based on the configuration.
     *
     * @param config the configuration
     * @return the connection
     */
    Connection getConnection(Config config) {
        if (stopped.get()) {
            throw new IllegalStateException("Connection manager is stopped");
        }

        String connectionKey = buildConnectionKey(config);
        return connectionPool.computeIfAbsent(connectionKey, k -> createManagedConnection(config))
                .getConnection();
    }

    /**
     * Get the health status of all connections.
     *
     * @return true if all connections are healthy, false otherwise
     */
    boolean isHealthy() {
        if (stopped.get()) {
            return false;
        }

        return connectionPool.values().stream()
                .allMatch(ManagedConnection::isHealthy);
    }

    /**
     * Get detailed health information for all connections.
     *
     * @return health information map
     */
    Map<String, ConnectionHealth> getHealthInfo() {
        Map<String, ConnectionHealth> healthInfo = new ConcurrentHashMap<>();
        connectionPool.forEach((key, conn) -> {
            healthInfo.put(key, conn.getHealthInfo());
        });
        return healthInfo;
    }

    /**
     * Stop the connection manager and close all connections.
     */
    void stop() {
        if (stopped.compareAndSet(false, true)) {
            LOGGER.log(Level.DEBUG, "Stopping NATS connection manager");

            // Stop health checks
            if (healthCheckTask != null) {
                healthCheckTask.cancel(false);
            }

            // Close all connections
            connectionPool.values().forEach(ManagedConnection::close);
            connectionPool.clear();

            LOGGER.log(Level.INFO, "NATS connection manager stopped");
        }
    }

    private String buildConnectionKey(Config config) {
        String url = config.get("url").asString().orElse("nats://localhost:4222");
        String username = config.get("username").asString().orElse("");
        boolean jetstream = config.get("jetstream").asBoolean().orElse(false);
        return String.format(CONNECTION_KEY_FORMAT, url, username, jetstream);
    }

    private ManagedConnection createManagedConnection(Config config) {
        LOGGER.log(Level.DEBUG, () -> "Creating new NATS connection");
        return new ManagedConnection(config);
    }

    private void startHealthChecks() {
        healthCheckTask = scheduler.scheduleWithFixedDelay(
                this::performHealthChecks,
                HEALTH_CHECK_INTERVAL.toSeconds(),
                HEALTH_CHECK_INTERVAL.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    private void performHealthChecks() {
        if (stopped.get()) {
            return;
        }

        LOGGER.log(Level.DEBUG, "Performing NATS health checks");
        connectionPool.values().forEach(ManagedConnection::checkHealth);
    }

    /**
     * Managed connection wrapper with health checking.
     */
    private static class ManagedConnection {
        private final Config config;
        private final AtomicBoolean healthy = new AtomicBoolean(true);
        private volatile Connection connection;
        private volatile String lastError;
        private volatile long lastHealthCheck;
        private volatile ServerInfo serverInfo;

        ManagedConnection(Config config) {
            this.config = config;
            this.connection = createConnection();
        }

        Connection getConnection() {
            if (!healthy.get() || connection == null || connection.getStatus() != Connection.Status.CONNECTED) {
                synchronized (this) {
                    // Double-check after acquiring lock
                    if (!healthy.get() || connection == null || connection.getStatus() != Connection.Status.CONNECTED) {
                        LOGGER.log(Level.INFO, "Reconnecting NATS connection");
                        close();
                        connection = createConnection();
                    }
                }
            }
            return connection;
        }

        boolean isHealthy() {
            return healthy.get() && connection != null && connection.getStatus() == Connection.Status.CONNECTED;
        }

        ConnectionHealth getHealthInfo() {
            return new ConnectionHealth(
                    isHealthy(),
                    connection != null ? connection.getStatus() : Connection.Status.CLOSED,
                    lastError,
                    lastHealthCheck,
                    serverInfo
            );
        }

        void checkHealth() {
            lastHealthCheck = System.currentTimeMillis();
            try {
                if (connection != null && connection.getStatus() == Connection.Status.CONNECTED) {
                    // Try to get server info as a health check
                    serverInfo = connection.getServerInfo();
                    healthy.set(true);
                    lastError = null;
                } else {
                    healthy.set(false);
                    lastError = "Connection not in CONNECTED state";
                }
            } catch (Exception e) {
                healthy.set(false);
                lastError = e.getMessage();
                LOGGER.log(Level.WARNING, "NATS health check failed", e);
            }
        }

        void close() {
            if (connection != null) {
                try {
                    connection.close();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.log(Level.WARNING, "Interrupted while closing NATS connection", e);
                }
            }
        }

        private Connection createConnection() {
            try {
                Options options = buildOptions();
                Connection conn = Nats.connect(options);
                healthy.set(true);
                lastError = null;
                LOGGER.log(Level.INFO, () -> String.format("Connected to NATS server: %s", 
                        config.get("url").asString().orElse("nats://localhost:4222")));
                return conn;
            } catch (Exception e) {
                healthy.set(false);
                lastError = e.getMessage();
                LOGGER.log(Level.ERROR, "Failed to connect to NATS server", e);
                throw new RuntimeException("Failed to connect to NATS server", e);
            }
        }

        private Options buildOptions() {
            String url = config.get("url").asString().orElse("nats://localhost:4222");
            Duration connectionTimeout = Duration.ofSeconds(config.get("connection-timeout").asInt().orElse(5));
            int maxReconnects = config.get("max-reconnects").asInt().orElse(10);
            Duration reconnectWait = Duration.ofSeconds(config.get("reconnect-wait").asInt().orElse(1));
            boolean tlsEnabled = config.get("tls-enabled").asBoolean().orElse(false);
            String authToken = config.get("auth-token").asString().orElse(null);
            String username = config.get("username").asString().orElse(null);
            String password = config.get("password").asString().orElse(null);

            Options.Builder builder = new Options.Builder()
                    .server(url)
                    .connectionTimeout(connectionTimeout)
                    .maxReconnects(maxReconnects)
                    .reconnectWait(reconnectWait)
                    .connectionListener(new NatsConnectionListener())
                    .errorListener(new NatsErrorListener());

            // Configure authentication (in order of precedence)
            String credentialsFile = config.get("credentials-file").asString().orElse(null);
            String jwt = config.get("jwt").asString().orElse(null);
            String nkeySeed = config.get("nkey").asString().orElse(null);
            
            if (credentialsFile != null && !credentialsFile.trim().isEmpty()) {
                try {
                    builder.credentialPath(credentialsFile);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load credentials file: " + credentialsFile, e);
                }
            } else if (jwt != null && !jwt.trim().isEmpty() && nkeySeed != null && !nkeySeed.trim().isEmpty()) {
                try {
                    builder.authHandler(io.nats.client.Nats.staticCredentials(jwt.toCharArray(), nkeySeed.toCharArray()));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to configure JWT authentication", e);
                }
            } else if (authToken != null && !authToken.trim().isEmpty()) {
                builder.token(authToken.toCharArray());
            } else if (username != null && password != null) {
                builder.userInfo(username, password);
            }

            // Configure TLS
            if (tlsEnabled) {
                try {
                    builder.secure();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to configure TLS", e);
                }
                
                // Add support for custom TLS configuration
                String keystorePath = config.get("tls-keystore").asString().orElse(null);
                String truststorePath = config.get("tls-truststore").asString().orElse(null);
                
                if (keystorePath != null || truststorePath != null) {
                    // This would need proper SSL context configuration
                    LOGGER.log(Level.WARNING, "Custom TLS configuration not yet implemented");
                }
            }

            return builder.build();
        }
    }

    /**
     * Connection health information.
     */
    static class ConnectionHealth {
        private final boolean healthy;
        private final Connection.Status status;
        private final String lastError;
        private final long lastHealthCheck;
        private final ServerInfo serverInfo;

        ConnectionHealth(boolean healthy, Connection.Status status, String lastError, 
                        long lastHealthCheck, ServerInfo serverInfo) {
            this.healthy = healthy;
            this.status = status;
            this.lastError = lastError;
            this.lastHealthCheck = lastHealthCheck;
            this.serverInfo = serverInfo;
        }

        /**
         * Check if the connection is healthy.
         *
         * @return true if healthy, false otherwise
         */
        public boolean isHealthy() {
            return healthy;
        }

        /**
         * Get the connection status.
         *
         * @return the status
         */
        public Connection.Status getStatus() {
            return status;
        }

        /**
         * Get the last error message.
         *
         * @return the last error or null
         */
        public String getLastError() {
            return lastError;
        }

        /**
         * Get the timestamp of the last health check.
         *
         * @return the timestamp
         */
        public long getLastHealthCheck() {
            return lastHealthCheck;
        }

        /**
         * Get the server information.
         *
         * @return the server info or null
         */
        public ServerInfo getServerInfo() {
            return serverInfo;
        }
    }

    /**
     * NATS connection event listener.
     */
    private static class NatsConnectionListener implements ConnectionListener {
        @Override
        public void connectionEvent(Connection conn, Events type) {
            LOGGER.log(Level.INFO, () -> String.format("NATS connection event: %s", type));
        }
    }

    /**
     * NATS error listener.
     */
    private static class NatsErrorListener implements ErrorListener {
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
    }
}