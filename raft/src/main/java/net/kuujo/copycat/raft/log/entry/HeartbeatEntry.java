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
package net.kuujo.copycat.raft.log.entry;

import net.kuujo.copycat.io.Buffer;
import net.kuujo.copycat.io.serializer.SerializeWith;
import net.kuujo.copycat.io.serializer.Serializer;
import net.kuujo.copycat.io.util.ReferenceManager;

/**
 * Heart beat entry.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
@SerializeWith(id=1007)
public class HeartbeatEntry extends TimestampedEntry<HeartbeatEntry> {
  private int memberId;

  public HeartbeatEntry(ReferenceManager<Entry<?>> referenceManager) {
    super(referenceManager);
  }

  /**
   * Sets the heartbeat member ID.
   *
   * @param memberId The member ID.
   * @return The heartbeat entry.
   */
  public HeartbeatEntry setMemberId(int memberId) {
    this.memberId = memberId;
    return this;
  }

  /**
   * Returns the member ID.
   *
   * @return The member ID.
   */
  public int getMemberId() {
    return memberId;
  }

  @Override
  public void writeObject(Buffer buffer, Serializer serializer) {
    super.writeObject(buffer, serializer);
    buffer.writeInt(memberId);
  }

  @Override
  public void readObject(Buffer buffer, Serializer serializer) {
    super.readObject(buffer, serializer);
    memberId = buffer.readInt();
  }

  @Override
  public String toString() {
    return String.format("%s[index=%d, term=%d, member=%d, timestamp=%d]", getClass().getSimpleName(), getIndex(), getTerm(), memberId, getTimestamp());
  }

}