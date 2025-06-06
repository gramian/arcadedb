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
package com.arcadedb.query.sql.function.geo;

import com.arcadedb.TestHelper;
import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.index.Index;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Schema;
import com.arcadedb.schema.Type;
import org.junit.jupiter.api.Test;
import org.locationtech.spatial4j.io.GeohashUtils;
import org.locationtech.spatial4j.shape.Circle;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Luca Garulli (l.garulli@arcadedata.com)
 */
public class SQLGeoFunctionsTest {

  @Test
  public void testPoint() throws Exception {
    TestHelper.executeInNewDatabase("GeoDatabase", (db) -> {
      ResultSet result = db.query("sql", "select point(11,11) as point");
      assertThat(result.hasNext()).isTrue();
      Point point = result.next().getProperty("point");
      assertThat(point).isNotNull();
    });
  }

  @Test
  public void testRectangle() throws Exception {
    TestHelper.executeInNewDatabase("GeoDatabase", (db) -> {
      ResultSet result = db.query("sql", "select rectangle(10,10,20,20) as shape");
      assertThat(result.hasNext()).isTrue();
      Rectangle rectangle = result.next().getProperty("shape");
      assertThat(rectangle).isNotNull();
    });
  }

  @Test
  public void testCircle() throws Exception {
    TestHelper.executeInNewDatabase("GeoDatabase", (db) -> {
      ResultSet result = db.query("sql", "select circle(10,10,10) as circle");
      assertThat(result.hasNext()).isTrue();
      Circle circle = result.next().getProperty("circle");
      assertThat(circle).isNotNull();
    });
  }

  @Test
  public void testPolygon() throws Exception {
    TestHelper.executeInNewDatabase("GeoDatabase", (db) -> {
      ResultSet result = db.query("sql",
          "select polygon( [ point(10,10), point(20,10), point(20,20), point(10,20), point(10,10) ] ) as polygon");
      assertThat(result.hasNext()).isTrue();
      Shape polygon = result.next().getProperty("polygon");
      assertThat(polygon).isNotNull();

      result = db.query("sql", "select polygon( [ [10,10], [20,10], [20,20], [10,20], [10,10] ] ) as polygon");
      assertThat(result.hasNext()).isTrue();
      polygon = result.next().getProperty("polygon");
      assertThat(polygon).isNotNull();
    });
  }

  @Test
  public void testPointIsWithinRectangle() throws Exception {
    TestHelper.executeInNewDatabase("GeoDatabase", (db) -> {
      ResultSet result = db.query("sql", "select point(11,11).isWithin( rectangle(10,10,20,20) ) as isWithin");
      assertThat(result.hasNext()).isTrue();
      assertThat((Boolean) result.next().getProperty("isWithin")).isTrue();

      result = db.query("sql", "select point(11,21).isWithin( rectangle(10,10,20,20) ) as isWithin");
      assertThat(result.hasNext()).isTrue();
      assertThat((Boolean) result.next().getProperty("isWithin")).isFalse();
    });
  }

  @Test
  public void testPointIsWithinCircle() throws Exception {
    TestHelper.executeInNewDatabase("GeoDatabase", (db) -> {
      ResultSet result = db.query("sql", "select point(11,11).isWithin( circle(10,10,10) ) as isWithin");
      assertThat(result.hasNext()).isTrue();
      assertThat((Boolean) result.next().getProperty("isWithin")).isTrue();

      result = db.query("sql", "select point(10,21).isWithin( circle(10,10,10) ) as isWithin");
      assertThat(result.hasNext()).isTrue();
      assertThat((Boolean) result.next().getProperty("isWithin")).isFalse();
    });
  }

  @Test
  public void testPointIntersectWithRectangle() throws Exception {
    TestHelper.executeInNewDatabase("GeoDatabase", (db) -> {
      ResultSet result = db.query("sql", "select rectangle(9,9,11,11).intersectsWith( rectangle(10,10,20,20) ) as intersectsWith");
      assertThat(result.hasNext()).isTrue();
      assertThat((Boolean) result.next().getProperty("intersectsWith")).isTrue();

      result = db.query("sql", "select rectangle(9,9,9.9,9.9).intersectsWith( rectangle(10,10,20,20) ) as intersectsWith");
      assertThat(result.hasNext()).isTrue();
      assertThat((Boolean) result.next().getProperty("intersectsWith")).isFalse();
    });
  }

  @Test
  public void testPointIntersectWithPolygons() throws Exception {
    TestHelper.executeInNewDatabase("GeoDatabase", (db) -> {
      ResultSet result = db.query("sql",
          "select polygon( [ [10,10], [20,10], [20,20], [10,20], [10,10] ] ).intersectsWith( rectangle(10,10,20,20) ) as intersectsWith");
      assertThat(result.hasNext()).isTrue();
      assertThat((Boolean) result.next().getProperty("intersectsWith")).isTrue();

      result = db.query("sql",
          "select polygon( [ [10,10], [20,10], [20,20], [10,20], [10,10] ] ).intersectsWith( rectangle(21,21,22,22) ) as intersectsWith");
      assertThat(result.hasNext()).isTrue();
      assertThat((Boolean) result.next().getProperty("intersectsWith")).isFalse();
    });
  }

