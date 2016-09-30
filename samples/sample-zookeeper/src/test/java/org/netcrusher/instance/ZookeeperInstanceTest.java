package org.netcrusher.instance;

import com.google.common.base.Joiner;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.*;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

@Ignore
public class ZookeeperInstanceTest {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ZookeeperInstanceTest.class);

    private static final File TMP_FOLDER = FileUtils.getTempDirectory();

    private static final File WORK_FOLDER = new File(TMP_FOLDER, "ZooKeeperTest");

    private ZookeeperInstance instance1;

    private ZookeeperInstance instance2;

    private ZookeeperInstance instance3;

    private String connection;

    @Before
    public void setUp() throws Exception {
        System.setProperty("zookeeper.jmx.log4j.disable", "true");

        Assert.assertFalse(WORK_FOLDER.exists());
        FileUtils.forceMkdir(WORK_FOLDER);

        instance1 = new ZookeeperInstance(WORK_FOLDER, false, 1);
        instance2 = new ZookeeperInstance(WORK_FOLDER, false, 2);
        instance3 = new ZookeeperInstance(WORK_FOLDER, false, 3);

        Collection<String> collections = new ArrayList<>();
        for (int i = 1; i <= ZookeeperInstance.CLUSTER_SIZE; i++) {
            int port = ZookeeperInstance.PORT_BASE_CLIENT + i;
            collections.add("127.0.0.1:" + port);
        }
        connection = Joiner.on(",").join(collections);

        LOGGER.info("==========================================================================================");
        LOGGER.info("Prepared");
        LOGGER.info("==========================================================================================");
    }

    @After
    public void tearDown() throws Exception {
        LOGGER.info("==========================================================================================");
        LOGGER.info("Shutdowning...");
        LOGGER.info("==========================================================================================");

        IOUtils.closeQuietly(instance1);
        IOUtils.closeQuietly(instance2);
        IOUtils.closeQuietly(instance3);

        FileUtils.deleteDirectory(WORK_FOLDER);
    }

    @Test
    public void test() throws Exception {
        byte[] data1 = { 1, 2, 3};
        write(connection, "/my/path", data1);

        byte[] data2 = read(connection, "/my/path");

        Assert.assertArrayEquals(data1, data2);
    }

    private void write(String connection, String path, byte[] data) throws Exception {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.newClient(connection, retryPolicy);

        client.start();
        try {
            client.create()
                    .creatingParentContainersIfNeeded()
                    .forPath(path, data);
        } finally {
            LOGGER.info("Closing client");
            client.close();
        }
    }

    private byte[] read(String connection, String path) throws Exception {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.newClient(connection, retryPolicy);

        client.start();
        try {
            return client.getData()
                    .forPath(path);
        } finally {
            LOGGER.info("Closing client");
            client.close();
        }
    }

}
