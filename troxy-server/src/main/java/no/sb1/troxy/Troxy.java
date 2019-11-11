package no.sb1.troxy;

import no.sb1.troxy.common.Config;
import no.sb1.troxy.common.Mode;
import no.sb1.troxy.http.common.Filter;
import no.sb1.troxy.jetty.TroxyJettyServer;
import no.sb1.troxy.jetty.TroxyJettyServer.TroxyJettyServerConfig.TroxyJettyServerConfigBuilder;
import no.sb1.troxy.rest.ApiHandler;
import no.sb1.troxy.util.*;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.AliasedX509ExtendedKeyManager;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A server for HTTP requests.
 */
public class Troxy implements Runnable {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(Troxy.class);

    /**
     * Where Troxy resides, set to current working directory.
     */
    public final String troxyHome;

    /**
     * Directory where filters can be found.
     */
    public final String filterDirectory;
    /**
     * Directory where log files can be found.
     */
    public final String logDirectory;

    /**
     * File used for storing loaded recordings in Troxy.
     */
    public final String loadedRecordingsFile;

    /**
     * Configuration key for the HTTP simulator mode.
     */
    private static final String KEY_MODE = "troxy.mode";
    /**
     * Default mode for the HTTP simulator.
     */
    private static final Mode DEFAULT_MODE = Mode.PLAYBACK;
    /**
     * Default HTTP port.
     */
    private static final int DEFAULT_HTTP_PORT = 8080;
    /**
     * Default HTTPS port.
     */
    private static final int DEFAULT_HTTPS_PORT = 8081;
    /**
     * The default interval between collecting statistics, in minutes.
     */
    private static final int DEFAULT_STATISTICS_INTERVAL = 60;

    private final Config config;
    private Mode mode;
    private final Cache cache;
    private final StatisticsCollector statisticsCollector;
    private TroxyFileHandler troxyFileHandler;
    private List<Class<Filter>> filterClasses = new ArrayList<>();
    private static TroxyJettyServer server;
    private KeyManager[] proxyKeyManagers = null;
    private boolean proxyForceHttps = false;


    /**
     * Default constructor.
     *
     * @param troxyHome
     * @param logDirectory
     */
    public Troxy(final String troxyHome,
                 final String logDirectory,
                 final String loadedRecordingsFile,
                 final String filterDirectory,
                 final Config config,
                 final Cache cache,
                 final TroxyFileHandler troxyFileHandler,
                 final StatisticsCollector statisticsCollector) {

        this.troxyHome = troxyHome;
        this.logDirectory = logDirectory;
        this.loadedRecordingsFile = loadedRecordingsFile;
        this.filterDirectory = filterDirectory;
        this.config = config;
        this.cache = cache;
        this.troxyFileHandler = troxyFileHandler;
        this.statisticsCollector = statisticsCollector;

        mode = Mode.valueOf(config.getValue(KEY_MODE, DEFAULT_MODE.name()).toUpperCase());

        TroxyJettyServer.TroxyJettyServerConfig jettyConfig = createConfig();
        /* set up server */
        server = new TroxyJettyServer(jettyConfig);
        loadFilters();
        initProxySettings();
        
        /* handlers */
        HandlerList handlerList = getHandlerList(cache);

        server.setHandler(handlerList);

    }

