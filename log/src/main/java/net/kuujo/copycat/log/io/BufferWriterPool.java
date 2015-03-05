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
package net.kuujo.copycat.log.io;

import net.kuujo.copycat.log.io.util.ReferenceCounted;
import net.kuujo.copycat.log.io.util.ReferenceManager;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Buffer writer pool.
 * <p>
 * The writer pool reduces garbage produced by frequent reads by tracking references to existing writers and recycling
 * writers once they're closed.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class BufferWriterPool<T extends Buffer & ReferenceCounted<? extends Buffer>, U extends BufferWriter<U, T>> implements ReferenceManager<U> {
  private final T buffer;
  private final BufferWriterFactory<U, T> factory;
  private final Queue<U> pool = new ArrayDeque<>(1024);

  public BufferWriterPool(T buffer, BufferWriterFactory<U, T> factory) {
    if (buffer == null)
      throw new NullPointerException("buffer cannot be null");
    if (factory == null)
      throw new NullPointerException("factory cannot be null");
    this.buffer = buffer;
    this.factory = factory;
  }

  /**
   * Acquires a new multi buffer writer.
   */
  public U acquire() {
    U writer = pool.poll();
    if (writer == null) {
      synchronized (pool) {
        writer = pool.poll();
        if (writer == null) {
          writer = factory.createWriter(buffer, 0, 0, this);
        }
      }
    }
    buffer.acquire();
    return writer;
  }

  @Override
  public void release(U reference) {
    buffer.release();
    pool.add(reference);
  }

}