  @Test
  public void testLineStringsIntersect() throws Exception {
    TestHelper.executeInNewDatabase("GeoDatabase", (db) -> {
      ResultSet result = db.query("sql",
          "select linestring( [ [10,10], [20,10], [20,20], [10,20], [10,10] ] ).intersectsWith( rectangle(10,10,20,20) ) as intersectsWith");
      assertThat(result.hasNext()).isTrue();
      assertThat((Boolean) result.next().getProperty("intersectsWith")).isTrue();

      result = db.query("sql",
          "select linestring( [ [10,10], [20,10], [20,20], [10,20], [10,10] ] ).intersectsWith( rectangle(21,21,22,22) ) as intersectsWith");
      assertThat(result.hasNext()).isTrue();
      assertThat((Boolean) result.next().getProperty("intersectsWith")).isFalse();
    });
  }

  @Test
  public void testGeoManualIndexPoints() throws Exception {
    final int TOTAL = 1_000;

    TestHelper.executeInNewDatabase("GeoDatabase", (db) -> {

      db.transaction(() -> {
        final DocumentType type = db.getSchema().createDocumentType("Restaurant");
        type.createProperty("coords", Type.STRING).createIndex(Schema.INDEX_TYPE.LSM_TREE, false);

        long begin = System.currentTimeMillis();

        for (int i = 0; i < TOTAL; i++) {
          final MutableDocument doc = db.newDocument("Restaurant");
          doc.set("lat", 10 + (0.01D * i));
          doc.set("long", 10 + (0.01D * i));
          doc.set("coords", GeohashUtils.encodeLatLon(doc.getDouble("lat"), doc.getDouble("long"))); // INDEXED
          doc.save();
        }

        //System.out.println("Elapsed insert: " + (System.currentTimeMillis() - begin));

        final String[] area = new String[] { GeohashUtils.encodeLatLon(10.5, 10.5), GeohashUtils.encodeLatLon(10.55, 10.55) };

        begin = System.currentTimeMillis();
        ResultSet result = db.query("sql", "select from Restaurant where coords >= ? and coords <= ?", area[0], area[1]);

        //System.out.println("Elapsed query: " + (System.currentTimeMillis() - begin));

        begin = System.currentTimeMillis();

        assertThat(result.hasNext()).isTrue();
        int returned = 0;
        while (result.hasNext()) {
          final Document record = result.next().toElement();
          assertThat(record.getDouble("lat")).isGreaterThanOrEqualTo(10.5);
          assertThat(record.getDouble("long")).isLessThanOrEqualTo(10.55);
//          System.out.println(record.toJSON());

          ++returned;
        }

        //System.out.println("Elapsed browsing: " + (System.currentTimeMillis() - begin));

        assertThat(returned).isEqualTo(6);
      });
    });
  }

  @Test
  public void testGeoManualIndexBoundingBoxes() throws Exception {
    final int TOTAL = 1_000;

    TestHelper.executeInNewDatabase("GeoDatabase", (db) -> {

      db.transaction(() -> {
        final DocumentType type = db.getSchema().createDocumentType("Restaurant");
        type.createProperty("bboxTL", Type.STRING).createIndex(Schema.INDEX_TYPE.LSM_TREE, false);
        type.createProperty("bboxBR", Type.STRING).createIndex(Schema.INDEX_TYPE.LSM_TREE, false);

        long begin = System.currentTimeMillis();

        for (int i = 0; i < TOTAL; i++) {
          final MutableDocument doc = db.newDocument("Restaurant");
          doc.set("x1", 10D + (0.0001D * i));
          doc.set("y1", 10D + (0.0001D * i));
          doc.set("x2", 10D + (0.001D * i));
          doc.set("y2", 10D + (0.001D * i));
          doc.set("bboxTL", GeohashUtils.encodeLatLon(doc.getDouble("x1"), doc.getDouble("y1"))); // INDEXED
          doc.set("bboxBR", GeohashUtils.encodeLatLon(doc.getDouble("x2"), doc.getDouble("y2"))); // INDEXED
          doc.save();
        }

        for (Index idx : type.getAllIndexes(false)) {
          assertThat(idx.countEntries()).isEqualTo(TOTAL);
        }

        //System.out.println("Elapsed insert: " + (System.currentTimeMillis() - begin));

        final String[] area = new String[] {//
            GeohashUtils.encodeLatLon(10.0001D, 10.0001D),//
            GeohashUtils.encodeLatLon(10.020D, 10.020D) };

        begin = System.currentTimeMillis();
        //ResultSet result = db.query("sql", "select from Restaurant where bboxBR <= ?",area[1]);
        ResultSet result = db.query("sql", "select from Restaurant where bboxTL >= ? and bboxBR <= ?", area[0], area[1]);

        //System.out.println("Elapsed query: " + (System.currentTimeMillis() - begin));

        begin = System.currentTimeMillis();

        assertThat(result.hasNext()).isTrue();
        int returned = 0;
        while (result.hasNext()) {
          final Document record = result.next().toElement();
          assertThat(record.getDouble("x1")).isGreaterThanOrEqualTo(10.0001D).withFailMessage("x1: " + record.getDouble("x1"));
          assertThat(record.getDouble("y1")).isGreaterThanOrEqualTo(10.0001D).withFailMessage("y1: " + record.getDouble("y1"));
          assertThat(record.getDouble("x2")).isLessThanOrEqualTo(10.020D).withFailMessage("x2: " + record.getDouble("x2"));
          assertThat(record.getDouble("y2")).isLessThanOrEqualTo(10.020D).withFailMessage("y2: " + record.getDouble("y2"));
          //System.out.println(record.toJSON());

          ++returned;
        }

        //System.out.println("Elapsed browsing: " + (System.currentTimeMillis() - begin));

        assertThat(returned).isEqualTo(20);
      });
    });
  }
}
