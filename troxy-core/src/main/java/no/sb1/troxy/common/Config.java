package no.sb1.troxy.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper class handling configuration parameters.

 */
public final class Config {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(Config.class);
    /**
     * Properties object storing the actual configuration.
     */
    private final Map<String, String> SETTINGS = new HashMap<>();
    /**
     * Pattern for file inclusion.
     */
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*@include\\s*([^\n]+)\\s*$");

    private Path path;

    /**
     * Private constructor to prevent instantiation.
     */
    private Config(final Path path) {
        this.path = path;
        reload();
    }

    public Config(Map<String, String> m) {
        SETTINGS.putAll(m);
    }

    public String getConfigFile() {
        return path.toAbsolutePath().toString();
    }

    /**
     * Fetch all key/value pairs in the configuration.
     * @return All key/value pairs in the configuration.
     */
    public Map<String, String> getKeysAndValues() {
        return getKeysAndValues("");
    }

    /**
     * Fetch all key/value pairs in the configuration where key begins with the given prefix.
     * @param prefix Prefix for keys in configuration.
     * @return All key/value pairs in the configuration where key begins with the given prefix.
     */
    public  Map<String, String> getKeysAndValues(String prefix) {
        log.debug("Requesting all keys & values for key names starting with \"{}\"", prefix);
        Map<String, String> entries = new HashMap<>();
        for (Map.Entry<String, String> entry : SETTINGS.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix))
                entries.put(key, entry.getValue());
        }
        return entries;
    }

    /**
     * Get the value for the given key, setting it to <code>null</code> if key is not found.
     * @param key The key for the value.
     * @return The value for the key, <code>null</code> if key was not found.
     */
    public  String getValue(String key) {
        if (!SETTINGS.containsKey(key)) {
            log.info("Requested key \"{}\" not found in configuration, returning null", key);
            return null;
        }
        return SETTINGS.get(key);
    }

    /**
     * Get the value for the given key, setting it to <code>defaultValue</code> if key is not found.
     * @param key The key for the value.
     * @param defaultValue The value to set if key is not found.
     * @return The value for the key, defaultValue if key was not found.
     */
    public  String getValue(String key, String defaultValue) {
        if (!SETTINGS.containsKey(key)) {
            log.debug("Requested key \"{}\" not found in configuration, adding it with value \"{}\"", key, defaultValue);
            SETTINGS.put(key, defaultValue);
            return defaultValue;
        }
        return SETTINGS.get(key);
    }

    /**
     * Load the properties from the configuration file.
     */
    public void reload() {
        log.debug("Reading configuration");
        Properties newSettings = new Properties();
        try {
            newSettings.load(readPropertiesFile(path, new HashSet<>()));
        } catch (IOException e) {
            log.warn("Unable to load properties from file {}", path, e);
            return;
        }
        /* mark existing configuration values and forget them if they're not set in new configuration */
        Set<String> oldKeys = new HashSet<>(SETTINGS.keySet());
        for (String key : newSettings.stringPropertyNames()) {
            /* update properties */
            String value = newSettings.getProperty(key) == null ? "" : newSettings.getProperty(key).trim();
            if (SETTINGS.containsKey(key)) {
                String oldValue = SETTINGS.get(key);
                /* key already exist, but has the value changed? */
                if (!value.equals(oldValue))
                    log.info("Key \"{}\" with value \"{}\" was updated to new value \"{}\"", key, oldValue, value);
            } else {
                /* new key/value pair */
                log.info("Key \"{}\" with value \"{}\" was added", key, value);
            }
            SETTINGS.put(key, value);
            /* remove key from oldKeys */
            oldKeys.remove(key);
        }
        for (String key : oldKeys) {
            log.info("Key \"{}\" with value \"{}\" was removed", key, SETTINGS.get(key));
            SETTINGS.remove(key);
        }
    }

    private static InputStream readPropertiesFile(Path path, Set<Path> alreadyIncluded) throws IOException {
        alreadyIncluded.add(path);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Files.lines(path).forEachOrdered(line -> {
                try {
                    Matcher matcher = INCLUDE_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        Path includePath = path.getParent();
                        String includeFile = matcher.group(1);
                        includePath = includePath == null ? Paths.get(includeFile) : includePath.resolve(includeFile);
                        if (!alreadyIncluded.contains(includePath)) {
                            log.info("Including properties file {}", includePath);
                            InputStream is = readPropertiesFile(includePath, alreadyIncluded);
                            int read;
                            byte[] data = new byte[4096];
                            while ((read = is.read(data)) >= 0)
                                baos.write(data, 0, read);
                        }
                    } else {
                        baos.write((line + "\n").getBytes());
                    }
                } catch (IOException e) {
                    log.warn("Unable to read properties file {}", path, e);
                }
            });
            return new ByteArrayInputStream(baos.toByteArray());
        }
    }

    public static Config load(final Path path) {
        return new Config(path);
    }
}
