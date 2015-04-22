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
package net.kuujo.copycat.raft;

import net.kuujo.copycat.io.Buffer;
import net.kuujo.copycat.io.HeapBuffer;
import net.kuujo.copycat.raft.protocol.*;
import net.kuujo.copycat.raft.storage.RaftEntry;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Passive state.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
class PassiveState extends RemoteState {
  private static final int MAX_BATCH_SIZE = 1024 * 1024;
  private ScheduledFuture<?> currentTimer;
  protected final Buffer KEY = HeapBuffer.allocate(1024, 1024 * 1024);
  protected final Buffer ENTRY = HeapBuffer.allocate(1024, 1024 * 1024);
  protected final Buffer RESULT = HeapBuffer.allocate(1024, 1024 * 1024);

  public PassiveState(RaftContext context) {
    super(context);
  }

  @Override
  public Type type() {
    return Type.PASSIVE;
  }

  @Override
  public synchronized CompletableFuture<RaftState> open() {
    return super.open().thenRun(this::startSyncTimer).thenApply(v -> this);
  }

  /**
   * Starts the sync timer.
   */
  private void startSyncTimer() {
    LOGGER.debug("{} - Setting sync timer", context.getLocalMember().id());
    currentTimer = context.executor().scheduleAtFixedRate(this::sync, 1, context.getHeartbeatInterval(), TimeUnit.MILLISECONDS);
  }

  /**
   * Synchronizes with random nodes via a gossip protocol.
   */
  private void sync() {
    context.checkThread();
    if (isClosed()) return;

    // Create a list of passive members. Construct a new array list since the Java 8 collectors API makes no guarantees
    // about the serializability of the returned list.
    List<RaftMember> passiveMembers = new ArrayList<>(context.getMembers().size());
    context.getMembers().stream()
      .filter(m -> m.type() == RaftMember.Type.PASSIVE && m.id() != context.getLocalMember().id())
      .forEach(passiveMembers::add);

    // Create a random list of three active members.
    Random random = new Random();
    List<RaftMember> randomMembers = new ArrayList<>(3);
    for (int i = 0; i < Math.min(passiveMembers.size(), 3); i++) {
      randomMembers.add(passiveMembers.get(random.nextInt(Math.min(passiveMembers.size(), 3))));
    }

    // Increment the local member version in the vector clock.
    context.setVersion(context.getVersion() + 1);

    Set<Integer> synchronizing = new HashSet<>();
    // For each active member, send membership info to the member.
    for (RaftMember member : randomMembers) {
      // If we're already synchronizing with the given node then skip the synchronization. This is possible in the event
      // that we began sending sync requests during another gossip pass and continue to send entries recursively.
      if (synchronizing.add(member.id())) {
        recursiveSync(member).whenComplete((result, error) -> synchronizing.remove(member.id()));
      }
    }
  }

