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
package com.arcadedb;

import com.arcadedb.database.MutableDocument;
import com.arcadedb.engine.LocalBucket;
import com.arcadedb.schema.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Populate the database with records 3X the page size of buckets.
 */
public class LargeRecordsTest extends TestHelper {
  private static final int TOT = 1000;

  @Test
  public void testPopulate() {
    // EMPTY METHOD
  }

  @Test
  public void testScan() {
    final AtomicInteger total = new AtomicInteger();

    database.begin();

    final DocumentType type = database.getSchema().getType("BigRecords");
    final long pageOverSize = ((LocalBucket) type.getBuckets(true).getFirst()).getPageSize() * 3;

    database.scanType("BigRecords", true, record -> {
      assertThat(record).isNotNull();
      final String buffer = record.asDocument().getString("buffer");
      assertThat(buffer.length()).isEqualTo(pageOverSize);
      total.incrementAndGet();
      return true;
    });

    assertThat(total.get()).isEqualTo(TOT);

    database.commit();
  }

  protected void beginTest() {
    database.transaction(() -> {
      final DocumentType type = database.getSchema().getOrCreateDocumentType("BigRecords");

      final long pageOverSize = ((LocalBucket) type.getBuckets(true).getFirst()).getPageSize() * 3L;

      final StringBuilder buffer = new StringBuilder();
      for (int k = 0; k < pageOverSize; ++k)
        buffer.append('8');

      for (int i = 0; i < TOT; ++i) {
        final MutableDocument v = database.newDocument("BigRecords");
        v.set("id", i);
        v.set("buffer", buffer.toString());
        v.save();
      }
    });
  }
}
