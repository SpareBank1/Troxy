package no.sb1.troxy.jetty;


import no.sb1.troxy.http.common.ConnectorAddr;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import java.io.IOException;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.NetworkChannel;
import java.util.ArrayList;
import java.util.List;

public class TroxyJettyServer {

    private static final Logger log = LoggerFactory.getLogger(TroxyJettyServer.class);
    public static final String ANY_ADDRESS = "0.0.0.0";


    public Server jettyServer;
    private ServerConnector httpConnector;
    private ServerConnector httpsConnector;


    public TroxyJettyServer(TroxyJettyServerConfig config) {
        createServer(config);
    }

    private void createServer(TroxyJettyServerConfig config) {
        log.info("Starting Troxy HTTP/HTTPS Server");

        jettyServer = new Server();

        if (config.port > 0 ) {
            /* setup http connector */
            httpConnector = new ServerConnector(jettyServer, new HttpConnectionFactory());
            httpConnector.setPort(config.port);
            log.info("Troxy HTTP port: " + config.port);
            jettyServer.addConnector(httpConnector);
        }

        if (config.securePort > 0) {
            /* setup https connector */
            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStorePath(config.httpsKeystoreFile);
            sslContextFactory.setKeyStoreType(config.httpsKeystoreType);
            sslContextFactory.setKeyStorePassword(config.httpsKeystorePassword);
            sslContextFactory.setCertAlias(config.httpsKeystoreAliasKey);
            sslContextFactory.setKeyManagerPassword(config.httpsKeystoreAliasPassword);
            HttpConfiguration httpsConfiguration = new HttpConfiguration();
            httpsConfiguration.addCustomizer(new SecureRequestCustomizer());
            httpsConnector = new ServerConnector(jettyServer, new SslConnectionFactory(sslContextFactory, "http/1.1"), new HttpConnectionFactory(httpsConfiguration));
            httpsConnector.setPort(config.securePort);
            log.info("Troxy HTTPS port: " + config.securePort);
            jettyServer.addConnector(httpsConnector);
        }
    }

    /**
     * start server, removing https connector and retrying with only http if server start failed
     */
    public TroxyJettyServer start() {


        boolean retryWithOnlyHttp = false;
        try {
            jettyServer.start();
            log.info("Successfully started Troxy HTTP/HTTPS server");
            jettyServer.join();
        } catch (BindException e) {
            log.warn("Unable to start Troxy HTTP/HTTPS Server, it seems like another program is already using the network port we wish to use", e);
            retryWithOnlyHttp = true;
        } catch (Exception e) {
            log.warn("Unable to start Troxy HTTP/HTTPS Server, this is usually caused by wrong keystore password or a corrupt keystore. Trying again with only HTTP server", e);
            retryWithOnlyHttp = true;
        }
        if (retryWithOnlyHttp && httpsConnector != null) {
            jettyServer.removeConnector(httpsConnector);
            try {
                jettyServer.start();
                log.info("Successfully started Troxy HTTP server");
            } catch (BindException e) {
                log.error("Unable to start Troxy HTTP Server, it seems like another program is already using the network port we wish to use, giving up", e);
            } catch (Exception e) {
                log.error("Unable to start Troxy HTTP Server, giving up", e);
            }
        }
        return this;
    }

    public void join() {
        try {
            jettyServer.join();
        } catch (InterruptedException e) {
            log.error("Failed joining", e);
        }

    }

    public void setHandler(Handler handler) {
        jettyServer.setHandler(handler);
    }

    public void stop() {
        try {
            jettyServer.stop();
        } catch (Exception e) {
            log.error("Unable to stop Troxy HTTP Server, giving up", e);
        }
    }


    public static class TroxyJettyServerConfig {
        final int port, securePort;
        final String httpsKeystoreFile;
        final String httpsKeystoreType;
        final String httpsKeystorePassword;
        final String httpsKeystoreAliasKey;
        final String httpsKeystoreAliasPassword;

