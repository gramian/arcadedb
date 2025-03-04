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
package performance;

import com.arcadedb.GlobalConfiguration;
import com.arcadedb.NullLogger;
import com.arcadedb.log.LogManager;
import com.arcadedb.utility.FileUtils;

import java.io.*;

public abstract class PerformanceTest {
  public final static String DATABASE_PATH = "target/databases/performance";

  protected String getDatabasePath() {
    return PerformanceTest.DATABASE_PATH;
  }

  public static void clean() {
    clean("high-performance");
  }

  public static void clean(final String profile) {
    GlobalConfiguration.PROFILE.setValue(profile);

    LogManager.instance().setLogger(NullLogger.INSTANCE);

    final File dir = new File(PerformanceTest.DATABASE_PATH);
    FileUtils.deleteRecursively(dir);
    dir.mkdirs();
  }
}
