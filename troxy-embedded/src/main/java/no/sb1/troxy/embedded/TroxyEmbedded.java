package no.sb1.troxy.embedded;

import no.sb1.troxy.common.Config;
import no.sb1.troxy.common.Mode;
import no.sb1.troxy.http.common.Filter;
import no.sb1.troxy.jetty.TroxyJettyServer;
import no.sb1.troxy.util.*;
import org.eclipse.jetty.server.handler.HandlerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.lang.String.format;


public class TroxyEmbedded {

    private static final Logger log = LoggerFactory.getLogger(TroxyEmbedded.class);




    /**
     *
     * Starts Troxy for embedded use. When this function returns is Troxy up and ready to receive network traffic
     *
     */
    public static TroxyJettyServer runTroxyEmbedded(List<String> recordingFiles, int port)  {
        long t = System.currentTimeMillis();

        /* set log level */
        //   Configurator.setRootLevel(Level.ALL);
        log.info("Troxy starting...");

        TroxyFileHandler troxyFileHandler = new TroxyFileHandler(null, null);
        Cache cache = createCache(recordingFiles, troxyFileHandler);

        TroxyJettyServer.TroxyJettyServerConfig.TroxyJettyServerConfigBuilder builder = new TroxyJettyServer.TroxyJettyServerConfig.TroxyJettyServerConfigBuilder();
        builder.setPort(port);

        TroxyJettyServer server = new TroxyJettyServer(builder.createTroxyJettyServerConfig());
        //TODO ? loadFilters();

        HandlerList handlerList = getHandlerList(cache,  new Config(new HashMap<>()), troxyFileHandler);
        server.setHandler(handlerList);

        try {
            server.jettyServer.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        long timeout = System.currentTimeMillis() + 30000;
        while (!server.jettyServer.isStarted()) {
            try {
                Thread.sleep(100);
                System.out.println(".");
            } catch (InterruptedException e) {
                // no-op
            }
            if (System.currentTimeMillis() > timeout) {
                throw new RuntimeException("Server took too long to start up.");
            }
        }

        log.info(format("Troxy has started up in embedded mode. Startup time: %d ms", System.currentTimeMillis()-t));

        return server;
    }

    private static Cache createCache(List<String> recordingsFiles, TroxyFileHandler troxyFileHandler) {
        Cache cache = Cache.createCacheRoot();
        Cache.loadRecordingsWithPaths(cache, troxyFileHandler, new HashSet<>(recordingsFiles));

        return cache;
    }


    private static HandlerList getHandlerList(Cache cache, Config config, TroxyFileHandler troxyFileHandler) {
        HandlerList handlerList = new HandlerList();
        // a handler working like an interceptor to store recent requests sent to the server
        RequestInterceptor requestInterceptor = new RequestInterceptor();
        handlerList.addHandler(requestInterceptor);


        // and finally the simulator handler
        SimulatorHandler simulatorHandler = new SimulatorHandler(
                new ModeHolder(Mode.PLAYBACK),
                new ArrayList<>() /*  (no filter classes?) */,
                config,
                troxyFileHandler,
                cache);
        handlerList.addHandler(simulatorHandler);
        return handlerList;
    }

//    /**
//     * Load filterClasses.
//     */
//    private void loadFilters() {
//        log.info("Loading filters from {}", filterDirectory);
//        Set<String> files = new HashSet<>();
//        try {
//            files = TroxyFileHandler.getFilesInDirectory(Paths.get(filterDirectory));
//        } catch (IOException e) {
//            log.warn("Unable to read files from directory: {}", filterDirectory, e);
//        }
//        filterClasses.clear();
//        for (String filename : files) {
//            if (!filename.endsWith(".jar")) {
//                log.info("Skipping file (unknown type): {}", filename);
//                continue;
//            }
//            try {
//                URL jarFile = new URL("jar", "", "file:" + filename + "!/");
//                URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{jarFile});
//                ZipFile zipFile = new ZipFile(new File(filename));
//                Enumeration<? extends ZipEntry> entries = zipFile.entries();
//                while (entries.hasMoreElements()) {
//                    ZipEntry entry = entries.nextElement();
//                    String entryName = entry.getName();
//                    if (entry.isDirectory() || !entryName.endsWith(".class") || entryName.contains("$"))
//                        continue;
//                    try {
//                        entryName = entryName.substring(0, entryName.lastIndexOf('.'));
//                        entryName = entryName.replace('/', '.');
//                        Class<Filter> filterClass = (Class<Filter>) classLoader.loadClass(entryName);
//                        /* force filterClasses to [re]load configuration */
//                        Filter filter = filterClass.newInstance();
//                        filter.reload(config);
//                        if (filter.isEnabled()) {
//                            filterClasses.add(filterClass);
//                            log.info("Loaded filter {}", entryName);
//                        } else {
//                            log.info("Not loading filter {} as it's disabled", entryName);
//                        }
//                    } catch (ClassNotFoundException e) {
//                        log.warn("Unable to load file \"{}\" as a class", entryName, e);
//                    } catch (InstantiationException e) {
//                        log.warn("Unable to instantiate filter \"{}\"", entryName, e);
//                    } catch (IllegalAccessException e) {
//                        log.warn("Unable to access filter \"{]\"", entryName, e);
//                    }
//                }
//            } catch (MalformedURLException e) {
//                log.warn("Unable to locate file \"{}\"", filename, e);
//            } catch (IOException e) {
//                log.warn("Unable to read file \"{}\" as a jar file", filename, e);
//            }
//        }
//        Collections.sort(filterClasses, (o1, o2) -> o1.getName().compareTo(o2.getName()));
//        log.info("Filters will be executed in this order: {}", filterClasses.stream().map(Class::getName).collect(Collectors.joining(", ")));
//    }
}
