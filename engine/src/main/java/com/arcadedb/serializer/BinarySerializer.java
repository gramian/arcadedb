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
package com.arcadedb.serializer;

import com.arcadedb.ContextConfiguration;
import com.arcadedb.GlobalConfiguration;
import com.arcadedb.database.BaseRecord;
import com.arcadedb.database.Binary;
import com.arcadedb.database.DataEncryption;
import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseContext;
import com.arcadedb.database.DatabaseInternal;
import com.arcadedb.database.Document;
import com.arcadedb.database.EmbeddedDocument;
import com.arcadedb.database.EmbeddedModifier;
import com.arcadedb.database.EmbeddedModifierProperty;
import com.arcadedb.database.Identifiable;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.database.RID;
import com.arcadedb.database.Record;
import com.arcadedb.engine.Dictionary;
import com.arcadedb.exception.SerializationException;
import com.arcadedb.graph.Edge;
import com.arcadedb.graph.EdgeSegment;
import com.arcadedb.graph.MutableEdge;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.graph.Vertex;
import com.arcadedb.graph.VertexInternal;
import com.arcadedb.log.LogManager;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Property;
import com.arcadedb.serializer.json.JSONObject;
import com.arcadedb.utility.DateUtils;

import java.lang.reflect.*;
import java.math.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import java.util.logging.*;

/**
 * Default serializer implementation.
 * <p>
 * TODO: check on storing all the property ids at the beginning of the buffer, so to partial deserialize values is much more
 * <p>
 * TODO: efficient, because it doesn't need to unmarshall all the values first.
 */
public class BinarySerializer {
  private final BinaryComparator comparator = new BinaryComparator();
  private       Class<?>         dateImplementation;
  private       Class<?>         dateTimeImplementation;
  private       DataEncryption   dataEncryption;

  public BinarySerializer(final ContextConfiguration configuration) throws ClassNotFoundException {
    setDateImplementation(configuration.getValue(GlobalConfiguration.DATE_IMPLEMENTATION));
    setDateTimeImplementation(configuration.getValue(GlobalConfiguration.DATE_TIME_IMPLEMENTATION));
  }

  public Binary serialize(final DatabaseInternal database, final Record record) {
    return switch (record.getRecordType()) {
      case Document.RECORD_TYPE, EmbeddedDocument.RECORD_TYPE -> serializeDocument(database, (MutableDocument) record);
      case Vertex.RECORD_TYPE -> serializeVertex(database, (MutableVertex) record);
      case Edge.RECORD_TYPE -> serializeEdge(database, (MutableEdge) record);
      case EdgeSegment.RECORD_TYPE -> serializeEdgeContainer((EdgeSegment) record);
      default -> throw new IllegalArgumentException("Cannot serialize a record of type=" + record.getRecordType());
    };
  }

  public Binary serializeDocument(final DatabaseInternal database, final Document document) {
    Binary header = ((BaseRecord) document).getBuffer();

    final DatabaseContext.DatabaseContextTL context = database.getContext();

    final boolean serializeProperties;
    if (header == null || (document instanceof MutableDocument mutableDocument && mutableDocument.isDirty())) {
      header = context.getTemporaryBuffer1();
      header.putByte(document.getRecordType()); // RECORD TYPE
      serializeProperties = true;
    } else {
      // COPY THE CONTENT (THE BUFFER IS IMMUTABLE)
      header = header.copyOfContent();
      header.position(Binary.BYTE_SERIALIZED_SIZE);
      serializeProperties = false;
    }

    if (serializeProperties)
      return serializeProperties(database, document, header, context.getTemporaryBuffer2());

    return header;
  }

