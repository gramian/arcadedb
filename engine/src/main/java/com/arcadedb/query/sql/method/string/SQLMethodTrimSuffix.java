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
package com.arcadedb.query.sql.method.string;

import com.arcadedb.database.Identifiable;
import com.arcadedb.query.sql.executor.CommandContext;
import com.arcadedb.query.sql.method.AbstractSQLMethod;

/**
 * @author Johann Sorel (Geomatys)
 * @author Luca Garulli (l.garulli--(at)--gmail.com)
 */
public class SQLMethodTrimSuffix extends AbstractSQLMethod {

  public static final String NAME = "trimsuffix";

  public SQLMethodTrimSuffix() {
    super(NAME, 1);
  }

  @Override
  public Object execute( final Object value, final Identifiable iRecord, final CommandContext iContext, final Object[] iParams) {
    if (value == null || null == iParams || null == iParams[0])
      return value;

    final String strval = value.toString();
    final String suffix = iParams[0].toString();

    if (strval.endsWith(suffix))
      return strval.substring(0,strval.length() - suffix.length());
    else
      return strval;
  }
}