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
package net.kuujo.copycat.protocol.raft;

import net.kuujo.copycat.protocol.raft.rpc.*;

import java.util.concurrent.CompletableFuture;

/**
 * Start state.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
class StartState extends RaftState {

  public StartState(RaftProtocol context) {
    super(context);
  }

  @Override
  public Type type() {
    return Type.START;
  }

  @Override
  protected CompletableFuture<AppendResponse> append(AppendRequest request) {
    return exceptionalFuture(new IllegalStateException("inactive state"));
  }

  @Override
  protected CompletableFuture<SyncResponse> sync(SyncRequest request) {
    return exceptionalFuture(new IllegalStateException("inactive state"));
  }

  @Override
  protected CompletableFuture<PollResponse> poll(PollRequest request) {
    return exceptionalFuture(new IllegalStateException("inactive state"));
  }

  @Override
  protected CompletableFuture<VoteResponse> vote(VoteRequest request) {
    return exceptionalFuture(new IllegalStateException("inactive state"));
  }

  @Override
  protected CompletableFuture<WriteResponse> write(WriteRequest request) {
    return exceptionalFuture(new IllegalStateException("inactive state"));
  }

  @Override
  protected CompletableFuture<ReadResponse> read(ReadRequest request) {
    return exceptionalFuture(new IllegalStateException("inactive state"));
  }

  @Override
  protected CompletableFuture<DeleteResponse> delete(DeleteRequest request) {
    return exceptionalFuture(new IllegalStateException("inactive state"));
  }

  @Override
  public CompletableFuture<Response> handle(Request request) {
    return exceptionalFuture(new IllegalStateException("inactive state"));
  }

}