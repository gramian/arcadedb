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
package com.arcadedb.index;

import com.arcadedb.TestHelper;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.exception.TransactionException;
import com.arcadedb.index.lsm.LSMTreeFullTextIndex;
import com.arcadedb.index.lsm.LSMTreeIndexAbstract;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Schema;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class LSMTreeFullTextIndexTest extends TestHelper {
  private static final int    TOT       = 10000;
  private static final String TYPE_NAME = "V";
  private static final int    PAGE_SIZE = LSMTreeIndexAbstract.DEF_PAGE_SIZE;

  @Test
  public void testIndexing() {
    assertThat(database.getSchema().existsType(TYPE_NAME)).isFalse();

    final DocumentType type = database.getSchema().buildDocumentType().withName(TYPE_NAME).withTotalBuckets(1).create();
    type.createProperty("text", String.class);
    final Index typeIndex = database.getSchema()
        .createTypeIndex(Schema.INDEX_TYPE.FULL_TEXT, false, TYPE_NAME, new String[] { "text" }, PAGE_SIZE);

    assertThat(database.getSchema().existsType(TYPE_NAME)).isTrue();

    final String text =
        """
        Jay Glenn Miner (May 31, 1932 – June 20, 1994) was an American integrated circuit designer, known primarily for developing multimedia chips for the Atari 2600 and Atari 8-bit family and as the "father of the Amiga". He received a BS in EECS from UC Berkeley in 1959.[2]

        Miner started in the electronics industry with a number of designs in the medical world, including a remote-control pacemaker.

        He moved to Atari, Inc. in the late 1970s. One of his first successes was to combine an entire breadboard of components into a single chip, known as the TIA. The TIA was the display hardware for the Atari 2600, which would go on to sell millions. After working on the TIA he headed up the design of the follow-on chip set known as ANTIC and CTIA for which he held a patent.[3] These chips would be used for the Atari 8-bit family of home computers and the Atari 5200 video game system.

        In the early 1980s, Jay, along with other Atari staffers, had become fed up with management and decamped. They set up another chipset project under a new company in Santa Clara, called Hi-Toro (later renamed to Amiga Corporation), where they could have creative freedom. There, they started to create a new Motorola 68000-based games console, codenamed Lorraine that could be upgraded to a computer. To raise money for the Lorraine project, Amiga Corp. designed and sold joysticks and game cartridges for popular game consoles such as the Atari 2600 and ColecoVision, as well as an odd input device called the Joyboard, essentially a joystick the player stood on. Atari continued to be interested in the team's efforts throughout this period, and funded them with $500,000 in capital in return for first use of their resulting chipset.

        Also in the early 1980s, Jay worked on a project with Intermedics, Inc. to create their first microprocessor-based cardiac pacemaker. The microprocessor was called Lazarus and the pacemaker was eventually called Cosmos. Jay was listed co-inventor on two patents. US patent 4390022, Richard V. Calfee & Jay Miner, "Implantable device with microprocessor control", issued 1983-06-28, assigned to Intermedics, Inc. US patent 4404972, Pat L. Gordon; Richard V. Calfee & Jay Miner, "Implantable device with microprocessor control", issued 1983-06-28, assigned to Intermedics, Inc.

        The Amiga crew, having continuing serious financial problems, had sought more monetary support from investors that entire Spring. Amiga entered into discussions with Commodore. The discussions ultimately led to Commodore wanting to purchase Amiga outright, which would (from Commodore's viewpoint) cancel any outstanding contracts - including Atari Inc.'s. So instead of Amiga delivering the chipset, Commodore delivered a check of $500,000 to Atari on Amiga's behalf, in effect returning the funds invested into Amiga for completion of the Lorraine chipset.


        The original Amiga (1985)
        Jay worked at Commodore-Amiga for several years, in Los Gatos, California. They made good progress at the beginning, but as Commodore management changed, they became marginalised and the original Amiga staff was fired or left out on a one-by-one basis, until the entire Los Gatos office was closed. Miner later worked as a consultant for Commodore until it went bankrupt. He was known as the 'Padre' (father) of the Amiga among Amiga users.

        Jay always took his dog "Mitchy" (a cockapoo) with him wherever he went. While he worked at Atari, Mitchy even had her own ID-badge, and an embossing of Mitchy's paw print is visible on the inside of the Amiga 1000 top cover, alongside the signatures of the engineers who worked on it.

        Jay endured kidney problems for most of his life, according to his wife, and relied on dialysis. His sister donated one of her own. Miner died due to complications from kidney failure at the age of 62, just two months after Commodore declared bankruptcy.""";

    database.transaction(() -> {
      for (int i = 0; i < TOT; ++i) {
        final MutableDocument v = database.newDocument(TYPE_NAME);
        v.set("id", i);
        v.set("text", text);
        v.save();
      }

      final List<String> keywords = ((LSMTreeFullTextIndex) ((TypeIndex) typeIndex).getIndexesOnBuckets()[0]).analyzeText(
          ((LSMTreeFullTextIndex) ((TypeIndex) typeIndex).getIndexesOnBuckets()[0]).getAnalyzer(), new Object[] { text });
      assertThat(keywords.isEmpty()).isFalse();

      for (final String k : keywords) {
        int totalPerKeyword = 0;

        for (final Index idx : ((TypeIndex) typeIndex).getIndexesOnBuckets()) {
          if (idx instanceof TypeIndex)
            continue;

          final IndexCursor result = idx.get(new Object[] { k });

          int totalPerIndex = 0;
          while (result.hasNext()) {
            result.next();
            ++totalPerIndex;
          }

          assertThat(totalPerIndex).isEqualTo(result.estimateSize());

          totalPerKeyword += totalPerIndex;
        }
        assertThat(totalPerKeyword).isEqualTo(TOT);
      }
    });

    reopenDatabase();

    assertThat(database.getSchema().getIndexes()[0].getType()).isEqualTo(Schema.INDEX_TYPE.FULL_TEXT);

    database.getSchema().dropIndex(typeIndex.getName());
  }

  @Test
  public void testIndexingComposite() {
    assertThat(database.getSchema().existsType(TYPE_NAME)).isFalse();

    final DocumentType type = database.getSchema().buildDocumentType().withName(TYPE_NAME).withTotalBuckets(1).create();
    type.createProperty("text", String.class);
    type.createProperty("type", String.class);
    try {
      database.getSchema()
          .createTypeIndex(Schema.INDEX_TYPE.FULL_TEXT, false, TYPE_NAME, new String[] { "text", "type" }, PAGE_SIZE);
      fail("");
    } catch (IndexException e) {
      // EXPECTED
    }
  }

  @Test
  public void testQuery() {
    database.transaction(() -> {
      assertThat(database.getSchema().existsType("Docs")).isFalse();

      final DocumentType type = database.getSchema().createDocumentType("Docs");
      type.createProperty("text", String.class);
      final Index typeIndex = database.getSchema()
          .createTypeIndex(Schema.INDEX_TYPE.FULL_TEXT, false, "Docs", new String[] { "text" });

      assertThat(database.getSchema().existsType("Docs")).isTrue();

      final String text =
          """
          Jay Glenn Miner (May 31, 1932 – June 20, 1994) was an American integrated circuit designer, known primarily for developing multimedia chips for the Atari 2600 and Atari 8-bit family and as the "father of the Amiga". He received a BS in EECS from UC Berkeley in 1959.[2]

          Miner started in the electronics industry with a number of designs in the medical world, including a remote-control pacemaker.

          He moved to Atari, Inc. in the late 1970s. One of his first successes was to combine an entire breadboard of components into a single chip, known as the TIA. The TIA was the display hardware for the Atari 2600, which would go on to sell millions. After working on the TIA he headed up the design of the follow-on chip set known as ANTIC and CTIA for which he held a patent.[3] These chips would be used for the Atari 8-bit family of home computers and the Atari 5200 video game system.

          In the early 1980s, Jay, along with other Atari staffers, had become fed up with management and decamped. They set up another chipset project under a new company in Santa Clara, called Hi-Toro (later renamed to Amiga Corporation), where they could have creative freedom. There, they started to create a new Motorola 68000-based games console, codenamed Lorraine that could be upgraded to a computer. To raise money for the Lorraine project, Amiga Corp. designed and sold joysticks and game cartridges for popular game consoles such as the Atari 2600 and ColecoVision, as well as an odd input device called the Joyboard, essentially a joystick the player stood on. Atari continued to be interested in the team's efforts throughout this period, and funded them with $500,000 in capital in return for first use of their resulting chipset.

          Also in the early 1980s, Jay worked on a project with Intermedics, Inc. to create their first microprocessor-based cardiac pacemaker. The microprocessor was called Lazarus and the pacemaker was eventually called Cosmos. Jay was listed co-inventor on two patents. US patent 4390022, Richard V. Calfee & Jay Miner, "Implantable device with microprocessor control", issued 1983-06-28, assigned to Intermedics, Inc. US patent 4404972, Pat L. Gordon; Richard V. Calfee & Jay Miner, "Implantable device with microprocessor control", issued 1983-06-28, assigned to Intermedics, Inc.

          The Amiga crew, having continuing serious financial problems, had sought more monetary support from investors that entire Spring. Amiga entered into discussions with Commodore. The discussions ultimately led to Commodore wanting to purchase Amiga outright, which would (from Commodore's viewpoint) cancel any outstanding contracts - including Atari Inc.'s. So instead of Amiga delivering the chipset, Commodore delivered a check of $500,000 to Atari on Amiga's behalf, in effect returning the funds invested into Amiga for completion of the Lorraine chipset.


          The original Amiga (1985)
          Jay worked at Commodore-Amiga for several years, in Los Gatos, California. They made good progress at the beginning, but as Commodore management changed, they became marginalised and the original Amiga staff was fired or left out on a one-by-one basis, until the entire Los Gatos office was closed. Miner later worked as a consultant for Commodore until it went bankrupt. He was known as the 'Padre' (father) of the Amiga among Amiga users.

          Jay always took his dog "Mitchy" (a cockapoo) with him wherever he went. While he worked at Atari, Mitchy even had her own ID-badge, and an embossing of Mitchy's paw print is visible on the inside of the Amiga 1000 top cover, alongside the signatures of the engineers who worked on it.

          Jay endured kidney problems for most of his life, according to his wife, and relied on dialysis. His sister donated one of her own. Miner died due to complications from kidney failure at the age of 62, just two months after Commodore declared bankruptcy.""";

      final String[] words = text.split(" ");

      for (int i = 0; i < words.length - 1; ++i) {

        final String toIndex = words[i].toLowerCase() + " has been indexed";

        if (skipIndexing(toIndex))
          continue;

        final MutableDocument doc = database.newDocument("Docs");
        doc.set("id", i);
        doc.set("text", toIndex);
        doc.save();
      }

      for (int i = 0; i < words.length - 1; ++i) {
        final String toFind = words[i].toLowerCase();

        if (skipIndexing(toFind))
          continue;

        final ResultSet result = database.query("sql", "select from Docs where text = '" + toFind + "'", toFind);
        assertThat(result.hasNext())
            .isTrue()
            .withFailMessage("Cannot find key '" + toFind + "'");

        final Result res = result.next();
        assertThat(res).isNotNull();

        final String content = res.getProperty("text").toString().toLowerCase();
        assertThat(content.contains(toFind))
            .isTrue()
            .withFailMessage("Cannot find the word '" + toFind + "' in indexed text '" + content + "'");
      }
    });
  }

  @Test
  public void testNullValuesViaSQL() {
    try {
      database.transaction(() -> {
        database.command("sql", "CREATE DOCUMENT TYPE doc");
        database.command("sql", "CREATE PROPERTY doc.str STRING");
        database.command("sql", "CREATE INDEX ON doc (str) FULL_TEXT null_strategy error");
        database.command("sql", "INSERT INTO doc (str) VALUES ('a'), ('b'), (null)");
      });
      fail("");
    } catch (TransactionException e) {
    }

    database.transaction(() -> {
      database.command("sql", "CREATE DOCUMENT TYPE doc2");
      database.command("sql", "CREATE PROPERTY doc2.str STRING");
      database.command("sql", "CREATE INDEX ON doc2 (str) FULL_TEXT null_strategy skip");
      database.command("sql", "INSERT INTO doc2 (str) VALUES ('a'), ('b'), (null)");
    });
  }

  private boolean skipIndexing(final String toIndex) {
    boolean skip = false;
    for (int j = 0; j < toIndex.length(); j++) {
      final char c = toIndex.charAt(j);
      if (!Character.isLetterOrDigit(c) && c != ' ')
        skip = true;
    }

    if (skip)
      return true;
    return false;
  }
}
