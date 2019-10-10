package no.sb1.troxy.jetty;


import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import java.net.BindException;

public class TroxyJettyServer {

    private static final Logger log = LoggerFactory.getLogger(TroxyJettyServer.class);


    public Server jettyServer;
    private ServerConnector httpsConnector;


    public TroxyJettyServer(TroxyJettyServerConfig config) {
        createServer(config);
    }

    private void createServer(TroxyJettyServerConfig config) {
        log.info("Starting Troxy HTTP/HTTPS Server");

        jettyServer = new Server();

        if (config.port > 0 ) {
            /* setup http connector */
            ServerConnector httpConnector = new ServerConnector(jettyServer, new HttpConnectionFactory());
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
}
