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
package com.arcadedb.integration.backup.format;

import com.arcadedb.database.DatabaseInternal;
import com.arcadedb.database.LocalDatabase;
import com.arcadedb.engine.ComponentFile;
import com.arcadedb.integration.backup.BackupException;
import com.arcadedb.integration.backup.BackupSettings;
import com.arcadedb.integration.importer.ConsoleLogger;
import com.arcadedb.schema.LocalSchema;
import com.arcadedb.utility.FileUtils;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.charset.*;
import java.security.spec.*;
import java.util.*;
import java.util.zip.*;

public class FullBackupFormat extends AbstractBackupFormat {
  private interface BackupCallback {
    void backup(ZipOutputStream zipFile) throws Exception;
  }

  public FullBackupFormat(final DatabaseInternal database, final BackupSettings settings, final ConsoleLogger logger) {
    super(database, settings, logger);
  }

  @Override
  public void backupDatabase() throws Exception {
    settings.validateSettings();

    String fileName;
    if (settings.file.startsWith("file://"))
      fileName = settings.file.substring("file://".length());
    else
      fileName = settings.file;

    if (settings.directory != null)
      fileName = settings.directory + File.separator + fileName;

    final File backupFile = new File(fileName);

    if (backupFile.exists() && !settings.overwriteFile)
      throw new BackupException("The backup file '%s' already exist and '-o' setting is false".formatted(settings.file));

    if (backupFile.getParentFile() != null && !backupFile.getParentFile().exists()) {
      if (!backupFile.getParentFile().mkdirs())
        throw new BackupException("The backup file '%s' cannot be created".formatted(backupFile));
    }

    if (database.isTransactionActive() && database.getTransaction().hasChanges())
      throw new BackupException("Transaction in progress found");

    logger.logLine(0, "Executing full backup of database to '%s'...", backupFile);

    encryptFile(backupFile, zipFile -> {
      // ACQUIRE A READ LOCK. TRANSACTION CAN STILL RUN, BUT CREATION OF NEW FILES (BUCKETS, TYPES, INDEXES) WILL BE PUT ON PAUSE UNTIL THIS LOCK IS RELEASED
      database.executeInReadLock(() -> {
        // FORCE FLUSHING BEFORE THE BACKUP AND AVOID FLUSHING OF DATA PAGES TO DISK
        database.getPageManager().suspendFlushAndExecute(database, () -> {

          final long beginTime = System.currentTimeMillis();

          long databaseOrigSize = 0L;
          databaseOrigSize += compressFile(zipFile, ((LocalDatabase) database.getEmbedded()).getConfigurationFile());
          databaseOrigSize += compressFile(zipFile, ((LocalSchema) database.getSchema()).getConfigurationFile());

          final Collection<ComponentFile> files = database.getFileManager().getFiles();

          for (final ComponentFile file : files)
            if (file != null)
              databaseOrigSize += compressFile(zipFile, file.getOSFile());

          zipFile.close();

          final long elapsedInSecs = (System.currentTimeMillis() - beginTime) / 1000;

          final long databaseCompressedSize = backupFile.length();

          logger.logLine(0, "Full backup completed in %d seconds %s -> %s (%,d%% compressed)", elapsedInSecs,
              FileUtils.getSizeAsString(databaseOrigSize), FileUtils.getSizeAsString((databaseCompressedSize)),
              databaseOrigSize > 0 ? (databaseOrigSize - databaseCompressedSize) * 100 / databaseOrigSize : 0);
        });
        return null;
      });
    });
  }

  private long compressFile(final ZipOutputStream zipFile, final File inputFile) throws IOException {
    logger.log(2, "- File '%s'...", inputFile.getName());
    if (inputFile.exists()) {
      final long origSize = inputFile.length();

      final ZipEntry zipEntry = new ZipEntry(inputFile.getName());
      zipFile.putNextEntry(zipEntry);

      try (final FileInputStream fileIn = new FileInputStream(inputFile)) {
        fileIn.transferTo(zipFile);
      }
      zipFile.closeEntry();

      final long compressedSize = zipEntry.getCompressedSize();

      logger.logLine(2, " %s -> %s (%,d%% compressed)", FileUtils.getSizeAsString(origSize),
          FileUtils.getSizeAsString(compressedSize), origSize > 0 ? (origSize - compressedSize) * 100 / origSize : 0);
      return origSize;
    }

    logger.logLine(2, " not found");
    return 0;
  }

  private void encryptFile(final File backupFile, final BackupCallback callback) throws Exception {
    try (final FileOutputStream fos = new FileOutputStream(backupFile)) {
      final ZipOutputStream zipFile;
      if (settings.encryptionKey != null) {
        // Generate a random salt (e.g., 16 bytes)
        final byte[] salt = new byte[16];

        new java.security.SecureRandom().nextBytes(salt);
        // Store this salt at the beginning of the backup file, similar to the IV.

        fos.write(salt);

        final SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        // Iteration count should be high, e.g., 65536 or more
        final KeySpec spec = new PBEKeySpec(settings.encryptionKey.toCharArray(), salt, 65536, 256); // 256-bit key
        final SecretKey tmp = factory.generateSecret(spec);
        final byte[] derivedKeyBytes = tmp.getEncoded();
        final javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(derivedKeyBytes, settings.encryptionAlgorithm);

        // Initialize cipher
        final javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(settings.encryptionAlgorithm + "/CTR/NoPadding");
        final byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);
        final javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(iv);
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, ivSpec);

        // Write IV at the beginning of the file
        fos.write(iv);

        // Wrap the output stream with CipherOutputStream
        final javax.crypto.CipherOutputStream cos = new javax.crypto.CipherOutputStream(fos, cipher);
        zipFile = new ZipOutputStream(cos, StandardCharsets.UTF_8);
      } else
        zipFile = new ZipOutputStream(fos, StandardCharsets.UTF_8);

      zipFile.setLevel(9);
      try {
        callback.backup(zipFile);
      } finally {
        zipFile.close();
      }
    }
  }
}
