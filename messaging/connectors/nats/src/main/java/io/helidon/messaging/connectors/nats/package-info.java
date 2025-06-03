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

/**
 * NATS connector for MicroProfile Reactive Messaging.
 *
 * <p>This package provides a complete implementation of a NATS connector that follows
 * the MicroProfile Reactive Messaging specification. It supports both basic NATS
 * pub/sub messaging and advanced JetStream features.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Basic NATS pub/sub messaging</li>
 *   <li>JetStream support (coming in Phase 2)</li>
 *   <li>Authentication (token, username/password)</li>
 *   <li>TLS/SSL support</li>
 *   <li>Connection management with reconnection</li>
 *   <li>Queue groups for load balancing</li>
 *   <li>Configurable timeouts and retry policies</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>The connector is configured using standard MicroProfile Config properties:
 *
 * <pre>{@code
 * mp:
 *   messaging:
 *     connector:
 *       helidon-nats:
 *         url: "nats://localhost:4222"
 *         connection-timeout: 5
 *         max-reconnects: 10
 *     outgoing:
 *       my-channel:
 *         connector: helidon-nats
 *         subject: "my.subject"
 *     incoming:
 *       other-channel:
 *         connector: helidon-nats
 *         subject: "other.subject"
 * }</pre>
 *
 * <h2>Usage</h2>
 * <p>Use standard MicroProfile Reactive Messaging annotations:
 *
 * <pre>{@code
 * @ApplicationScoped
 * public class MyService {
 *
 *     @Outgoing("my-channel")
 *     public Multi<String> generateMessages() {
 *         return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
 *             .map(tick -> "Message " + tick);
 *     }
 *
 *     @Incoming("other-channel")
 *     public void processMessage(String message) {
 *         System.out.println("Received: " + message);
 *     }
 * }
 * }</pre>
 */
package io.helidon.messaging.connectors.nats;
