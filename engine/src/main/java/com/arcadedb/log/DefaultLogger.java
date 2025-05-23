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
package com.arcadedb.log;

import com.arcadedb.utility.AnsiLogFormatter;
import com.arcadedb.utility.SystemVariableResolver;

import java.io.*;
import java.util.concurrent.*;
import java.util.logging.LogManager;
import java.util.logging.*;

/**
 * Default Logger implementation that writes to the Java Logging Framework.
 * Set the property `java.util.logging.config.file` to the configuration file to use.
 */
public class DefaultLogger implements Logger {
  private static final String                                          DEFAULT_LOG                  = "com.arcadedb";
  private static final String                                          ENV_INSTALL_CUSTOM_FORMATTER = "arcadedb.installCustomFormatter";
  private static final DefaultLogger                                   instance                     = new DefaultLogger();
  private static final String                                          FILE_LOG_PROPERTIES          = "arcadedb-log.properties";
  private final        ConcurrentMap<String, java.util.logging.Logger> loggersCache                 = new ConcurrentHashMap<>();

  public DefaultLogger() {
    final File logDir = new File("./log");

    try {
      if (!logDir.exists() || !logDir.isDirectory())
        // TRY TO CREATE LOG DIRECTORY
        logDir.mkdirs();
    } catch (final Exception e) {
      // IGNORE
    }

    installCustomFormatter();
  }

  public static DefaultLogger instance() {
    return instance;
  }

  public void installCustomFormatter() {
    InputStream stream = null;

    final String defaultLogConfigurationFile = System.getProperty("java.util.logging.config.file");
    if (defaultLogConfigurationFile != null) {
      final File file = new File(defaultLogConfigurationFile);
      if (file.exists()) {
        try {
          stream = new FileInputStream(file);
        } catch (Exception e) {
          // USE DEFAULT SETTINGS
          System.err.println(
              "Error on loading logging configuration file '" + defaultLogConfigurationFile + "'. Using default settings");
        }
      } else
        System.err.println("Error on loading logging configuration file '" + defaultLogConfigurationFile
            + "': file not found. Using default settings");
    }

    if (stream == null) {
      stream = getClass().getClassLoader().getResourceAsStream(FILE_LOG_PROPERTIES);
      if (stream == null) {
        try {
          stream = new FileInputStream("config/" + FILE_LOG_PROPERTIES);
        } catch (final FileNotFoundException e) {
          // USE DEFAULT SETTINGS
        }
      }
    }

    if (stream != null)
      try {
        LogManager.getLogManager().readConfiguration(stream);
      } catch (final IOException e) {
        // NOT FOUND, APPLY DEFAULTS
        System.err.println("Cannot find ArcadeDB log file `arcadedb-log.properties`. Using default settings");
      }
    else
      System.err.println("Cannot find ArcadeDB log file `arcadedb-log.properties`. Using default settings");

    final boolean installCustomFormatter = Boolean.parseBoolean(
        SystemVariableResolver.INSTANCE.resolveSystemVariables("${" + ENV_INSTALL_CUSTOM_FORMATTER + "}", "true"));

    if (!installCustomFormatter)
      return;

    try {
      // ASSURE TO HAVE THE LOG FORMATTER TO THE CONSOLE EVEN IF NO CONFIGURATION FILE IS TAKEN
      final java.util.logging.Logger log = java.util.logging.Logger.getLogger("");

      if (log.getHandlers().length == 0) {
        // SET DEFAULT LOG FORMATTER
        final Handler h = new ConsoleHandler();
        h.setFormatter(new AnsiLogFormatter());
        log.addHandler(h);
      } else {
        for (final Handler h : log.getHandlers()) {
          if (h instanceof ConsoleHandler && !h.getFormatter().getClass().equals(AnsiLogFormatter.class))
            h.setFormatter(new AnsiLogFormatter());
        }
      }
    } catch (final Exception e) {
      System.err.println("Error while installing custom formatter. Logging could be disabled. Cause: " + e);
    }
  }

