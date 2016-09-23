package org.netcrusher;

import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;
import org.hsqldb.Server;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.common.NioReactor;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

public class DbTest {

    private static final int DB_PORT = 10777;

    private static final int CRUSHER_PORT = 10778;

    private static final String SQL_CHECK = "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS";

    private Server hsqlServer;

    private BoneCP connectionPool;

    private NioReactor reactor;

    private TcpCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
                .withReactor(reactor)
                .withLocalAddress("127.0.0.1", CRUSHER_PORT)
                .withRemoteAddress("127.0.0.1", DB_PORT)
                .buildAndOpen();

        hsqlServer = new Server();
        hsqlServer.setAddress("127.0.0.1");
        hsqlServer.setPort(DB_PORT);
        hsqlServer.setDaemon(true);
        hsqlServer.setErrWriter(new PrintWriter(System.err));
        hsqlServer.setLogWriter(new PrintWriter(System.out));
        hsqlServer.setNoSystemExit(true);
        hsqlServer.setDatabasePath(0, "mem:testDb");
        hsqlServer.setDatabaseName(0, "testDb");

        hsqlServer.start();

        Class.forName("org.hsqldb.jdbc.JDBCDriver");

        BoneCPConfig config = new BoneCPConfig();
        config.setJdbcUrl(String.format("jdbc:hsqldb:hsql://127.0.0.1:%d/testDb", CRUSHER_PORT));
        config.setInitSQL(SQL_CHECK);
        config.setAcquireIncrement(1);
        config.setAcquireRetryAttempts(1);
        config.setAcquireRetryDelayInMs(1000);
        config.setConnectionTimeoutInMs(1000);
        config.setDefaultAutoCommit(false);
        config.setDefaultReadOnly(true);
        config.setDefaultTransactionIsolation("NONE");
        config.setPartitionCount(1);
        config.setMinConnectionsPerPartition(1);
        config.setMaxConnectionsPerPartition(1);
        config.setLazyInit(true);

        connectionPool = new BoneCP(config);
    }

    @After
    public void tearDown() throws Exception {
        connectionPool.shutdown();
        hsqlServer.stop();
        crusher.close();
        reactor.close();
    }

    @Test
    public void test() throws Exception {
        // create a connection
        Connection connection = connectionPool.getConnection();

        // query some data
        connection.createStatement().executeQuery(SQL_CHECK);

        // check the pool has only one connection
        try {
            connectionPool.getConnection();
            Assert.fail("Exception is expected");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // disconnect
        crusher.crush();

        // query should fail
        try {
            connection.createStatement().executeQuery(SQL_CHECK);
            Assert.fail("Exception is expected");
        } catch (SQLTransientConnectionException e) {
            e.printStackTrace();
        }

        // close the connection as it is useless
        connection.close();

        // get a new fresh one from the pool
        connection = connectionPool.getConnection();

        // query some data
        connection.createStatement().executeQuery(SQL_CHECK);

        // close
        connection.close();
    }


}


