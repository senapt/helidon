/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient.spi;

import io.helidon.common.Builder;
import io.helidon.common.GenericType;
import io.helidon.common.config.Config;
import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatements;

/**
 * Provider specific {@link DbClient} builder.
 *
 * @param <T> type of the builder extending or implementing this interface.
 */
public interface DbClientBuilder<T extends DbClientBuilder<T>> extends Builder<T, DbClient> {

    /**
     * Use database connection configuration from configuration file.
     *
     * @param config {@link Config} instance with database connection attributes
     * @return database provider builder
     */
    T config(Config config);

    /**
     * Set database connection string (URL).
     *
     * @param url database connection string
     * @return database provider builder
     */
    T url(String url);

    /**
     * Set database connection username.
     *
     * @param username database connection user name
     * @return database provider builder
     */
    T username(String username);

    /**
     * Set database connection password.
     *
     * @param password database connection password
     * @return database provider builder
     */
    T password(String password);

    /**
     * Missing values in named parameters {@link java.util.Map} are considered as null values.
     * When set to {@code true}, named parameters value missing in the {@code Map} is considered
     * as {@code null}. When set to {@code false}, any parameter value missing in the {@code Map}
     * will cause an exception.
     * @param missingMapParametersAsNull whether missing values in named parameters {@code Map}
     *                                   are considered as null values
     * @return updated builder instance
     */
    T missingMapParametersAsNull(boolean missingMapParametersAsNull);

    /**
     * Statements to use either from configuration
     * or manually configured.
     *
     * @param statements Statements to use
     * @return updated builder instance
     */
    T statements(DbStatements statements);

    /**
     * Database schema mappers provider.
     *
     * @param provider database schema mappers provider to use
     * @return updated builder instance
     */
    T addMapperProvider(DbMapperProvider provider);

    /**
     * Add a custom mapper.
     *
     * @param dbMapper    the mapper capable of mapping the mappedClass to various database objects
     * @param mappedClass class that this mapper supports
     * @param <TYPE>      type of the supported class
     * @return updated builder instance.
     */
    <TYPE> T addMapper(DbMapper<TYPE> dbMapper, Class<TYPE> mappedClass);

    /**
     * Add a custom mapper with generic types support.
     *
     * @param dbMapper    the mapper capable of mapping the mappedClass to various database objects
     * @param mappedType  type that this mapper supports
     * @param <TYPE>      type of the supported class
     * @return updated builder instance.
     */
    <TYPE> T addMapper(DbMapper<TYPE> dbMapper, GenericType<TYPE> mappedType);

    /**
     * Mapper manager for generic mapping, such as mapping of parameters to expected types.
     *
     * @param manager mapper manager
     * @return updated builder instance
     */
    T mapperManager(MapperManager manager);

    /**
     * Mapper manager of all configured {@link DbMapper mappers}.
     *
     * @param manager mapper manager
     * @return updated builder instance
     */
    T dbMapperManager(DbMapperManager manager);

    /**
     * Add an interceptor.
     * This allows to add implementation of tracing, metrics, logging etc. without the need to hard-code these into
     * the base.
     *
     * @param clientService interceptor instance
     * @return updated builder instance
     */
    T addService(DbClientService clientService);

}
