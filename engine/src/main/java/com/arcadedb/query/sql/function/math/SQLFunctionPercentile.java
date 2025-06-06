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
package com.arcadedb.query.sql.function.math;

import com.arcadedb.database.Identifiable;
import com.arcadedb.query.sql.executor.CommandContext;
import com.arcadedb.query.sql.executor.MultiValue;
import com.arcadedb.query.sql.function.SQLFunctionAbstract;

import java.util.*;

/**
 * Computes the percentile for a field. Nulls are ignored in the calculation.
 *
 * @author Fabrizio Fortino
 */
public class SQLFunctionPercentile extends SQLFunctionAbstract {
  public static final String NAME = "percentile";

  protected final List<Double> quantiles = new ArrayList<Double>();
  private final   List<Number> values    = new ArrayList<Number>();

  public SQLFunctionPercentile() {
    this(NAME);
  }

  public SQLFunctionPercentile(final String iName) {
    super(iName);
  }

  @Override
  public Object execute(final Object self, final Identifiable currentRecord, final Object currentResult, final Object[] params,
      final CommandContext context) {

    if (quantiles.isEmpty()) { // set quantiles once
      for (int i = 1; i < params.length; ++i) {
        this.quantiles.add(Double.parseDouble(params[i].toString()));
      }
    }

    if (params[0] instanceof Number number) {
      addValue(number);
    } else if (MultiValue.isMultiValue(params[0])) {
      for (final Object n : MultiValue.getMultiValueIterable(params[0])) {
        addValue((Number) n);
      }
    }
    return null;
  }

  @Override
  public boolean aggregateResults() {
    return true;
  }

  @Override
  public Object getResult() {
    return this.evaluate(this.values);
  }

  @Override
  public String getSyntax() {
    return NAME + "(<field>, <quantile> [,<quantile>*])";
  }

  private void addValue(final Number value) {
    if (value != null) {
      this.values.add(value);
    }
  }

  private Object evaluate(final List<Number> iValues) {
    if (iValues.isEmpty())  // result set is empty
      return null;

    if (quantiles.size() > 1) {
      final List<Number> results = new ArrayList<Number>(this.quantiles.size());
      for (final Double q : this.quantiles)
        results.add(this.evaluate(iValues, q));

      return results;
    } else
      return this.evaluate(iValues, this.quantiles.getFirst());
  }

  private Number evaluate(final List<Number> iValues, final double iQuantile) {
    iValues.sort((o1, o2) -> {
      final double d1 = o1.doubleValue();
      final double d2 = o2.doubleValue();
      return Double.compare(d1, d2);
    });

    final double n = iValues.size();
    final double pos = iQuantile * (n + 1);

    if (pos < 1)
      return iValues.getFirst();

    if (pos >= n)
      return iValues.get((int) n - 1);

    final double fpos = Math.floor(pos);
    final int intPos = (int) fpos;
    final double dif = pos - fpos;

    final double lower = iValues.get(intPos - 1).doubleValue();
    final double upper = iValues.get(intPos).doubleValue();
    return lower + dif * (upper - lower);
  }
}
