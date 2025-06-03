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

import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;

/**
 * Health check for NATS connections.
 */
public class NatsHealthCheck implements HealthCheck {

    private final NatsConnectionManager connectionManager;
    private final String name;

    /**
     * Create a new NATS health check.
     *
     * @param connectionManager the connection manager
     */
    NatsHealthCheck(NatsConnectionManager connectionManager) {
        this(connectionManager, "nats");
    }

    /**
     * Create a new NATS health check with a custom name.
     *
     * @param connectionManager the connection manager
     * @param name the health check name
     */
    NatsHealthCheck(NatsConnectionManager connectionManager, String name) {
        this.connectionManager = connectionManager;
        this.name = name;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponse.Builder builder = HealthCheckResponse.builder();

        try {
            Map<String, NatsConnectionManager.ConnectionHealth> healthInfo = connectionManager.getHealthInfo();
            
            if (healthInfo.isEmpty()) {
                return builder
                        .status(HealthCheckResponse.Status.UP)
                        .detail("message", "No NATS connections configured")
                        .build();
            }

            boolean allHealthy = true;
            int healthyCount = 0;
            int totalCount = healthInfo.size();

            for (Map.Entry<String, NatsConnectionManager.ConnectionHealth> entry : healthInfo.entrySet()) {
                String connectionKey = entry.getKey();
                NatsConnectionManager.ConnectionHealth health = entry.getValue();
                
                String detailPrefix = "connection." + connectionKey.replaceAll("[#@]", "_");
                builder.detail(detailPrefix + ".status", health.getStatus().toString());
                builder.detail(detailPrefix + ".healthy", health.isHealthy());
                
                if (health.getLastError() != null) {
                    builder.detail(detailPrefix + ".error", health.getLastError());
                }
                
                if (health.getServerInfo() != null) {
                    builder.detail(detailPrefix + ".server.version", health.getServerInfo().getVersion());
                    builder.detail(detailPrefix + ".server.id", health.getServerInfo().getServerId());
                }
                
                if (health.isHealthy()) {
                    healthyCount++;
                } else {
                    allHealthy = false;
                }
            }

            builder.detail("connections.total", totalCount);
            builder.detail("connections.healthy", healthyCount);

            if (allHealthy) {
                builder.status(HealthCheckResponse.Status.UP)
                        .detail("message", String.format("All %d NATS connections are healthy", totalCount));
            } else {
                builder.status(HealthCheckResponse.Status.DOWN)
                        .detail("message", String.format("%d of %d NATS connections are unhealthy", 
                                totalCount - healthyCount, totalCount));
            }

        } catch (Exception e) {
            builder.status(HealthCheckResponse.Status.DOWN)
                    .detail("error", e.getMessage())
                    .detail("message", "Failed to check NATS health");
        }

        return builder.build();
    }

    @Override
    public HealthCheckType type() {
        return HealthCheckType.READINESS;
    }

    @Override
    public String name() {
        return name;
    }
}