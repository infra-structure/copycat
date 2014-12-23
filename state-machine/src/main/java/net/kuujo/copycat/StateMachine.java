/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat;

import net.kuujo.copycat.cluster.ClusterConfig;
import net.kuujo.copycat.internal.DefaultStateMachine;
import net.kuujo.copycat.internal.util.Services;
import net.kuujo.copycat.spi.ExecutionContext;

import java.util.concurrent.CompletableFuture;

/**
 * State machine.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface StateMachine<T> extends CopycatResource {

  /**
   * Creates a new state machine.
   *
   * @param name The state machine resource name.
   * @param uri The state machine member URI.
   * @param stateType The state machine state type.
   * @param initialState The state machine state.
   * @return The state machine.
   */
  static <T> StateMachine<T> create(String name, String uri, Class<T> stateType, T initialState) {
    return create(name, uri, stateType, initialState, Services.load("copycat.cluster", ClusterConfig.class), new StateMachineConfig(), ExecutionContext.create());
  }

  /**
   * Creates a new state machine.
   *
   * @param name The state machine resource name.
   * @param uri The state machine member URI.
   * @param stateType The state machine state type.
   * @param initialState The state machine state.
   * @param config The state machine configuration.
   * @return The state machine.
   */
  static <T> StateMachine<T> create(String name, String uri, Class<T> stateType, T initialState, StateMachineConfig config) {
    return create(name, uri, stateType, initialState, Services.load("copycat.cluster", ClusterConfig.class), config, ExecutionContext.create());
  }

  /**
   * Creates a new state machine.
   *
   * @param name The state machine resource name.
   * @param uri The state machine member URI.
   * @param stateType The state machine state type.
   * @param initialState The state machine state.
   * @param config The state machine configuration.
   * @param context The user execution context.
   * @return The state machine.
   */
  static <T> StateMachine<T> create(String name, String uri, Class<T> stateType, T initialState, StateMachineConfig config, ExecutionContext context) {
    return create(name, uri, stateType, initialState, Services.load("copycat.cluster", ClusterConfig.class), config, context);
  }

  /**
   * Creates a new state machine.
   *
   * @param name The state machine resource name.
   * @param uri The state machine member URI.
   * @param stateType The state machine state type.
   * @param initialState The state machine state.
   * @param cluster The state machine cluster configuration.
   * @return The state machine.
   */
  static <T> StateMachine<T> create(String name, String uri, Class<T> stateType, T initialState, ClusterConfig cluster) {
    return create(name, uri, stateType, initialState, cluster, new StateMachineConfig(), ExecutionContext.create());
  }

  /**
   * Creates a new state machine.
   *
   * @param name The state machine resource name.
   * @param uri The state machine member URI.
   * @param stateType The state machine state type.
   * @param initialState The state machine state.
   * @param cluster The state machine cluster configuration.
   * @param context The user execution context.
   * @return The state machine.
   */
  static <T> StateMachine<T> create(String name, String uri, Class<T> stateType, T initialState, ClusterConfig cluster, ExecutionContext context) {
    return create(name, uri, stateType, initialState, cluster, new StateMachineConfig(), context);
  }

  /**
   * Creates a new state machine.
   *
   * @param name The state machine resource name.
   * @param uri The state machine member URI.
   * @param stateType The state machine state type.
   * @param initialState The state machine state.
   * @param cluster The state machine cluster configuration.
   * @param config The state machine configuration.
   * @param context The user execution context.
   * @return The state machine.
   */
  static <T> StateMachine<T> create(String name, String uri, Class<T> stateType, T initialState, ClusterConfig cluster, StateMachineConfig config, ExecutionContext context) {
    return new DefaultStateMachine<>(stateType, initialState, StateLog.create(name, uri, cluster, config, context));
  }

  /**
   * Creates a state machine proxy.
   *
   * @param type The proxy interface.
   * @param <U> The proxy type.
   * @return The proxy object.
   */
  <U> U createProxy(Class<U> type);

  /**
   * Submits a command to the state machine.
   *
   * @param command The command to commit.
   * @param args The command arguments.
   * @param <U> The command output type.
   * @return A completable future to be completed with the command result.
   */
  <U> CompletableFuture<U> submit(String command, Object... args);

}