  public Binary serializeVertex(final DatabaseInternal database, final VertexInternal vertex) {
    Binary header = ((BaseRecord) vertex).getBuffer();

    final DatabaseContext.DatabaseContextTL context = database.getContext();

    final boolean serializeProperties;
    if (header == null || (vertex instanceof MutableVertex mutableVertex && mutableVertex.isDirty())) {
      header = context.getTemporaryBuffer1();
      header.putByte(vertex.getRecordType()); // RECORD TYPE
      serializeProperties = true;
    } else {
      // COPY THE CONTENT (THE BUFFER IS IMMUTABLE)
      header = header.copyOfContent();
      header.position(Binary.BYTE_SERIALIZED_SIZE);
      serializeProperties = false;
    }

    // WRITE OUT AND IN EDGES POINTER FIRST, THEN SERIALIZE THE VERTEX PROPERTIES (AS A DOCUMENT)
    final RID outEdges = vertex.getOutEdgesHeadChunk();
    if (outEdges != null) {
      header.putInt(outEdges.getBucketId());
      header.putLong(outEdges.getPosition());
    } else {
      header.putInt(-1);
      header.putLong(-1);
    }

    final RID inEdges = vertex.getInEdgesHeadChunk();
    if (inEdges != null) {
      header.putInt(inEdges.getBucketId());
      header.putLong(inEdges.getPosition());
    } else {
      header.putInt(-1);
      header.putLong(-1);
    }

    if (serializeProperties)
      return serializeProperties(database, vertex, header, context.getTemporaryBuffer2());

    return header;
  }

  public Binary serializeEdge(final DatabaseInternal database, final Edge edge) {
    Binary header = ((BaseRecord) edge).getBuffer();

    final DatabaseContext.DatabaseContextTL context = database.getContext();

    final boolean serializeProperties;
    if (header == null || (edge instanceof MutableEdge mutableEdge && mutableEdge.isDirty())) {
      header = context.getTemporaryBuffer1();
      header.putByte(edge.getRecordType()); // RECORD TYPE
      serializeProperties = true;
    } else {
      // COPY THE CONTENT (THE BUFFER IS IMMUTABLE)
      header = header.copyOfContent();
      header.position(Binary.BYTE_SERIALIZED_SIZE);
      serializeProperties = false;
    }

    // WRITE OUT AND IN EDGES POINTER FIRST, THEN SERIALIZE THE VERTEX PROPERTIES (AS A DOCUMENT)
    serializeValue(database, header, BinaryTypes.TYPE_COMPRESSED_RID, edge.getOut());
    serializeValue(database, header, BinaryTypes.TYPE_COMPRESSED_RID, edge.getIn());

    if (serializeProperties)
      return serializeProperties(database, edge, header, context.getTemporaryBuffer2());

    return header;
  }

  public Binary serializeEdgeContainer(final EdgeSegment record) {
    return record.getContent();
  }

  public Set<String> getPropertyNames(final Database database, final Binary buffer, final RID rid) {
    try {
      buffer.getInt(); // HEADER-SIZE
      final int properties = (int) buffer.getUnsignedNumber();
      final Set<String> result = new LinkedHashSet<>(properties);

      for (int i = 0; i < properties; ++i) {
        final int nameId = (int) buffer.getUnsignedNumber();
        buffer.getUnsignedNumber(); //contentPosition
        final String name = database.getSchema().getDictionary().getNameById(nameId);
        result.add(name);
      }

      return result;
    } catch (Exception e) {
      LogManager.instance().log(this, Level.SEVERE, "Possible corrupted record %s", e, rid);
    }
    return null;
  }

  public Map<String, Object> deserializeProperties(final Database database, final Binary buffer,
      final EmbeddedModifier embeddedModifier, final RID rid, final String... fieldNames) {
    try {
      final int headerEndOffset = buffer.getInt();
      final int properties = (int) buffer.getUnsignedNumber();

      if (properties < 0)
        throw new SerializationException("Error on deserialize record. It may be corrupted (properties=" + properties + ")");
      else if (properties == 0)
        // EMPTY: NOT FOUND
        return new LinkedHashMap<>();

      final Map<String, Object> values = new LinkedHashMap<>(properties);

      int lastHeaderPosition;

      final int[] fieldIds = new int[fieldNames.length];

      final Dictionary dictionary = database.getSchema().getDictionary();
      for (int i = 0; i < fieldNames.length; ++i)
        fieldIds[i] = dictionary.getIdByName(fieldNames[i], false);

      for (int i = 0; i < properties; ++i) {
        final int nameId = (int) buffer.getUnsignedNumber();
        final int contentPosition = (int) buffer.getUnsignedNumber();

        lastHeaderPosition = buffer.position();

        if (fieldIds.length > 0) {
          boolean found = false;
          // FILTER BY FIELD
          for (final int f : fieldIds)
            if (f == nameId) {
              found = true;
              break;
            }

          if (!found)
            continue;
        }

        final String propertyName = dictionary.getNameById(nameId);

        buffer.position(headerEndOffset + contentPosition);

        final byte type = buffer.getByte();

        final EmbeddedModifierProperty propertyModifier =
            embeddedModifier != null ? new EmbeddedModifierProperty(embeddedModifier.getOwner(), propertyName) : null;

        final Object propertyValue = deserializeValue(database, buffer, type, propertyModifier);

        values.put(propertyName, propertyValue);

        buffer.position(lastHeaderPosition);

        if (fieldIds.length > 0 && values.size() >= fieldIds.length)
          // ALL REQUESTED PROPERTIES ALREADY FOUND
          break;
      }

      return values;
    } catch (Exception e) {
      LogManager.instance().log(this, Level.SEVERE, "Possible corrupted record %s", e, rid);
    }
    return new LinkedHashMap<>();
  }