    private HandlerList getHandlerList(Cache cache) {
        HandlerList handlerList = new HandlerList();
        // a handler working like an interceptor to store recent requests sent to the server
        RequestInterceptor requestInterceptor = new RequestInterceptor();
        handlerList.addHandler(requestInterceptor);

        // a resource handler for static files (html, js, css)
        ResourceHandler resourceHandler = new ResourceHandler();
        String resourceBase = "server/server/src/main/resources/webapp";
        if ((new File(resourceBase)).exists())
            resourceHandler.setResourceBase(resourceBase); // running locally, makes us able to modify html/css/js without building a new jar
        else
            resourceHandler.setResourceBase(Troxy.class.getClassLoader().getResource("webapp").toExternalForm());
        handlerList.addHandler(resourceHandler);

        //Enable REST API by default
        String enableRest = config.getValue("troxy.restapi.enabled");
        if (!"false".equalsIgnoreCase(enableRest)) {
            // a servlet for handling the REST api
            ResourceConfig resourceConfig = new ResourceConfig();
            resourceConfig.register(JacksonFeature.class);
            resourceConfig.register(MultiPartFeature.class);
            resourceConfig.property(ServerProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true);
            resourceConfig.property(ServerProperties.METAINF_SERVICES_LOOKUP_DISABLE, true);
            resourceConfig.register(MultiPartFeature.class);
            resourceConfig.register(new ApiHandler(this, config, statisticsCollector, troxyFileHandler, cache));

            ServletHolder apiServlet = new ServletHolder(new ServletContainer(resourceConfig));
            apiServlet.setInitOrder(0);

            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            context.setVirtualHosts(getRestAPIHostnames());
            context.setContextPath("/api");
            context.addServlet(apiServlet, "/*");

            handlerList.addHandler(context);
        }

        // and finally the simulator handler
        SimulatorHandler simulatorHandler = new SimulatorHandler(this, config, troxyFileHandler, cache);
        handlerList.addHandler(simulatorHandler);
        return handlerList;
    }

    private String[] getRestAPIHostnames() {
        String restHostnames=config.getValue("troxy.restapi.hostnames");
        return restHostnames != null && !restHostnames.isEmpty() ? restHostnames.trim().split("\\s*,\\s*"): null;
    }

    /**
     * Get the current HTTP simulator mode.
     *
     * @return The current HTTP simulator mode.
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Set the current HTTP simulator mode.
     *
     * @param mode The current HTTP simulator mode.
     */
    public void setMode(String mode) {
        this.mode = Mode.valueOf(mode);
    }

    public String getLoadedRecordingsFile() {
        return loadedRecordingsFile;
    }

    public KeyManager[] getProxyKeyManagers() { return proxyKeyManagers; }

    public boolean isProxyForceHttps() { return proxyForceHttps; }

    /**
     * Get the list of filters that may be applied to requests/responses.
     *
     * @return The filters that may be applied to requests/responses.
     */
    public List<Class<Filter>> getFilterClasses() {
        return filterClasses;
    }


    public TroxyJettyServer.TroxyJettyServerConfig createConfig() {
        int port;
        try {
            port = Integer.parseInt(config.getValue("http.port", "" + DEFAULT_HTTP_PORT));
        } catch (NumberFormatException e) {
            log.warn("Unable to parse HTTP port from configuration, falling back to default port {}", DEFAULT_HTTP_PORT);
            port = DEFAULT_HTTP_PORT;
        }

        TroxyJettyServerConfigBuilder builder = new TroxyJettyServerConfigBuilder();
        builder.setPort(port);

        final String keyStorePath = config.getValue("https.keystore.file");
        if (keyStorePath != null && !keyStorePath.isEmpty()) {
            try {
                builder.setSecurePort(Integer.parseInt(config.getValue("https.port", "" + DEFAULT_HTTPS_PORT)));
            } catch (NumberFormatException e) {
                log.warn("Unable to parse HTTPS port from configuration, falling back to default port {}", DEFAULT_HTTPS_PORT);

                builder.setSecurePort(DEFAULT_HTTPS_PORT);
            }

            builder.setHttpsKeystoreFile(keyStorePath);
            builder.setHttpsKeystoreType(config.getValue("https.keystore.type"));
            builder.setHttpsKeystorePassword(config.getValue("https.keystore.password"));
            builder.setHttpsKeystoreAliasKey(config.getValue("https.keystore.alias.key"));
            builder.setHttpsKeystoreAliasPassword(config.getValue("https.keystore.alias.password"));
        }

        return builder.createTroxyJettyServerConfig();
    }

