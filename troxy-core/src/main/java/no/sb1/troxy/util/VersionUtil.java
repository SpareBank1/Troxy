package no.sb1.troxy.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class VersionUtil {

    private static final Logger log = LoggerFactory.getLogger(VersionUtil.class);


    public static String getRelease() {
        return getInternal().getProperty("release", "<unknown>");
    }

    public static String getVersion() {
        return  getInternal().getProperty("version", "<unknown>");
    }

    private static Properties getInternal() {

        Properties p = new Properties();
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("troxy_version.properties")) {
            p.load(inputStream);
        } catch (IOException e) {
            log.info("Unable to read file with Troxy version and release", e);
        }
        return p;
    }
}