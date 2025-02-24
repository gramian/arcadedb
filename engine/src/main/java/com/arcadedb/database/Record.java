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
package com.arcadedb.database;

import com.arcadedb.serializer.json.JSONObject;
import com.arcadedb.utility.ExcludeFromJacocoGeneratedReport;

/**
 * Generic interface representing a record as an entry in the database.
 *
 * @author Luca Garulli
 */
@ExcludeFromJacocoGeneratedReport
public interface Record extends Identifiable {
  RID getIdentity();

  byte getRecordType();

  Database getDatabase();

  void reload();

  void delete();

  default JSONObject toJSON() {
    return toJSON(true);
  }

  JSONObject toJSON(boolean includeMetadata);

  /**
   * Returns the binary record size if known, otherwise -1
   */
  int size();
}