    /**
     * Run the TroxyServer.
     */
    @Override
    public void run() {
        log.info("Starting Troxy HTTP/HTTPS Server");


        server.start();
        log.info("Successfully started Troxy HTTP/HTTPS server");
        server.join();

    }

    /**
     * Main method.
     *
     * @param args Arguments to Troxy.
     */
    public static void main(String... args) {
        log.info("Troxy starting...");

        final String userDir = System.getProperty("user.dir");
        final String troxyDir = System.getProperty("troxy.home");
        final String logDir = System.getProperty("troxy.log.dir");

        final String troxyHome = new File(troxyDir == null ? Paths.get(userDir).toString() : troxyDir).getAbsolutePath();
        final String logDirectory = new File(logDir == null ? Paths.get(userDir, "logs").toString() : logDir).getAbsolutePath();
        final String configDirectory = new File(troxyHome, "conf").getAbsolutePath();
        final String recordingDirectory = new File(troxyHome, "data" + File.separator + "recordings").getAbsolutePath();
        final String loadedRecordingsFile = new File(configDirectory + File.separator + "loaded_recordings.ini").getAbsolutePath();
        final String statisticsDirectory = new File(logDirectory, "statistics").getAbsolutePath();
        final String filterDirectory = new File(troxyHome, "data" + File.separator + "filters").getAbsolutePath();

        final Config config = configure(configDirectory);

        TroxyFileHandler troxyFileHandler = new TroxyFileHandler(recordingDirectory, loadedRecordingsFile);

        Cache cache = Cache.createCacheRoot();
        /* add recordings that were loaded when Troxy stopped */
        File loadedRecordings = new File(loadedRecordingsFile);
        if (loadedRecordings.exists()) {
            try (BufferedReader loadedFilesReader = new BufferedReader(new FileReader(loadedRecordings))) {
                Set<String> loadRecordings = new HashSet<>();
                String filename;
                while ((filename = loadedFilesReader.readLine()) != null)
                    loadRecordings.add(filename);
                Cache.loadRecordings(cache, troxyFileHandler, loadRecordings);
            } catch (IOException e) {
                log.warn("Unable to read file with loaded recordings", e);
            }
        }
        /* start statistics thread */
        final StatisticsCollector statisticsCollector = new StatisticsCollector(getStatisticsInterval(config), statisticsDirectory, cache);
        statisticsCollector.startThread();

        /* set up server thread & start it */
        Troxy troxy = new Troxy(troxyHome, logDirectory, loadedRecordingsFile, filterDirectory, config, cache, troxyFileHandler, statisticsCollector);
        new Thread(troxy).start();

        /* set up shutdown hook */
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                log.info("Stopping Troxy HTTP/HTTPS Server");
                try {
                    troxyFileHandler.storeLoadedRecordings(cache.getRecordings());
                    server.stop();
                } catch (Exception e) {
                    log.warn("Unable to stop Troxy HTTP/HTTPS Server", e);
                }
                /* stop statistics thread */
                statisticsCollector.stopThread();
            }
        });
    }

    /**
     * Reconfigure Troxy server.
     */
    public boolean reconfigure() {
        log.info("Reconfiguring Troxy server");
        config.reload();
        mode = Mode.valueOf(config.getValue(KEY_MODE, DEFAULT_MODE.name()).toUpperCase());
        updateStatisticsInterval();
        loadFilters();
        initProxySettings();
        return true;
    }

    public static Config configure(final String configDirectory) {
        log.info("Configuring Troxy server");
        return Config.load(Paths.get(configDirectory, "troxy.properties"));
    }


    /**
     * Update the interval for when to collect statistics.
     */
    private void updateStatisticsInterval() {
        statisticsCollector.setStatisticsInterval(getStatisticsInterval(config));
    }

    private static int getStatisticsInterval(final Config config) {
        int statisticsInterval;
        try {
            statisticsInterval = Integer.parseInt(Objects.requireNonNull(config.getValue("statistics.interval")));
        } catch (NumberFormatException e) {
            log.warn("Unable to parse configuration value for statistics interval, falling back to default interval: {}", DEFAULT_STATISTICS_INTERVAL);
            statisticsInterval = DEFAULT_STATISTICS_INTERVAL;
        }
        return statisticsInterval;
    }

    /**
     * Load filterClasses.
     */
    private void loadFilters() {
        log.info("Loading filters from {}", filterDirectory);
        Set<String> files = new HashSet<>();
        try {
            files = TroxyFileHandler.getFilesInDirectory(Paths.get(filterDirectory));
        } catch (IOException e) {
            log.warn("Unable to read files from directory: {}", filterDirectory, e);
        }
        filterClasses.clear();
        for (String filename : files) {
            if (!filename.endsWith(".jar")) {
                log.info("Skipping file (unknown type): {}", filename);
                continue;
            }
            try {
                URL jarFile = new URL("jar", "", "file:" + filename + "!/");
                URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{jarFile});
                ZipFile zipFile = new ZipFile(new File(filename));
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entry.isDirectory() || !entryName.endsWith(".class") || entryName.contains("$"))
                        continue;
                    try {
                        entryName = entryName.substring(0, entryName.lastIndexOf('.'));
                        entryName = entryName.replace('/', '.');
                        Class<Filter> filterClass = (Class<Filter>) classLoader.loadClass(entryName);
                        /* force filterClasses to [re]load configuration */
                        Filter filter = filterClass.newInstance();
                        filter.reload(config);
                        if (filter.isEnabled()) {
                            filterClasses.add(filterClass);
                            log.info("Loaded filter {}", entryName);
                        } else {
                            log.info("Not loading filter {} as it's disabled", entryName);
                        }
                    } catch (ClassNotFoundException e) {
                        log.warn("Unable to load file \"{}\" as a class", entryName, e);
                    } catch (InstantiationException e) {
                        log.warn("Unable to instantiate filter \"{}\"", entryName, e);
                    } catch (IllegalAccessException e) {
                        log.warn("Unable to access filter \"{]\"", entryName, e);
                    }
                }
            } catch (MalformedURLException e) {
                log.warn("Unable to locate file \"{}\"", filename, e);
            } catch (IOException e) {
                log.warn("Unable to read file \"{}\" as a jar file", filename, e);
            }
        }
        Collections.sort(filterClasses, (o1, o2) -> o1.getName().compareTo(o2.getName()));
        log.info("Filters will be executed in this order: {}", filterClasses.stream().map(Class::getName).collect(Collectors.joining(", ")));
    }

    private void initProxySettings() {
        this.proxyForceHttps="true".equalsIgnoreCase(config.getValue("egress.https.force"));
        this.proxyKeyManagers = initProxyKeyManagers();
    }

    private KeyManager[] initProxyKeyManagers() {
        log.info("Loading client side certificates...");
        KeyManager[] keyManager = null;
        final String clientKeystoreLocation = config.getValue("egress.https.keystore.file");
        if (clientKeystoreLocation == null || clientKeystoreLocation.isEmpty()) return null;

        try {
            KeyStore keyStore = KeyStore.getInstance(config.getValue("egress.https.keystore.type"));
            FileInputStream fis = new FileInputStream(clientKeystoreLocation);

            keyStore.load(fis, config.getValue("egress.https.keystore.password").toCharArray());

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(keyStore, config.getValue("egress.https.keystore.alias.password").toCharArray());
            keyManager = keyManagerFactory.getKeyManagers();

            String alias = config.getValue("egress.https.keystore.alias.key");
            if (alias != null && !alias.isEmpty()) {
                for (int i = 0; i < keyManager.length; i++) {
                    if (keyManager[i] instanceof X509ExtendedKeyManager) {
                        keyManager[i] = new AliasedX509ExtendedKeyManager((X509ExtendedKeyManager) keyManager[i], alias);
                    }
                }
            }
        }
        catch (Exception e) {
            log.error("Failed loading client certificates",e);
            throw new IllegalStateException("Unable to initialize client key manager", e);
        }
        return keyManager;
    }
}
