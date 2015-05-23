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
package net.kuujo.copycat.cluster;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import net.kuujo.copycat.ConfigurationException;
import net.kuujo.copycat.io.serializer.Serializer;
import net.kuujo.copycat.util.ExecutionContext;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Netty cluster implementation.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class NettyCluster extends AbstractCluster {

  /**
   * Returns a new Netty cluster builder.
   *
   * @return A new Netty cluster builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  private final EventLoopGroup eventLoopGroup;

  public NettyCluster(EventLoopGroup eventLoopGroup, NettyLocalMember localMember, Collection<NettyRemoteMember> remoteMembers, Serializer serializer) {
    super(localMember, remoteMembers, serializer);
    this.eventLoopGroup = eventLoopGroup;
    localMember.setSerializer(serializer.copy());
    remoteMembers.forEach(m -> m.setSerializer(serializer.copy()).setEventLoopGroup(eventLoopGroup));
  }

  @Override
  protected AbstractRemoteMember createRemoteMember(AbstractMember.Info info) {
    return new NettyRemoteMember((NettyMember.Info) info, new ExecutionContext(String.format("copycat-cluster-%d", info.id())))
      .setSerializer(serializer.copy())
      .setEventLoopGroup(eventLoopGroup);
  }

  @Override
  public CompletableFuture<Void> close() {
    return super.close().thenRun(eventLoopGroup::shutdownGracefully);
  }

  @Override
  public String toString() {
    return String.format("NettyCluster[member=%d, members=%s]", localMember.id(), members.values().stream().map(Member::id).collect(Collectors.toList()));
  }

  /**
   * Netty cluster builder.
   */
  public static class Builder extends AbstractCluster.Builder<Builder, NettyMember> {
    private String host;
    private int port;
    private EventLoopGroup eventLoopGroup;

    private Builder() {
    }

    /**
     * Sets the server host.
     *
     * @param host The server host.
     * @return The Netty cluster builder.
     */
    public Builder withHost(String host) {
      this.host = host;
      return this;
    }

    /**
     * Sets the server port.
     *
     * @param port The server port.
     * @return The Netty cluster builder.
     */
    public Builder withPort(int port) {
      this.port = port;
      return this;
    }

    /**
     * Sets the Netty event loop group.
     *
     * @param eventLoopGroup The Netty event loop group.
     * @return The Netty cluster builder.
     */
    public Builder withEventLoopGroup(EventLoopGroup eventLoopGroup) {
      this.eventLoopGroup = eventLoopGroup;
      return this;
    }

    @Override
    public ManagedCluster build() {
      NettyMember member = members.remove(memberId);
      NettyMember.Info info;
      if (member != null) {
        info = new NettyMember.Info(memberId, Member.Type.ACTIVE, member.address());
      } else {
        if (host == null)
          throw new ConfigurationException("member host must be configured");
        info = new NettyMember.Info(memberId, memberType != null ? memberType : Member.Type.REMOTE, new InetSocketAddress(host, port));
      }

      NettyLocalMember localMember = new NettyLocalMember(info, new ExecutionContext(String.format("copycat-cluster-%d", memberId)));
      return new NettyCluster(eventLoopGroup != null ? eventLoopGroup : new NioEventLoopGroup(), localMember, members.values().stream().map(m -> (NettyRemoteMember) m).collect(Collectors.toList()), serializer != null ? serializer : new Serializer());
    }
  }

}