  public boolean hasProperty(final Database database, final Binary buffer, final String fieldName, final RID rid) {
    try {
      buffer.getInt(); // headerEndOffset
      final int properties = (int) buffer.getUnsignedNumber();
      if (properties < 0)
        throw new SerializationException("Error on deserialize record. It may be corrupted (properties=" + properties + ")");
      else if (properties == 0)
        // EMPTY: NOT FOUND
        return false;

      final int fieldId = database.getSchema().getDictionary().getIdByName(fieldName, false);

      for (int i = 0; i < properties; ++i) {
        if (fieldId == (int) buffer.getUnsignedNumber())
          return true;
        buffer.getUnsignedNumber(); // contentPosition
      }
    } catch (Exception e) {
      LogManager.instance().log(this, Level.SEVERE, "Possible corrupted record %s", e, rid);
    }

    return false;
  }

  public Object deserializeProperty(final Database database, final Binary buffer, final EmbeddedModifier embeddedModifier,
      final String fieldName, final RID rid) {
    try {
      final int headerEndOffset = buffer.getInt();
      final int properties = (int) buffer.getUnsignedNumber();

      if (properties < 0)
        throw new SerializationException("Error on deserialize record. It may be corrupted (properties=" + properties + ")");
      else if (properties == 0)
        // EMPTY: NOT FOUND
        return null;

      final Dictionary dictionary = database.getSchema().getDictionary();
      final int fieldId = dictionary.getIdByName(fieldName, false);

      for (int i = 0; i < properties; ++i) {
        final int nameId = (int) buffer.getUnsignedNumber();
        final int contentPosition = (int) buffer.getUnsignedNumber();

        if (fieldId != nameId)
          continue;

        buffer.position(headerEndOffset + contentPosition);

        final byte type = buffer.getByte();

        final EmbeddedModifierProperty propertyModifier =
            embeddedModifier != null ? new EmbeddedModifierProperty(embeddedModifier.getOwner(), fieldName) : null;

        return deserializeValue(database, buffer, type, propertyModifier);
      }
    } catch (Exception e) {
      LogManager.instance().log(this, Level.SEVERE, "Possible corrupted record %s", e, rid);
    }
    return null;
  }

