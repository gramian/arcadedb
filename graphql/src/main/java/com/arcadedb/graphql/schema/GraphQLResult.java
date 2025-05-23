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
package com.arcadedb.graphql.schema;

import com.arcadedb.database.RID;
import com.arcadedb.query.sql.executor.ResultInternal;

import java.util.*;

import static com.arcadedb.schema.Property.RID_PROPERTY;

/**
 * @author Luca Garulli (l.garulli@arcadedata.com)
 */
public class GraphQLResult extends ResultInternal {
  RID identity;

  public GraphQLResult() {
  }

  public GraphQLResult(final Map<String, Object> map) {
    super(map);
    identity = (RID) map.get(RID_PROPERTY);
  }

  @Override
  public Optional<RID> getIdentity() {
    return identity == null ? Optional.empty() : Optional.of(identity);
  }
}