        public TroxyJettyServerConfig(int port, int securePort, String httpsKeystoreFile, String httpsKeystoreType, String httpsKeystorePassword, String httpsKeystoreAliasKey, String httpsKeystoreAliasPassword) {
            this.port = port;
            this.securePort = securePort;
            this.httpsKeystoreFile = httpsKeystoreFile;
            this.httpsKeystoreType = httpsKeystoreType;
            this.httpsKeystorePassword = httpsKeystorePassword;
            this.httpsKeystoreAliasKey = httpsKeystoreAliasKey;
            this.httpsKeystoreAliasPassword = httpsKeystoreAliasPassword;
        }

        public static class TroxyJettyServerConfigBuilder {
            private int port;
            private int securePort = -1;
            private String httpsKeystoreFile;
            private String httpsKeystoreType;
            private String httpsKeystorePassword;
            private String httpsKeystoreAliasKey;
            private String httpsKeystoreAliasPassword;

            public TroxyJettyServerConfigBuilder setPort(int port) {
                this.port = port;
                return this;
            }

            public TroxyJettyServerConfigBuilder setSecurePort(int securePort) {
                this.securePort = securePort;
                return this;
            }

            public TroxyJettyServerConfigBuilder setHttpsKeystoreFile(String httpsKeystoreFile) {
                this.httpsKeystoreFile = httpsKeystoreFile;
                return this;
            }

            public TroxyJettyServerConfigBuilder setHttpsKeystoreType(String httpsKeystoreType) {
                this.httpsKeystoreType = httpsKeystoreType;
                return this;
            }

            public TroxyJettyServerConfigBuilder setHttpsKeystorePassword(String httpsKeystorePassword) {
                this.httpsKeystorePassword = httpsKeystorePassword;
                return this;
            }

            public TroxyJettyServerConfigBuilder setHttpsKeystoreAliasKey(String httpsKeystoreAliasKey) {
                this.httpsKeystoreAliasKey = httpsKeystoreAliasKey;
                return this;
            }

            public TroxyJettyServerConfigBuilder setHttpsKeystoreAliasPassword(String httpsKeystoreAliasPassword) {
                this.httpsKeystoreAliasPassword = httpsKeystoreAliasPassword;
                return this;
            }

            public TroxyJettyServer.TroxyJettyServerConfig createTroxyJettyServerConfig() {
                return new TroxyJettyServer.TroxyJettyServerConfig(port, securePort, httpsKeystoreFile, httpsKeystoreType, httpsKeystorePassword, httpsKeystoreAliasKey, httpsKeystoreAliasPassword);
            }
        }
    }

    public List<ConnectorAddr> getConnectorAddresses() throws IOException {
        List<ConnectorAddr> retval = new ArrayList<ConnectorAddr>(2);
        if (httpConnector != null && httpConnector.isOpen()) {
            ConnectorAddr connectorAddr = getIPv4ConnectorAddress(httpConnector);
            if (connectorAddr != null) retval.add(connectorAddr);
        }
        if (httpsConnector != null && httpsConnector.isOpen()) {
            ConnectorAddr connectorAddr = getIPv4ConnectorAddress(httpsConnector);
            if (connectorAddr != null) retval.add(connectorAddr);
        }
        return retval;
    }

    private static ConnectorAddr getIPv4ConnectorAddress(ServerConnector connector) throws IOException {
        Object transport = connector.getTransport();
        if (transport != null && transport instanceof NetworkChannel) {
            NetworkChannel channel = (NetworkChannel) transport;
            SocketAddress address = channel.getLocalAddress();
            if (address != null && address instanceof InetSocketAddress) {
                InetSocketAddress inetsockaddr = (InetSocketAddress) address;
                if (inetsockaddr.getAddress() != null ) {
                    if (inetsockaddr.getAddress() instanceof Inet4Address) {
                        Inet4Address inet4Address = (Inet4Address) inetsockaddr.getAddress();
                        return new ConnectorAddr(connector.getDefaultProtocol(), inet4Address.getHostAddress(), inetsockaddr.getPort());
                    }
                    else if (inetsockaddr.getAddress().isAnyLocalAddress()) {
                        return new ConnectorAddr(connector.getDefaultProtocol(), ANY_ADDRESS, inetsockaddr.getPort());
                    }
                }
            }
        }
        return null;
    }
}
