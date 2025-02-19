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
package com.arcadedb.query.sql.executor;

import com.arcadedb.TestHelper;
import com.arcadedb.exception.CommandExecutionException;
import com.arcadedb.schema.DocumentType;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.fail;

public class CheckTypeTypeStepTest {

  @Test
  public void shouldCheckSubclasses() throws Exception {
    TestHelper.executeInNewDatabase((db) -> {
      final BasicCommandContext context = new BasicCommandContext();
      context.setDatabase(db);
      final DocumentType parentClass = TestHelper.createRandomType(db);
      final DocumentType childClass = TestHelper.createRandomType(db).addSuperType(parentClass);
      final CheckTypeTypeStep step = new CheckTypeTypeStep(childClass.getName(), parentClass.getName(), context);

      final ResultSet result = step.syncPull(context, 20);
      assertThat(result.stream().count()).isEqualTo(0);
    });
  }

  @Test
  public void shouldCheckOneType() throws Exception {
    TestHelper.executeInNewDatabase((db) -> {
      final BasicCommandContext context = new BasicCommandContext();
      context.setDatabase(db);
      final String className = TestHelper.createRandomType(db).getName();
      final CheckTypeTypeStep step = new CheckTypeTypeStep(className, className, context);

      final ResultSet result = step.syncPull(context, 20);
      assertThat(result.stream().count()).isEqualTo(0);
    });
  }

  @Test
  public void shouldThrowExceptionWhenClassIsNotParent() throws Exception {
    try {
      TestHelper.executeInNewDatabase((db) -> {
        final BasicCommandContext context = new BasicCommandContext();
        context.setDatabase(db);
        final CheckTypeTypeStep step = new CheckTypeTypeStep(TestHelper.createRandomType(db).getName(),
            TestHelper.createRandomType(db).getName(), context);

        step.syncPull(context, 20);
      });
      fail("Expected CommandExecutionException");
    } catch (final CommandExecutionException e) {
      // OK
    }
  }
}
