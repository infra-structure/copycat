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
package net.kuujo.copycat.raft.rpc;

import net.kuujo.copycat.io.Buffer;
import net.kuujo.copycat.io.serializer.Serializer;
import net.kuujo.copycat.io.util.ReferenceManager;
import net.kuujo.copycat.raft.Operation;

import java.util.Objects;

/**
 * Protocol command request.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class SubmitRequest extends AbstractRequest<SubmitRequest> {
  private static final ThreadLocal<Builder> builder = new ThreadLocal<Builder>() {
    @Override
    protected Builder initialValue() {
      return new Builder();
    }
  };

  /**
   * Returns a new submit request builder.
   *
   * @return A new submit request builder.
   */
  public static Builder builder() {
    return builder.get().reset();
  }

  /**
   * Returns a submit request builder for an existing request.
   *
   * @param request The request to build.
   * @return The submit request builder.
   */
  public static Builder builder(SubmitRequest request) {
    return builder.get().reset(request);
  }

  private Operation operation;

  public SubmitRequest(ReferenceManager<SubmitRequest> referenceManager) {
    super(referenceManager);
  }

  @Override
  public Type type() {
    return Type.SUBMIT;
  }

  /**
   * Returns the operation.
   *
   * @return The operation.
   */
  public Operation operation() {
    return operation;
  }

  @Override
  public void readObject(Buffer buffer, Serializer serializer) {
    operation = serializer.readObject(buffer);
  }

  @Override
  public void writeObject(Buffer buffer, Serializer serializer) {
    serializer.writeObject(operation, buffer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(operation);
  }

  @Override
  public boolean equals(Object object) {
    return object instanceof SubmitRequest && ((SubmitRequest) object).operation.equals(operation);
  }

  @Override
  public String toString() {
    return String.format("%s[operation=%s]", getClass().getSimpleName(), operation);
  }

  /**
   * Write request builder.
   */
  public static class Builder extends AbstractRequest.Builder<Builder, SubmitRequest> {

    protected Builder() {
      super(SubmitRequest::new);
    }

    /**
     * Sets the request operation.
     *
     * @param operation The request operation.
     * @return The request builder.
     */
    public Builder withOperation(Operation operation) {
      if (operation == null)
        throw new NullPointerException("operation cannot be null");
      request.operation = operation;
      return this;
    }

    @Override
    public SubmitRequest build() {
      super.build();
      if (request.operation == null)
        throw new NullPointerException("operation cannot be null");
      return request;
    }

    @Override
    public int hashCode() {
      return Objects.hash(request);
    }

    @Override
    public boolean equals(Object object) {
      return object instanceof Builder && ((Builder) object).request.equals(request);
    }

    @Override
    public String toString() {
      return String.format("%s[request=%s]", getClass().getCanonicalName(), request);
    }

  }

}