  public void serializeValue(final Database database, final Binary serialized, final byte type, Object value) {
    if (value == null)
      return;
    Binary content = dataEncryption != null ? new Binary() : serialized;

    switch (type) {
    case BinaryTypes.TYPE_NULL:
      break;
    case BinaryTypes.TYPE_COMPRESSED_STRING:
      content.putUnsignedNumber((Integer) value);
      break;
    case BinaryTypes.TYPE_BINARY:
      if (value instanceof byte[] bytes)
        content.putBytes(bytes);
      else if (value instanceof Binary binary)
        content.putBytes(binary.getContent());
      break;
    case BinaryTypes.TYPE_STRING:
      if (value instanceof byte[] bytes)
        content.putBytes(bytes);
      else
        content.putString(value.toString());
      break;
    case BinaryTypes.TYPE_BYTE:
      content.putByte((Byte) value);
      break;
    case BinaryTypes.TYPE_BOOLEAN:
      content.putByte((byte) ((Boolean) value ? 1 : 0));
      break;
    case BinaryTypes.TYPE_SHORT:
      content.putNumber(((Number) value).shortValue());
      break;
    case BinaryTypes.TYPE_INT:
      content.putNumber(((Number) value).intValue());
      break;
    case BinaryTypes.TYPE_LONG:
      content.putNumber(((Number) value).longValue());
      break;
    case BinaryTypes.TYPE_FLOAT:
      content.putNumber(Float.floatToIntBits(((Number) value).floatValue()));
      break;
    case BinaryTypes.TYPE_DOUBLE:
      content.putNumber(Double.doubleToLongBits(((Number) value).doubleValue()));
      break;
    case BinaryTypes.TYPE_DATE:
      if (value instanceof Date date)
        content.putUnsignedNumber(date.getTime() / DateUtils.MS_IN_A_DAY);
      else if (value instanceof LocalDate date)
        content.putUnsignedNumber(date.toEpochDay());
      break;
    case BinaryTypes.TYPE_DATETIME_SECOND:
    case BinaryTypes.TYPE_DATETIME:
    case BinaryTypes.TYPE_DATETIME_MICROS:
    case BinaryTypes.TYPE_DATETIME_NANOS:
      serializeDateTime(content, value, type);
      break;
    case BinaryTypes.TYPE_DECIMAL:
      content.putNumber(((BigDecimal) value).scale());
      content.putBytes(((BigDecimal) value).unscaledValue().toByteArray());
      break;
    case BinaryTypes.TYPE_COMPRESSED_RID: {
      final RID rid = ((Identifiable) value).getIdentity();
      serialized.putNumber(rid.getBucketId());
      serialized.putNumber(rid.getPosition());
      break;
    }
    case BinaryTypes.TYPE_RID: {
      if (value instanceof Result result)
        // COMING FROM A QUERY
        value = result.getElement().get();

      final RID rid = ((Identifiable) value).getIdentity();
      serialized.putInt(rid.getBucketId());
      serialized.putLong(rid.getPosition());
      break;
    }
    case BinaryTypes.TYPE_UUID: {
      final UUID uuid = (UUID) value;
      content.putNumber(uuid.getMostSignificantBits());
      content.putNumber(uuid.getLeastSignificantBits());
      break;
    }
    case BinaryTypes.TYPE_LIST: {
      switch (value) {
      case Collection collection -> {
        final Collection<Object> list = (Collection<Object>) value;
        content.putUnsignedNumber(list.size());
        for (final Iterator<Object> it = list.iterator(); it.hasNext(); ) {
          final Object entryValue = it.next();
          final byte entryType = BinaryTypes.getTypeFromValue(entryValue, null);
          content.putByte(entryType);
          serializeValue(database, content, entryType, entryValue);
        }
      }
      case Object[] array -> {
        content.putUnsignedNumber(array.length);
        for (final Object entryValue : array) {
          final byte entryType = BinaryTypes.getTypeFromValue(entryValue, null);
          content.putByte(entryType);
          serializeValue(database, content, entryType, entryValue);
        }
      }
      case Iterable iter -> {
        final List list = new ArrayList();
        for (final Iterator it = iter.iterator(); it.hasNext(); )
          list.add(it.next());

        content.putUnsignedNumber(list.size());
        for (final Iterator it = list.iterator(); it.hasNext(); ) {
          final Object entryValue = it.next();
          final byte entryType = BinaryTypes.getTypeFromValue(entryValue, null);
          content.putByte(entryType);
          serializeValue(database, content, entryType, entryValue);
        }
      }
      default -> {
        // ARRAY
        final int length = Array.getLength(value);
        content.putUnsignedNumber(length);
        for (int i = 0; i < length; ++i) {
          final Object entryValue = Array.get(value, i);
          try {
            final byte entryType = BinaryTypes.getTypeFromValue(entryValue, null);
            content.putByte(entryType);
            serializeValue(database, content, entryType, entryValue);
          } catch (Exception e) {
            LogManager.instance().log(this, Level.SEVERE, "Error on serializing array value for element %d = '%s'",
                i, entryValue);
            throw new SerializationException(
                "Error on serializing array value for element " + i + " = '" + entryValue + "'");
          }
        }
      }
      }
      break;
    }
    case BinaryTypes.TYPE_MAP: {
      final Dictionary dictionary = database.getSchema().getDictionary();

      if (value instanceof JSONObject object)
        value = object.toMap();

      final Map<Object, Object> map = (Map<Object, Object>) value;
      content.putUnsignedNumber(map.size());
      for (final Map.Entry<Object, Object> entry : map.entrySet()) {
        try {
          // WRITE THE KEY
          Object entryKey = entry.getKey();
          byte entryKeyType = BinaryTypes.getTypeFromValue(entryKey, null);

          if (entryKey != null && entryKeyType == BinaryTypes.TYPE_STRING) {
            final int id = dictionary.getIdByName((String) entryKey, false);
            if (id > -1) {
              // WRITE THE COMPRESSED STRING AS MAP KEY
              entryKeyType = BinaryTypes.TYPE_COMPRESSED_STRING;
              entryKey = id;
            }
          }

          content.putByte(entryKeyType);
          serializeValue(database, content, entryKeyType, entryKey);

          // WRITE THE VALUE
          final Object entryValue = entry.getValue();
          final byte entryValueType = BinaryTypes.getTypeFromValue(entryValue, null);
          content.putByte(entryValueType);
          serializeValue(database, content, entryValueType, entryValue);
        } catch (Exception e) {
          LogManager.instance().log(this, Level.SEVERE, "Error on serializing map value for key '%s' = '%s'",
              entry.getKey(), entry.getValue());
          throw new SerializationException(
              "Error on serializing map value for key '" + entry.getKey() + "' = '" + entry.getValue() + "'");
        }
      }
      break;
    }
    case BinaryTypes.TYPE_EMBEDDED: {
      final Document document = (Document) value;
      final long schemaId = database.getSchema().getDictionary().getIdByName(document.getTypeName(), false);
      if (schemaId == -1)
        throw new IllegalArgumentException("Cannot find type '" + document.getTypeName() + "' declared in embedded document");
      content.putUnsignedNumber(schemaId);

      final Binary header = new Binary(8192);
      header.setAllocationChunkSize(2048);
      final Binary body = new Binary(8192);
      body.setAllocationChunkSize(2048);

      header.putByte(EmbeddedDocument.RECORD_TYPE);
      serializeProperties(database, document, header, body);

      content.putUnsignedNumber(header.size());
      content.append(header);
      break;
    }
    case BinaryTypes.TYPE_ARRAY_OF_SHORTS: {
      final int length = Array.getLength(value);
      content.putUnsignedNumber(length);
      for (int i = 0; i < length; ++i)
        content.putNumber(Array.getShort(value, i));
      break;
    }
    case BinaryTypes.TYPE_ARRAY_OF_INTEGERS: {
      final int length = Array.getLength(value);
      content.putUnsignedNumber(length);
      for (int i = 0; i < length; ++i)
        content.putNumber(Array.getInt(value, i));
      break;
    }
    case BinaryTypes.TYPE_ARRAY_OF_LONGS: {
      final int length = Array.getLength(value);
      content.putUnsignedNumber(length);
      for (int i = 0; i < length; ++i)
        content.putNumber(Array.getLong(value, i));
      break;
    }
    case BinaryTypes.TYPE_ARRAY_OF_FLOATS: {
      final int length = Array.getLength(value);
      content.putUnsignedNumber(length);
      for (int i = 0; i < length; ++i)
        content.putNumber(Float.floatToIntBits(Array.getFloat(value, i)));
      break;
    }
    case BinaryTypes.TYPE_ARRAY_OF_DOUBLES: {
      final int length = Array.getLength(value);
      content.putUnsignedNumber(length);
      for (int i = 0; i < length; ++i)
        content.putNumber(Double.doubleToLongBits(Array.getDouble(value, i)));
      break;
    }
    default:
      LogManager.instance().log(this, Level.INFO, "Error on serializing value '" + value + "', type not supported");
    }

    if (dataEncryption != null) {
      switch (type) {
      case BinaryTypes.TYPE_NULL:
      case BinaryTypes.TYPE_COMPRESSED_RID:
      case BinaryTypes.TYPE_RID:
        break;
      default:
        serialized.putBytes(dataEncryption.encrypt(content.toByteArray()));
      }
    }
  }

