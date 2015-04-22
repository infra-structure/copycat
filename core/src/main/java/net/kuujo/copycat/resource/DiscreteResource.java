/*
 * Copyright 2015 the original author or authors.
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
package net.kuujo.copycat.resource;

import net.kuujo.copycat.io.Buffer;
import net.kuujo.copycat.io.serializer.CopycatSerializer;
import net.kuujo.copycat.protocol.Protocol;

import java.util.concurrent.CompletableFuture;

/**
 * Discrete resource.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public abstract class DiscreteResource<T extends DiscreteResource<?, U>, U extends Resource<?>> extends AbstractResource<U> {
  protected final Protocol protocol;
  protected final ReplicationStrategy replicationStrategy;
  protected final CopycatSerializer serializer;

  protected DiscreteResource(DiscreteResourceConfig config) {
    super(config.resolve());
    this.protocol = config.getProtocol();
    this.replicationStrategy = config.getReplicationStrategy();
    this.serializer = config.getSerializer();
  }

  /**
   * Commits an entry to the resource.
   */
  protected abstract Buffer commit(Buffer key, Buffer entry, Buffer result);

  @Override
  @SuppressWarnings("unchecked")
  public CompletableFuture<U> open() {
    protocol.handler(this::commit);
    return super.open().thenCompose(v -> protocol.open(this)).thenApply(v -> (U) this);
  }

  @Override
  public CompletableFuture<Void> close() {
    return protocol.close().thenCompose(v -> super.close());
  }

  /**
   * Discrete resource builder.
   *
   * @param <T> The resource builder type.
   * @param <U> The discrete resource type.
   */
  public static abstract class Builder<T extends Builder<T, U>, U extends DiscreteResource<U, ?>> extends Resource.Builder<T, U> {
    private final DiscreteResourceConfig config;

    protected Builder(DiscreteResourceConfig config) {
      super(config);
      this.config = config;
    }

    /**
     * Sets the resource protocol.
     *
     * @param protocol The resource protocol.
     * @return The resource builder.
     */
    @SuppressWarnings("unchecked")
    public T withProtocol(Protocol protocol) {
      config.setProtocol(protocol);
      return (T) this;
    }

    /**
     * Sets the resource replication strategy.
     *
     * @param replicationStrategy The resource replication strategy.
     * @return The resource builder.
     */
    @SuppressWarnings("unchecked")
    public T withReplicationStrategy(ReplicationStrategy replicationStrategy) {
      config.setReplicationStrategy(replicationStrategy);
      return (T) this;
    }

    /**
     * Sets the resource serializer.
     *
     * @param serializer The resource serializer.
     * @return The resource builder.
     */
    @SuppressWarnings("unchecked")
    public T withSerializer(CopycatSerializer serializer) {
      config.setSerializer(serializer);
      return (T) this;
    }
  }

}