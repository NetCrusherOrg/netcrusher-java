package org.netcrusher;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.netcrusher.common.NioReactor;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class HttpClientTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientTest.class);

    private static final String REMOTE_HOST = "hc.apache.org";

    private static final int REMOTE_PORT = 80;

    private static final String RESOURCE = "http://hc.apache.org:%d/httpcomponents-client-ga/examples.html";

    private static final int CRUSHER_PORT = 10080;

    private CloseableHttpClient http;

    private NioReactor reactor;

    private TcpCrusher crusher;

    @Before
    public void setUp() throws Exception {
        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
                .withReactor(reactor)
                .withBindAddress("127.0.0.1", CRUSHER_PORT)
                .withConnectAddress(REMOTE_HOST, REMOTE_PORT)
                .buildAndOpen();

        DnsResolver dnsResolver = new SystemDefaultDnsResolver() {
            @Override
            public InetAddress[] resolve(final String host) throws UnknownHostException {
                if (host.equalsIgnoreCase(REMOTE_HOST)) {
                    return new InetAddress[] { InetAddress.getByAddress(new byte[] {127, 0, 0, 1}) };
                } else {
                    return super.resolve(host);
                }
            }
        };

        HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> httpConnectionFactory =
                new ManagedHttpClientConnectionFactory();

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(
                socketFactoryRegistry, httpConnectionFactory, dnsResolver);

        http = HttpClients.createMinimal(connectionManager);
    }

    @After
    public void tearDown() throws Exception {
        if (http != null) {
            http.close();
        }

        if (crusher != null) {
            crusher.close();
        }

        if (reactor != null) {
            reactor.close();
        }
    }

    @Test
    public void test() throws Exception {
        HttpUriRequest request = composeRequest();

        try (CloseableHttpResponse response = http.execute(request)) {
            Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

            HttpEntity entity = response.getEntity();
            EntityUtils.consume(entity);
        }
    }

    @Test
    public void testClosed() throws Exception {
        HttpUriRequest request = composeRequest();

        crusher.close();

        try {
            http.execute(request);
            Assert.fail("Exception is expected");
        } catch (HttpHostConnectException e) {
            LOGGER.debug("Exception dump", e);
        }
    }

    @Test
    public void testFreeze() throws Exception {
        HttpUriRequest request = composeRequest();

        crusher.freeze();

        try {
            http.execute(request);
            Assert.fail("Exception is expected");
        } catch (SocketTimeoutException e) {
            LOGGER.debug("Exception dump", e);
        }
    }

    private HttpUriRequest composeRequest() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(5000)
                .setConnectTimeout(3000)
                .setSocketTimeout(1000)
                .setRedirectsEnabled(true)
                .setCircularRedirectsAllowed(false)
                .setMaxRedirects(3)
                .build();

        HttpGet request = new HttpGet(String.format(RESOURCE, CRUSHER_PORT));
        request.setConfig(requestConfig);

        return request;
    }
}

