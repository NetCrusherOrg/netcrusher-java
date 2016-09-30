package org.netcrusher.instance;

import org.apache.commons.io.FileUtils;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ZKDatabase;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

public class ZookeeperInstance implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperInstance.class);

    public static final int PORT_BASE_CLIENT = 10100;

    public static final int PORT_BASE_LEADER = 10200;

    public static final int PORT_BASE_ELECTION = 10300;

    public static final int PORT_CRUSHER_BASE = 1000;

    public static final int CLUSTER_SIZE = 3;

    private final ServerCnxnFactory factory;

    private final QuorumPeer peer;

    private final int instance;

    private final int clientPort;

    public ZookeeperInstance(File commonWorkDir, boolean useCrusher, int instance) throws Exception {
        File workDir = new File(commonWorkDir, "instance-" + instance);
        FileUtils.forceMkdir(workDir);

        File logDir = new File(commonWorkDir, "log-" + instance);
        FileUtils.forceMkdir(logDir);

        File markerFile = new File(workDir, "myid");
        FileUtils.write(markerFile, Integer.toString(instance), "UTF-8");

        this.instance = instance;
        this.clientPort = PORT_BASE_CLIENT + instance;

        Properties properties = new Properties();
        properties.put("tickTime", "100");
        properties.put("dataDir", workDir.getAbsolutePath());
        properties.put("dataLogDir", logDir.getAbsolutePath());
        properties.put("clientPort", Integer.toString(clientPort));
        properties.put("clientPortAddress", "127.0.0.1");
        properties.put("initLimit", "5");
        properties.put("syncLimit", "5");

        for (int i = 1; i <= CLUSTER_SIZE; i++) {
            int leaderPort = (i != instance && useCrusher) ?
                    PORT_BASE_LEADER + PORT_CRUSHER_BASE + i : PORT_BASE_LEADER + i;
            int electionPort = (i != instance && useCrusher) ?
                    PORT_BASE_ELECTION + PORT_CRUSHER_BASE + i : PORT_BASE_ELECTION + i;

            properties.put("server." + i, "127.0.0.1:" + leaderPort + ":" + electionPort);
        }

        QuorumPeerConfig config = new QuorumPeerConfig();
        config.parseProperties(properties);

        this.factory = ServerCnxnFactory.createFactory();
        this.peer = createPeer(factory, config);

        LOGGER.info("Zookeeper #{} is started", instance);
    }

    private QuorumPeer createPeer(ServerCnxnFactory cnxnFactory, QuorumPeerConfig config) throws IOException {
        cnxnFactory.configure(config.getClientPortAddress(),
                config.getMaxClientCnxns());

        QuorumPeer quorumPeer = new QuorumPeer();
        quorumPeer.setClientPortAddress(config.getClientPortAddress());
        quorumPeer.setTxnFactory(new FileTxnSnapLog(
                new File(config.getDataLogDir()),
                new File(config.getDataDir())));
        quorumPeer.setQuorumPeers(config.getServers());
        quorumPeer.setElectionType(config.getElectionAlg());
        quorumPeer.setMyid(config.getServerId());
        quorumPeer.setTickTime(config.getTickTime());
        quorumPeer.setMinSessionTimeout(config.getMinSessionTimeout());
        quorumPeer.setMaxSessionTimeout(config.getMaxSessionTimeout());
        quorumPeer.setInitLimit(config.getInitLimit());
        quorumPeer.setSyncLimit(config.getSyncLimit());
        quorumPeer.setQuorumVerifier(config.getQuorumVerifier());
        quorumPeer.setCnxnFactory(cnxnFactory);
        quorumPeer.setZKDatabase(new ZKDatabase(quorumPeer.getTxnFactory()));
        quorumPeer.setLearnerType(config.getPeerType());
        quorumPeer.setSyncEnabled(config.getSyncEnabled());
        quorumPeer.setQuorumListenOnAllIPs(config.getQuorumListenOnAllIPs());

        quorumPeer.start();

        return quorumPeer;
    }

    @Override
    public void close() throws IOException {
        LOGGER.info("Zookeeper #{} is closing", instance);

        peer.shutdown();
        factory.shutdown();
    }

    public int getClientPort() {
        return clientPort;
    }
}