  /**
   * Recursively sends sync request to the given member.
   */
  private CompletableFuture<Void> recursiveSync(RaftMember member) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    recursiveSync(member, false, future);
    return future;
  }

  /**
   * Recursively sends sync requests to the given member.
   */
  private void recursiveSync(RaftMember member, boolean requireEntries, CompletableFuture<Void> future) {
    // Get a list of entries up to 1MB in size.
    List<RaftEntry> entries = new ArrayList<>(1024);
    long firstIndex = 0;
    if (!context.log().isEmpty() && context.getCommitIndex() != 0) {
      firstIndex = member.commitIndex();
      long index = firstIndex;
      int size = 0;
      while (size < MAX_BATCH_SIZE && index <= context.getCommitIndex()) {
        RaftEntry entry = context.log().getEntry(index);
        size += entry.size();
        entries.add(entry);
        index++;
      }
    }

    // If we have entries to send or if entries are not required for this request then send the sync request.
    if (!requireEntries || !entries.isEmpty()) {
      SyncRequest request = SyncRequest.builder()
        .withLeader(context.getLeader())
        .withTerm(context.getTerm())
        .withLogIndex(member.commitIndex())
        .withMembers(context.getMembers())
        .withEntries(entries)
        .build();

      LOGGER.debug("{} - Sending sync request to {}", context.getLocalMember().id(), member.id());
      context.<SyncRequest, SyncResponse>sendRequest(request, member).whenCompleteAsync((response, error) -> {
        context.checkThread();
        // Always check if the context is still open in order to prevent race conditions in asynchronous callbacks.
        if (isOpen()) {
          if (error == null) {
            // If the response succeeded, update membership info with the target node's membership.
            if (response.status() == Response.Status.OK) {
              context.updateMembers(response.members());
              recursiveSync(member, true, future);
            } else {
              LOGGER.warn("{} - Received error response from {}", context.getLocalMember().id(), member.id());
              future.completeExceptionally(response.error().createException());
            }
          } else {
            // If the request failed then record the member as INACTIVE.
            LOGGER.warn("{} - Sync to {} failed: {}", context.getLocalMember().id(), member, error.getMessage());
            future.completeExceptionally(error);
          }
        }
      }, context.executor());
    } else {
      future.complete(null);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public CompletableFuture<SyncResponse> sync(SyncRequest request) {
    context.checkThread();

    if (request.term() > context.getTerm()) {
      context.setTerm(request.term());
      context.setLeader(request.leader());
    } else if (request.term() == context.getTerm() && context.getLeader() == 0 && request.leader() != 0) {
      context.setLeader(request.leader());
    }

    // Increment the local vector clock version and update cluster members.
    context.setVersion(context.getVersion() + 1);
    context.updateMembers(request.members());

    // If the local log doesn't contain the previous index and the given index is not the first index in the
    // requestor's log then reply immediately.
    if (!context.log().containsIndex(request.logIndex())) {
      return CompletableFuture.completedFuture(logResponse(SyncResponse.builder()
        .withStatus(Response.Status.OK)
        .withMembers(context.getMembers())
        .build()));
    }

    // Iterate through provided entries and append any that are missing from the log. Only committed entries are
    // replicated via gossip, so we don't have to worry about consistency checks here.
    for (RaftEntry entry : request.entries()) {
      if (!context.log().containsIndex(entry.index())) {
        try (RaftEntry transfer = context.log().skip(entry.index() - context.log().lastIndex()).createEntry()) {
          assert entry.index() == transfer.index();
          transfer.write(entry);
        }

        context.log().commit(entry.index());
        context.setCommitIndex(entry.index());

        try {
          RESULT.clear();
          KEY.clear();
          ENTRY.clear();
          entry.readKey(KEY);
          entry.readEntry(ENTRY);
          context.commit(KEY.flip(), ENTRY.flip(), RESULT);
        } catch (Exception e) {
          LOGGER.warn("failed to apply command", e);
        } finally {
          context.setLastApplied(entry.index());
        }
      }
    }

    // Update the recycle index using the highest member's recycle index.
    request.members().stream().mapToLong(RaftMember::recycleIndex).max()
      .ifPresent(compactIndex -> context.setRecycleIndex(Math.max(context.getRecycleIndex(), compactIndex)));

    // Reply with the updated vector clock.
    return CompletableFuture.completedFuture(logResponse(SyncResponse.builder()
      .withStatus(Response.Status.OK)
      .withMembers(context.getMembers())
      .build()));
  }

  @Override
  protected CompletableFuture<AppendResponse> append(AppendRequest request) {
    context.checkThread();
    logRequest(request);
    return CompletableFuture.completedFuture(logResponse(AppendResponse.builder()
      .withStatus(Response.Status.ERROR)
      .withError(RaftError.Type.ILLEGAL_MEMBER_STATE_ERROR)
      .build()));
  }

  @Override
  protected CompletableFuture<PollResponse> poll(PollRequest request) {
    context.checkThread();
    logRequest(request);
    return CompletableFuture.completedFuture(logResponse(PollResponse.builder()
      .withStatus(Response.Status.ERROR)
      .withError(RaftError.Type.ILLEGAL_MEMBER_STATE_ERROR)
      .build()));
  }

  @Override
  protected CompletableFuture<VoteResponse> vote(VoteRequest request) {
    context.checkThread();
    logRequest(request);
    return CompletableFuture.completedFuture(logResponse(VoteResponse.builder()
      .withStatus(Response.Status.ERROR)
      .withError(RaftError.Type.ILLEGAL_MEMBER_STATE_ERROR)
      .build()));
  }

  /**
   * Cancels the sync timer.
   */
  private void cancelSyncTimer() {
    if (currentTimer != null) {
      LOGGER.debug("{} - Cancelling sync timer", context.getLocalMember().id());
      currentTimer.cancel(false);
    }
  }

  @Override
  public synchronized CompletableFuture<Void> close() {
    KEY.close();
    ENTRY.close();
    RESULT.close();
    return super.close().thenRun(this::cancelSyncTimer);
  }

  @Override
  public String toString() {
    return String.format("%s[context=%s]", getClass().getSimpleName(), context);
  }

}