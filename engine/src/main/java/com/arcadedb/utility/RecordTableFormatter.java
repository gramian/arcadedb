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
package com.arcadedb.utility;

import com.arcadedb.database.Document;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.schema.Property;

import java.util.*;

import static com.arcadedb.schema.Property.RID_PROPERTY;
import static com.arcadedb.schema.Property.TYPE_PROPERTY;

public class RecordTableFormatter extends TableFormatter {

  public static class TableRecordRow implements TableRow {
    private final Result result;

    public TableRecordRow(final Result result) {
      this.result = result;
    }

    @Override
    public Object getField(final String field) {
      if (field.equalsIgnoreCase(RID_PROPERTY)) {
        if (result.getIdentity().isPresent())
          return result.getIdentity().get();
      } else if (field.equalsIgnoreCase(TYPE_PROPERTY)) {
        if (result.getRecord().isPresent())
          return ((Document) result.getRecord().get()).getTypeName();
      }
      return result.getProperty(field);
    }

    @Override
    public Set<String> getFields() {
      return result.getPropertyNames();
    }
  }

  public RecordTableFormatter(final TableOutput iConsole) {
    super(iConsole);
  }

  public void writeRecords(final List<Result> records, final int limit) {
    final List<TableRow> rows = new ArrayList<>();
    for (final Result record : records)
      rows.add(new TableRecordRow(record));

    super.writeRows(rows, limit);
  }
}
