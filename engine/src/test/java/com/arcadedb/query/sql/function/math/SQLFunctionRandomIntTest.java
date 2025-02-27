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

import com.arcadedb.TestHelper;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Luca Garulli (l.garulli@arcadedata.com)
 */
public class SQLFunctionRandomIntTest {
  private SQLFunctionRandomInt random;

  @BeforeEach
  public void setup() {
    random = new SQLFunctionRandomInt();
  }

  @Test
  public void testEmpty() {
    final Object result = random.getResult();
    assertThat(result).isNull();
  }

  @Test
  public void testResultWithIntParameter() {
    final Integer result = (Integer) random.execute(null, null, null, new Integer[] { 1000 }, null);
    assertThat(result).isNotNull();
  }

  @Test
  public void testResultWithStringParameter() {
    final Integer result = (Integer) random.execute(null, null, null, new Object[] { "1000" }, null);
    assertThat(result).isNotNull();
  }

  @Test
  public void testQuery() throws Exception {
    TestHelper.executeInNewDatabase("SQLFunctionRandomInt", (db) -> {
      final ResultSet result = db.query("sql", "select randomInt(1000) as random");
      assertThat((Iterator<? extends Result>) result).isNotNull();
      assertThat(result.next().<Integer>getProperty("random")).isNotNull();
    });
  }
}
