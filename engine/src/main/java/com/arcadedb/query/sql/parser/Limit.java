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
/* Generated By:JJTree: Do not edit this line. OLimit.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_USERTYPE_VISIBILITY_PUBLIC=true */
package com.arcadedb.query.sql.parser;

import com.arcadedb.exception.CommandExecutionException;
import com.arcadedb.query.sql.executor.CommandContext;

import java.util.*;

public class Limit extends SimpleNode {
  protected PInteger       num;
  protected InputParameter inputParam;

  public Limit(final int id) {
    super(id);
  }

  public void toString(final Map<String, Object> params, final StringBuilder builder) {
    if (num == null && inputParam == null)
      return;

    builder.append(" LIMIT ");
    if (num != null)
      num.toString(params, builder);
    else
      inputParam.toString(params, builder);

  }

  public int getValue(final CommandContext context) {
    if (num != null)
      return num.getValue().intValue();

    if (inputParam != null) {
      final Object paramValue = inputParam.getValue(context.getInputParameters());
      if (paramValue instanceof Number number)
        return number.intValue();
      else
        throw new CommandExecutionException("Invalid value for LIMIT: " + paramValue);
    }
    throw new CommandExecutionException("No value for LIMIT");
  }

  public Limit setValue(final int value) {
    num = new PInteger(-1).setValue(value);
    return this;
  }

  public Limit copy() {
    final Limit result = new Limit(-1);
    result.num = num == null ? null : num.copy();
    result.inputParam = inputParam == null ? null : inputParam.copy();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    final Limit oLimit = (Limit) o;

    if (!Objects.equals(num, oLimit.num))
      return false;
    return Objects.equals(inputParam, oLimit.inputParam);
  }

  @Override
  public int hashCode() {
    int result = num != null ? num.hashCode() : 0;
    result = 31 * result + (inputParam != null ? inputParam.hashCode() : 0);
    return result;
  }
}
/* JavaCC - OriginalChecksum=1063b9489290bb08de6048ba55013171 (do not edit this line) */