  public Object deserializeValue(final Database database, final Binary deserialized, final byte type,
      final EmbeddedModifier embeddedModifier) {
    final Binary content = dataEncryption != null &&
        type != BinaryTypes.TYPE_NULL &&
        type != BinaryTypes.TYPE_COMPRESSED_RID &&
        type != BinaryTypes.TYPE_RID ? new Binary(dataEncryption.decrypt(deserialized.getBytes())) : deserialized;

    final Object value;
    switch (type) {
    case BinaryTypes.TYPE_NULL:
      value = null;
      break;
    case BinaryTypes.TYPE_STRING:
      value = content.getString();
      break;
    case BinaryTypes.TYPE_COMPRESSED_STRING:
      value = database.getSchema().getDictionary().getNameById((int) content.getUnsignedNumber());
      break;
    case BinaryTypes.TYPE_BINARY:
      value = content.getBytes();
      break;
    case BinaryTypes.TYPE_BYTE:
      value = content.getByte();
      break;
    case BinaryTypes.TYPE_BOOLEAN:
      value = content.getByte() == 1;
      break;
    case BinaryTypes.TYPE_SHORT:
      value = (short) content.getNumber();
      break;
    case BinaryTypes.TYPE_INT:
      value = (int) content.getNumber();
      break;
    case BinaryTypes.TYPE_LONG:
      value = content.getNumber();
      break;
    case BinaryTypes.TYPE_FLOAT:
      value = Float.intBitsToFloat((int) content.getNumber());
      break;
    case BinaryTypes.TYPE_DOUBLE:
      value = Double.longBitsToDouble(content.getNumber());
      break;
    case BinaryTypes.TYPE_DATE:
      value = DateUtils.date(database, content.getUnsignedNumber(), dateImplementation);
      break;
    case BinaryTypes.TYPE_DATETIME_SECOND:
      value = DateUtils.dateTime(database, content.getUnsignedNumber(), ChronoUnit.SECONDS, dateTimeImplementation,
          ChronoUnit.SECONDS);
      break;
    case BinaryTypes.TYPE_DATETIME:
      value = DateUtils.dateTime(database, content.getUnsignedNumber(), ChronoUnit.MILLIS, dateTimeImplementation,
          ChronoUnit.MILLIS);
      break;
    case BinaryTypes.TYPE_DATETIME_MICROS:
      value = DateUtils.dateTime(database, content.getUnsignedNumber(), ChronoUnit.MICROS, dateTimeImplementation,
          ChronoUnit.MICROS);
      break;
    case BinaryTypes.TYPE_DATETIME_NANOS:
      value = DateUtils.dateTime(database, content.getUnsignedNumber(), ChronoUnit.NANOS, dateTimeImplementation, ChronoUnit.NANOS);
      break;
    case BinaryTypes.TYPE_DECIMAL:
      final int scale = (int) content.getNumber();
      final byte[] unscaledValue = content.getBytes();
      value = new BigDecimal(new BigInteger(unscaledValue), scale);
      break;
    case BinaryTypes.TYPE_COMPRESSED_RID:
      value = new RID(database, (int) deserialized.getNumber(), deserialized.getNumber());
      break;
    case BinaryTypes.TYPE_RID:
      value = new RID(database, deserialized.getInt(), deserialized.getLong());
      break;
    case BinaryTypes.TYPE_UUID:
      value = new UUID(content.getNumber(), content.getNumber());
      break;
    case BinaryTypes.TYPE_LIST: {
      final int count = (int) content.getUnsignedNumber();
      final List<Object> list = new ArrayList<>(count);
      for (int i = 0; i < count; ++i) {
        final byte entryType = content.getByte();
        list.add(deserializeValue(database, content, entryType, embeddedModifier));
      }
      value = list;
      break;
    }
    case BinaryTypes.TYPE_MAP: {
      final int count = (int) content.getUnsignedNumber();
      final Map<Object, Object> map = new LinkedHashMap<>(count);
      for (int i = 0; i < count; ++i) {
        final byte entryKeyType = content.getByte();
        final Object entryKey = deserializeValue(database, content, entryKeyType, embeddedModifier);

        final byte entryValueType = content.getByte();
        final Object entryValue = deserializeValue(database, content, entryValueType, embeddedModifier);

        map.put(entryKey, entryValue);
      }
      value = map;
      break;
    }
    case BinaryTypes.TYPE_EMBEDDED: {
      final String typeName = database.getSchema().getDictionary().getNameById((int) content.getUnsignedNumber());

      final int embeddedObjectSize = (int) content.getUnsignedNumber();

      final Binary embeddedBuffer = content.slice(content.position(), embeddedObjectSize);

      value = ((DatabaseInternal) database).getRecordFactory()
          .newImmutableRecord(database, database.getSchema().getType(typeName), null, embeddedBuffer, embeddedModifier);

      content.position(content.position() + embeddedObjectSize);
      break;
    }
    case BinaryTypes.TYPE_ARRAY_OF_SHORTS: {
      final int count = (int) content.getUnsignedNumber();
      final short[] array = new short[count];
      for (int i = 0; i < count; ++i)
        array[i] = (short) content.getNumber();
      value = array;
      break;
    }
    case BinaryTypes.TYPE_ARRAY_OF_INTEGERS: {
      final int count = (int) content.getUnsignedNumber();
      final int[] array = new int[count];
      for (int i = 0; i < count; ++i)
        array[i] = (int) content.getNumber();
      value = array;
      break;
    }
    case BinaryTypes.TYPE_ARRAY_OF_LONGS: {
      final int count = (int) content.getUnsignedNumber();
      final long[] array = new long[count];
      for (int i = 0; i < count; ++i)
        array[i] = content.getNumber();
      value = array;
      break;
    }
    case BinaryTypes.TYPE_ARRAY_OF_FLOATS: {
      final int count = (int) content.getUnsignedNumber();
      final float[] array = new float[count];
      for (int i = 0; i < count; ++i)
        array[i] = Float.intBitsToFloat((int) content.getNumber());
      value = array;
      break;
    }
    case BinaryTypes.TYPE_ARRAY_OF_DOUBLES: {
      final int count = (int) content.getUnsignedNumber();
      final double[] array = new double[count];
      for (int i = 0; i < count; ++i)
        array[i] = Double.longBitsToDouble(content.getNumber());
      value = array;
      break;
    }

    default:
      LogManager.instance().log(this, Level.INFO, "Error on deserializing value of type " + type);
      value = null;
    }
    return value;
  }

