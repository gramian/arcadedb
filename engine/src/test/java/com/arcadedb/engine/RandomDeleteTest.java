package com.arcadedb.engine;

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.database.RID;
import com.arcadedb.database.Record;
import com.arcadedb.graph.MutableVertex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

public class RandomDeleteTest {
  private final static int    TOT_RECORDS = 100_000;
  private final static String TYPE        = "Product";
  private static final int    CYCLES      = 20;

  @Test
  public void testSmallRecords() {
    try (DatabaseFactory databaseFactory = new DatabaseFactory("databases/randomDeleteTest")) {
      if (databaseFactory.exists())
        databaseFactory.open().drop();

      try (Database db = databaseFactory.create()) {
        db.getSchema().createVertexType(TYPE, 1);

        db.transaction(() -> {
          List<RID> rids = insert(db);
          Assertions.assertEquals(TOT_RECORDS, db.countType(TYPE, true));

          // DELETE FROM 1 TO N
          for (int i = 0; i < TOT_RECORDS; i++)
            db.deleteRecord(rids.get(i).asVertex());

          Assertions.assertEquals(0, db.countType(TYPE, true));

          // DELETE RANDOMLY X TIMES
          for (int cycle = 0; cycle < CYCLES; cycle++) {
            rids = insert(db);
            checkRecords(db, rids);

            for (int deleted = 0; deleted < TOT_RECORDS; ) {
              final int i = new Random().nextInt(TOT_RECORDS);
              final RID rid = rids.get(i);
              if (rid != null) {
                db.deleteRecord(rid.asVertex());
                rids.set(i, null);
                ++deleted;
              }
            }
          }

          Assertions.assertEquals(0, db.countType(TYPE, true));

        });
      } finally {
        if (databaseFactory.exists())
          databaseFactory.open().drop();
      }
    }
  }

  private void checkRecords(Database db, List<RID> rids) {
    for (int i = 0; i < rids.size(); i++)
      Assertions.assertNotNull(rids.get(i).asVertex());

    final List<RID> found = new ArrayList<>();
    for (Iterator<Record> it = db.iterateType(TYPE, true); it.hasNext(); )
      found.add(it.next().asVertex().getIdentity());

    Assertions.assertEquals(rids, found);

    Assertions.assertEquals(rids.size(), db.countType(TYPE, true));
  }

  private static List<RID> insert(final Database db) {
    final List<RID> rids = new ArrayList<>(TOT_RECORDS);
    for (int i = 0; i < TOT_RECORDS; i++) {
      final MutableVertex v = db.newVertex(TYPE)//
          .set("id", i)//
          .save();

      rids.add(v.getIdentity());
    }
    return rids;
  }
}
