const { Client } = require("pg");
const { GenericContainer, Wait } = require("testcontainers");

describe("E2E tests using pg client", () => {
  jest.setTimeout(60000);

  let arcadedbContainer;
  let postgresClient;

  beforeAll(async () => {
    arcadedbContainer = await new GenericContainer("arcadedata/arcadedb:latest")
      .withExposedPorts(2480, 6379, 5432, 8182)
      .withEnvironment({
      JAVA_OPTS:
          "-Darcadedb.server.rootPassword=playwithdata -Darcadedb.server.defaultDatabases=beer[root]{import:https://github.com/ArcadeData/arcadedb-datasets/raw/main/orientdb/OpenBeer.gz} -Darcadedb.server.plugins=Postgres:com.arcadedb.postgres.PostgresProtocolPlugin,GremlinServer:com.arcadedb.server.gremlin.GremlinServerPlugin",
    })
      .withStartupTimeout(60000)
      .withWaitStrategy(Wait.forHttp("/api/v1/ready", 2480).forStatusCodeMatching((statusCode) => statusCode === 204))
      .start();
    const connectionString = `postgres://root:playwithdata@${arcadedbContainer.getHost()}:${arcadedbContainer.getMappedPort(5432)}/beer`;

    postgresClient = new Client(connectionString);
    await postgresClient.connect();
  });

  afterAll(async () => {
    await postgresClient.end();
    await arcadedbContainer.stop();
  });

  it("should run simple query", async () => {
    const query = "select from Beer limit 10";
    const res = await postgresClient.query(query);
    expect(res.rows.length).toBe(10);
  });


  it("should run schema query", async () => {
    const query = "select * from schema:types limit -1";
    const res = await postgresClient.query(query);
    expect(res.rows.length).toBeGreaterThan(0);
    res.rows.forEach((row) => {
      console.log(row);
    });
  });

//  it("should run parametrixed query", async () => {
//    const query = "select from Beer where name = $1 limit 10";
//    const res = await postgresClient.query(query, ['Stout']);
//    expect(res.rows.length).toBe(10);
//  });


});
