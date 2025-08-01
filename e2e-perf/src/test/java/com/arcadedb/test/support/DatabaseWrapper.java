package com.arcadedb.test.support;

import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.remote.RemoteDatabase;
import com.arcadedb.remote.RemoteHttpComponent;
import com.arcadedb.remote.RemoteSchema;
import com.arcadedb.remote.RemoteServer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.arcadedb.test.support.ContainersTestTemplate.DATABASE;
import static com.arcadedb.test.support.ContainersTestTemplate.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseWrapper {
  private static final Logger            logger = LoggerFactory.getLogger(DatabaseWrapper.class);
  private final        String            host;
  private final        int               port;
  private final        RemoteDatabase    db;
  private final        Supplier<Integer> idSupplier;
  private final        Timer             photosTimer;
  private final        Timer             usersTimer;
  private final        Timer             friendshipTimer;
  private final        Timer             likeTimer;

  public DatabaseWrapper(String host, int port, Supplier<Integer> idSupplier) {
    this.host = host;
    this.port = port;
    this.db = connectToDatabase();
    this.idSupplier = idSupplier;
    usersTimer = Metrics.timer("arcadedb.test.inserted.users");
    photosTimer = Metrics.timer("arcadedb.test.inserted.photos");
    friendshipTimer = Metrics.timer("arcadedb.test.inserted.friendship");
    likeTimer = Metrics.timer("arcadedb.test.inserted.like");
  }

  private RemoteDatabase connectToDatabase() {
    RemoteDatabase database = new RemoteDatabase(host,
        port,
        DATABASE,
        "root",
        PASSWORD);
    database.setConnectionStrategy(RemoteHttpComponent.CONNECTION_STRATEGY.FIXED);
    database.setTimeout(30000);
    return database;
  }

  public void close() {
    db.close();
  }

  public void createDatabase() {
    RemoteServer server = new RemoteServer(host,
        port,
        "root",
        PASSWORD);
    server.setConnectionStrategy(RemoteHttpComponent.CONNECTION_STRATEGY.FIXED);

    if (server.exists(DATABASE)) {
      logger.info("Dropping existing database {}", DATABASE);
      server.drop(DATABASE);
    }
    server.create(DATABASE);
  }

  /**
   * This method creates the schema for the test.
   * It creates vertex types User and Photo, edge types HasUploaded, FriendOf, and Likes.
   * It also creates properties for User and Photo vertex types.
   */
  public void createSchema() {
    //this is a test-double of HTTPGraphIT.testOneEdgePerTx test
    db.command("sqlscript",
        """
            CREATE VERTEX TYPE User;
            CREATE PROPERTY User.id INTEGER;
            CREATE INDEX ON User (id) UNIQUE;

            CREATE VERTEX TYPE Photo;
            CREATE PROPERTY Photo.id INTEGER;
            CREATE INDEX ON Photo (id) UNIQUE;

            CREATE EDGE TYPE HasUploaded;

            CREATE EDGE TYPE FriendOf;

            CREATE EDGE TYPE Likes;
            """);
  }

  /**
   * This method checks if the schema is created correctly.
   * It checks if the types User, Photo, HasUploaded, FriendOf, and Likes exist.
   */
  public void checkSchema() {
    RemoteSchema schema = db.getSchema();
    assertThat(schema.existsType("Photo")).isTrue();
    assertThat(schema.existsType("User")).isTrue();
    assertThat(schema.existsType("HasUploaded")).isTrue();
    assertThat(schema.existsType("FriendOf")).isTrue();
    assertThat(schema.existsType("Likes")).isTrue();
  }

  /**
   * This method creates a number of users and photos for each user.
   * The photos are created in a transaction with the user.
   *
   * @param numberOfUsers  the number of users to create
   * @param numberOfPhotos the number of photos to create for each user
   */
  public void addUserAndPhotos(int numberOfUsers, int numberOfPhotos) {
    for (int userIndex = 1; userIndex <= numberOfUsers; userIndex++) {
      int userId = idSupplier.get();
      try {
        usersTimer.record(() -> {
              db.command("sqlscript",
                  """
                      BEGIN;
                      LOCK TYPE User;
                      CREATE VERTEX User SET id = ?;
                      COMMIT RETRY 30;
                      """, userId);
            }

        );

        addPhotosOfUser(userId, numberOfPhotos);

      } catch (Exception e) {
        Metrics.counter("arcadedb.test.inserted.users.error").increment();
        logger.error("Error creating user {}: {}", userId, e.getMessage());
      }

    }
  }

  /**
   * This method adds photos for a specific user.
   * It creates a photo in a transaction with the user.
   *
   * @param userId         the id of the user to add photos for
   * @param numberOfPhotos the number of photos to add for the user
   */
  private void addPhotosOfUser(int userId, int numberOfPhotos) {
    for (int i = 0; i < numberOfPhotos; i++) {
      int photoId = idSupplier.get();
      String photoName = String.format("download-%s.jpg", photoId);
      String sqlScript = """
          BEGIN;
          LOCK TYPE User, Photo, HasUploaded;
          LET photo = CREATE VERTEX Photo SET id = ?, name = ?;
          LET user = SELECT FROM User WHERE id = ?;
          CREATE EDGE HasUploaded FROM $user TO $photo;
          COMMIT RETRY 30;
          """;
      try {
        photosTimer.record(() -> {
              db.command("sqlscript", sqlScript, photoId, photoName, userId);
            }
        );

      } catch (Exception e) {
        Metrics.counter("arcadedb.test.inserted.photos.error").increment();
        logger.error("Error creating photo {}: {}", photoId, e.getMessage());
      }
    }
  }

  /**
   * This method creates friendships between users.
   * It creates a number of friendships in iterations.
   * Each iteration creates a number of friendships between users.
   *
   * @param numOfFriendships the number of iterations to create friendships
   */
  public void createFriendships(int numOfFriendships) {
    int pauseEvery = numOfFriendships / 10;
    int count = 0;
    TypeIdSupplier userIdSupplier = new TypeIdSupplier(db, "User");
    while (count < numOfFriendships) {
      Integer userId1 = userIdSupplier.get();
      Integer userId2 = userIdSupplier.get();
      if (userId1 == null || userId2 == null || userId1.equals(userId2)) {
        continue; // Skip if no more users or same user
      }
      addFriendship(userId1, userId2);
      count++;
      if (count % pauseEvery == 0) {
        try {
          TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
          // Ignore the interruption
        }
      }
    }
    logger.info("Created {} friendships", count);
  }

  public void createLike(int numOfLikes) {
    int pauseEvery = numOfLikes / 10;
    int count = 0;
    TypeIdSupplier userIdSupplier = new TypeIdSupplier(db, "User");
    TypeIdSupplier photoIdSupplier = new TypeIdSupplier(db, "Photo");
    while (count < numOfLikes) {
      Integer userId = userIdSupplier.get();
      Integer photoId = photoIdSupplier.get();
      if (userId == null || photoId == null) {
        continue; // No more users or photos available
      }
      addLike(userId, photoId);
      count++;
      if (count % pauseEvery == 0) {
        try {
          TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
          // Ignore the interruption
        }
      }

    }
    logger.info("Created {} likes", count);
  }

  /**
   * This method creates a friendship between two users.
   * The friendship is created in a transaction with the users.
   *
   * @param userId1 the id of the first user
   * @param userId2 the id of the second user
   */
  public void addFriendship(int userId1, int userId2) {
    try {
      friendshipTimer.record(() -> {
            db.command("sqlscript",
                """
                    BEGIN;
                    LOCK TYPE User, FriendOf;
                    CREATE EDGE FriendOf FROM (SELECT FROM User WHERE id = ?) TO (SELECT FROM User WHERE id = ?);
                    COMMIT RETRY 30;
                    """, userId1, userId2);
          }
      );

    } catch (Exception e) {
      Metrics.counter("arcadedb.test.inserted.friendship.error").increment();
      logger.error("Error creating friendship between {} and {}: {}", userId1, userId2, e.getMessage());
    }
  }

  public void addLike(int userId, int photoId) {
    try {
      likeTimer.record(() -> {
            db.command("sqlscript",
                """
                    BEGIN;
                    LOCK TYPE User, Photo, Likes;
                    CREATE EDGE Likes FROM (SELECT FROM User WHERE id = ?) TO (SELECT FROM Photo WHERE id = ?);
                    COMMIT RETRY 30;
                    """, userId, photoId);
          }
      );

    } catch (Exception e) {
      Metrics.counter("arcadedb.test.inserted.like.error").increment();
      logger.error("Error creating like between {} and {}: {}", userId, photoId, e.getMessage());
    }
  }

  public void assertThatUserCountIs(int expectedCount) {
    assertThat(countUsers()).isEqualTo(expectedCount);
  }

  public List<Integer> getUserIds(int numOfUsers, int skip) {
    ResultSet resultSet = db.query("sql", "SELECT id  FROM User ORDER BY id SKIP ? LIMIT ?", skip, numOfUsers);
    return resultSet.stream()
        .map(r -> r.<Integer>getProperty("id"))
        .toList();
  }

  public void assertThatFriendshipCountIs(int expectedCount) {
    assertThat(countFriendships()).isEqualTo(expectedCount);
  }

  public void assertThatPhotoCountIs(int expectedCount) {
    assertThat(countPhotos()).isEqualTo(expectedCount);
  }

  public long countUsers() {
    return db.countType("User", false);
  }

  public long countPhotos() {
    return db.countType("Photo", false);
  }

  public long countFriendships() {
    return db.countType("FriendOf", false);
  }

  public ResultSet command(String command, Object... args) {
    logger.info("Execute command: {}", command);
    return db.command("sql", command, args);
  }

  public long countLikes() {
    return db.countType("Likes", false);
  }

  public void assertThatLikesCountIs(int expectedCount) {
    assertThat(countLikes()).isEqualTo(expectedCount);
  }

}