  public Binary serializeProperties(final Database database, final Document record, final Binary header, final Binary content) {
    final int headerSizePosition = header.position();
    header.putInt(0); // TEMPORARY PLACEHOLDER FOR HEADER SIZE

    final Map<String, Object> properties = record.propertiesAsMap();
    header.putUnsignedNumber(properties.size());

    final Dictionary dictionary = database.getSchema().getDictionary();

    final DocumentType documentType = record.getType();

    for (final Map.Entry<String, Object> entry : properties.entrySet()) {
      final String propertyName = entry.getKey();

      // WRITE PROPERTY ID FROM THE DICTIONARY
      header.putUnsignedNumber(dictionary.getIdByName(propertyName, true));

      Object value = entry.getValue();

      final int startContentPosition = content.position();

      final Property propertyType = documentType.getPropertyIfExists(propertyName);
      byte type = BinaryTypes.getTypeFromValue(value, propertyType);

      if (value != null && type == BinaryTypes.TYPE_STRING) {
        final int id = dictionary.getIdByName((String) value, false);
        if (id > -1) {
          // WRITE THE COMPRESSED STRING
          type = BinaryTypes.TYPE_COMPRESSED_STRING;
          value = id;
        }
      }

      content.putByte(type);
      serializeValue(database, content, type, value);

      // WRITE PROPERTY CONTENT POSITION
      header.putUnsignedNumber(startContentPosition);
    }

    content.flip();

    final int headerEndOffset = header.position();

    // UPDATE HEADER SIZE
    header.putInt(headerSizePosition, headerEndOffset);

    header.append(content);
    header.flip();
    return header;
  }

  public Class getDateImplementation() {
    return dateImplementation;
  }

  public void setDateImplementation(final Object dateImplementation) throws ClassNotFoundException {
    this.dateImplementation = dateImplementation instanceof Class c ?
        c :
        Class.forName(dateImplementation.toString());
  }

  public Class getDateTimeImplementation() {
    return dateTimeImplementation;
  }

  public void setDateTimeImplementation(final Object dateTimeImplementation) throws ClassNotFoundException {
    this.dateTimeImplementation = dateTimeImplementation instanceof Class<?> c ?
        c :
        Class.forName(dateTimeImplementation.toString());
  }

  public BinaryComparator getComparator() {
    return comparator;
  }

  private void serializeDateTime(final Binary content, final Object value, final byte type) {
    content.putUnsignedNumber(DateUtils.dateTimeToTimestamp(value, DateUtils.getPrecisionFromBinaryType(type)));
  }

  public void setDataEncryption(final DataEncryption dataEncryption) {
    this.dataEncryption = dataEncryption;
  }

}
