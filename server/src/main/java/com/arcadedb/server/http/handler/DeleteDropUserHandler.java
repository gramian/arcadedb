/*
 * Copyright © 2021-present Arcade Data Ltd (info@arcadedata.com)
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
 *
 * SPDX-FileCopyrightText: 2021-present Arcade Data Ltd (info@arcadedata.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package com.arcadedb.server.http.handler;

import com.arcadedb.serializer.json.JSONObject;
import com.arcadedb.server.http.HttpServer;
import com.arcadedb.server.security.ServerSecurityUser;
import io.micrometer.core.instrument.Metrics;
import io.undertow.server.HttpServerExchange;

import java.util.*;

/**
 * Drops a server user.
 *
 * @author Luca Garulli (l.garulli@arcadedata.com)
 * @Deprecated Use the generic @see PostServerCommandHandler
 */
@Deprecated
public class DeleteDropUserHandler extends AbstractServerHttpHandler {
  public DeleteDropUserHandler(final HttpServer httpServer) {
    super(httpServer);
  }

  @Override
  public ExecutionResponse execute(final HttpServerExchange exchange, final ServerSecurityUser user, final JSONObject payload) {
    checkRootUser(user);

    final Deque<String> userNamePar = exchange.getQueryParameters().get("userName");
    String userName = userNamePar.isEmpty() ? null : userNamePar.getFirst().trim();
    if (userName != null && userName.isEmpty())
      userName = null;

    if (userName == null)
      return new ExecutionResponse(400, "{ \"error\" : \"User name parameter is null\"}");

    Metrics.counter("http.drop-user").increment();
    ;

    final boolean result = httpServer.getServer().getSecurity().dropUser(userName);
    if (!result)
      throw new RuntimeException("User '" + userName + "' not found on server");

    return new ExecutionResponse(204, "");
  }
}
