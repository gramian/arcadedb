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
package com.arcadedb.mongo;

import com.arcadedb.query.sql.executor.Result;
import de.bwaldvogel.mongo.backend.Utils;
import de.bwaldvogel.mongo.bson.Document;
import de.bwaldvogel.mongo.bson.ObjectId;

import java.util.*;

public class MongoDBToSqlTranslator {

  protected static void buildExpression(final StringBuilder buffer, final Document query) {
    for (final Map.Entry<String, Object> entry : query.entrySet()) {
      final Object key = entry.getKey();
      final Object value = entry.getValue();

      if (key instanceof String string && string.startsWith("$"))
        buildExpression(buffer, string, value);
      else if (value instanceof Document) {
        buildAnd(buffer, key, value);
      } else if (value instanceof List list) {
        if (key.equals("$or")) {
          buildOr(buffer, list);
        } else
          throw new IllegalArgumentException("Invalid operator " + key);
      } else {
        buffer.append(entry.getKey());
        buffer.append(" = ");
        buildValue(buffer, value);
      }
    }
  }

  protected static void buildAnd(final StringBuilder sql, final Object key, final Object value) {
    int expressionCount = 0;

    sql.append("(");

    if (value instanceof List) {
      for (final Document o : (List<Document>) value) {
        if (expressionCount++ > 0)
          sql.append(" AND ");

        buildExpression(sql, o);
      }
    } else if (value instanceof Document document) {
      for (final Map.Entry<String, Object> subEntry : document.entrySet()) {
        final String subKey = subEntry.getKey();
        final Object subValue = subEntry.getValue();

        if (expressionCount++ > 0)
          sql.append(" AND ");

        if (key != null)
          sql.append(key);

        buildExpression(sql, subKey, subValue);

      }
    }

    sql.append(")");
  }

  protected static void buildExpression(final StringBuilder sql, final String key, final Object value) {
    if (key.equals("$in")) {
      if (value instanceof Collection collection) {
        sql.append(" IN ");
        buildCollection(sql, collection);
      } else
        throw new IllegalArgumentException("Operator $in was expecting a collection");
    } else if (key.equals("$nin")) {
      if (value instanceof Collection collection) {
        sql.append(" NOT IN ");
        buildCollection(sql, collection);
      } else
        throw new IllegalArgumentException("Operator $in was expecting a collection");
    } else if (key.equals("$eq")) {
      sql.append(" = ");
      buildValue(sql, value);
    } else if (key.equals("$ne")) {
      sql.append(" <> ");
      buildValue(sql, value);
    } else if (key.equals("$lt")) {
      sql.append(" < ");
      buildValue(sql, value);
    } else if (key.equals("$lte")) {
      sql.append(" <= ");
      buildValue(sql, value);
    } else if (key.equals("$gt")) {
      sql.append(" > ");
      buildValue(sql, value);
    } else if (key.equals("$gte")) {
      sql.append(" >= ");
      buildValue(sql, value);
    } else if (key.equals("$exists")) {
      sql.append(" IS DEFINED ");
    } else if (key.equals("$size")) {
      sql.append(".size() = ");
      buildValue(sql, value);
    } else if (key.equals("$or")) {
      buildOr(sql, (List) value);
    } else if (key.equals("$and")) {
      buildAnd(sql, key, value);
    } else if (key.equals("$not")) {
      sql.append(" NOT ");
      buildExpression(sql, (Document) value);
    } else
      throw new IllegalArgumentException("Unknown operator " + key);
  }

  protected static void buildOr(final StringBuilder buffer, final List list) {
    buffer.append("(");

    int i = 0;
    for (final Object o : list) {
      if (i++ > 0)
        buffer.append(" OR ");

      if (o instanceof Document document) {
        buildExpression(buffer, document);
      }
    }

    buffer.append(")");
  }

  protected static void buildCollection(final StringBuilder buffer, final Collection coll) {
    int i = 0;
    buffer.append('[');
    for (final Iterator it = coll.iterator(); it.hasNext(); ) {
      if (i++ > 0)
        buffer.append(',');

      buildValue(buffer, it.next());
    }
    buffer.append(']');
  }

  protected static void buildValue(final StringBuilder buffer, final Object value) {
    if (value instanceof String) {
      buffer.append('\'');
      buffer.append(value);
      buffer.append('\'');
    } else
      buffer.append(value);
  }

  protected static void fillResultSet(final int numberToSkip, final int numberToReturn, final List<Document> result, final Iterator it) {
    for (int i = 0; it.hasNext(); ++i) {
      if (numberToSkip > 0 && i < numberToSkip - 1)
        continue;

      final Object next = it.next();

      if (next instanceof com.arcadedb.database.Document document)
        result.add(convertDocumentToMongoDB(document));
      else if (next instanceof Result result1)
        result.add(convertDocumentToMongoDB(result1));
      else
        throw new IllegalArgumentException("Object not supported");

      if (numberToReturn > 0 && result.size() >= numberToReturn)
        break;
    }
  }

  protected static Document convertDocumentToMongoDB(final com.arcadedb.database.Document doc) {
    final Document result = new Document();
    for (final Map.Entry<String, Object> entry : doc.toMap().entrySet()) {
      final String p = entry.getKey();
      final Object value = entry.getValue();
      result.put(p, "_id".equals(p) ? getObjectId((String) value) : value);
    }
    return result;
  }

  protected static Document convertDocumentToMongoDB(final Result doc) {
    final Document result = new Document();
    for (final Map.Entry<String, Object> entry : doc.toMap().entrySet()) {
      final String p = entry.getKey();
      final Object value = entry.getValue();
      result.put(p, "_id".equals(p) ? getObjectId((String) value) : value);
    }
    return result;
  }

  protected static ObjectId getObjectId(final String s) {
    final byte[] buffer = new byte[s.length() / 2];
    for (int i = 0; i < s.length(); i += 2) {
      buffer[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
    }
    return new ObjectId(buffer);
  }

  protected static Document projectDocument(final Document document, final Document fields, final String idField) {
    if (document == null) {
      return null;
    } else {
      final Document newDocument = new Document();
      final Iterator var4;
      String key;
      if (onlyExclusions(fields)) {
        newDocument.putAll(document);
        var4 = fields.keySet().iterator();

        while (var4.hasNext()) {
          key = (String) var4.next();
          newDocument.remove(key);
        }
      } else {
        var4 = fields.keySet().iterator();

        while (var4.hasNext()) {
          key = (String) var4.next();
          if (Utils.isTrue(fields.get(key))) {
            projectField(document, newDocument, key);
          }
        }
      }

      if (!fields.containsKey(idField)) {
        newDocument.put(idField, document.get(idField));
      }

      return newDocument;
    }
  }

  protected static boolean onlyExclusions(final Document fields) {
    final Iterator var1 = fields.keySet().iterator();

    String key;
    do {
      if (!var1.hasNext()) {
        return true;
      }

      key = (String) var1.next();
    } while (!Utils.isTrue(fields.get(key)));

    return false;
  }

  protected static void projectField(final Document document, final Document newDocument, final String key) {
    if (document != null) {
      final int dotPos = key.indexOf(46);
      if (dotPos > 0) {
        final String mainKey = key.substring(0, dotPos);
        final String subKey = key.substring(dotPos + 1);
        final Object object = document.get(mainKey);
        if (object instanceof Document document1) {
          if (!newDocument.containsKey(mainKey)) {
            newDocument.put(mainKey, new Document());
          }

          projectField(document1, (Document) newDocument.get(mainKey), subKey);
        }
      } else {
        newDocument.put(key, document.get(key));
      }

    }
  }
}
