/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
 * Virtual Thread testing features for Helidon test extensions.
 */
module io.helidon.common.testing.vitualthreads {
    requires jdk.jfr;
    exports io.helidon.common.testing.virtualthreads to
            io.helidon.microprofile.testing,
            io.helidon.microprofile.testing.junit5,
            io.helidon.microprofile.testing.testng,
            io.helidon.webserver.testing.junit5;
}
