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
package net.kuujo.copycat.protocol;

import java.io.Serializable;

/**
 * A base request.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
@SuppressWarnings("serial")
public abstract class Request implements Serializable {
  private final Object id;

  protected Request(Object id) {
    this.id = id;
  }

  /**
   * Returns the request correlation ID.
   *
   * @return The request correlation ID.
   */
  public Object id() {
    return id;
  }

  @Override
  public String toString() {
    return String.format("Request[id=%s]", id);
  }

}