  public void log(final Object requester, final Level level, String message, final Throwable exception, final String context,
      final Object arg1, final Object arg2, final Object arg3, final Object arg4, final Object arg5, final Object arg6,
      final Object arg7, final Object arg8, final Object arg9, final Object arg10, final Object arg11, final Object arg12,
      final Object arg13, final Object arg14, final Object arg15, final Object arg16, final Object arg17) {
    if (message == null)
      return;

    //level = Level.SEVERE;

    final String requesterName;
    if (requester instanceof String string)
      requesterName = string;
    else if (requester instanceof Class<?> class1)
      requesterName = class1.getName();
    else if (requester != null)
      requesterName = requester.getClass().getName();
    else
      requesterName = DEFAULT_LOG;

    java.util.logging.Logger log = loggersCache.get(requesterName);
    if (log == null) {
      log = java.util.logging.Logger.getLogger(requesterName);

      if (log != null) {
        final java.util.logging.Logger oldLogger = loggersCache.putIfAbsent(requesterName, log);

        if (oldLogger != null)
          log = oldLogger;
      }
    }

    final boolean hasParams =
        arg1 != null || arg2 != null || arg3 != null || arg4 != null || arg5 != null || arg6 != null || arg7 != null || arg8 != null
            || arg9 != null || arg10 != null || arg11 != null || arg12 != null || arg13 != null || arg14 != null || arg15 != null
            || arg16 != null || arg17 != null;

    if (log == null) {
      if (context != null)
        message = "<" + context + "> " + message;

      // USE SYSERR
      try {
        String msg = message;
        if (hasParams)
          msg = message.formatted(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17);

        System.err.println(msg);

      } catch (final Exception e) {
        System.err.print("Error on formatting message '%s'. Exception: %s".formatted(message, e));
      } finally {
        if (level == Level.SEVERE)
          System.err.flush();
      }
    } else if (log.isLoggable(level)) {
      // USE THE LOG
      try {
        if (context != null)
          message = "<" + context + "> " + message;

        String msg = message;
        if (hasParams)
          msg = message.formatted(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17);

        if (exception != null)
          log.log(level, msg, exception);
        else
          log.log(level, msg);

        if (level == Level.SEVERE)
          flush();

      } catch (final Exception e) {
        System.err.printf("Error on formatting message '%s'. Exception: %s", message, e);
        System.err.flush();
      }
    }
  }

  public void log(final Object requester, final Level level, String message, final Throwable exception, final String context,
      final Object... args) {
    if (message != null) {
      final String requesterName;
      if (requester instanceof String string)
        requesterName = string;
      else if (requester instanceof Class<?> class1)
        requesterName = class1.getName();
      else if (requester != null)
        requesterName = requester.getClass().getName();
      else
        requesterName = DEFAULT_LOG;

      java.util.logging.Logger log = loggersCache.get(requesterName);
      if (log == null) {
        log = java.util.logging.Logger.getLogger(requesterName);

        if (log != null) {
          final java.util.logging.Logger oldLogger = loggersCache.putIfAbsent(requesterName, log);

          if (oldLogger != null)
            log = oldLogger;
        }
      }

      if (log == null) {
        if (context != null)
          message = "<" + context + "> " + message;

        try {
          String msg = message;
          if (args.length > 0)
            msg = message.formatted(args);
          System.err.println(msg);

        } catch (final Exception e) {
          System.err.printf("Error on formatting message '%s'. Exception: %s", message, e);
        }
      } else {
        // USE THE LOG
        try {
          if (context != null)
            message = "<" + context + "> " + message;

          String msg = message;
          if (args.length > 0)
            msg = message.formatted(args);

          if (log.isLoggable(level)) {
            if (exception != null)
              log.log(level, msg, exception);
            else
              log.log(level, msg);
          } else if (com.arcadedb.log.LogManager.instance().isDebugEnabled()) {
            if (exception != null) {
              System.out.print(new LogFormatter().format(new LogRecord(level, msg)));
              exception.printStackTrace();
            } else
              System.out.println(msg);
          }
        } catch (final Exception e) {
          System.err.printf("Error on formatting message '%s'. Exception: %s", message, e);
        }
      }
    }
  }

  @Override
  public void flush() {
    for (final Handler h : java.util.logging.Logger.getLogger(java.util.logging.Logger.GLOBAL_LOGGER_NAME).getHandlers())
      h.flush();
  }
}
