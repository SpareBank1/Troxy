package no.sb1.troxy.util;

import java.beans.XMLDecoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import no.sb1.troxy.Troxy;
import no.sb1.troxy.record.v2.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for handling reading, parsing and writing recording files.

 * @deprecated Kept for supporting old recording file format, will be removed in the future
 */
@Deprecated
public class RecordingFileHandler {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(RecordingFileHandler.class);

    /**
     * Load recording.
     * @param filePath File path to be loaded.
     * @return Loaded recording.
     * @throws FileNotFoundException If the file can't be found.
     */
    public static Recording loadRecording(String filePath) throws FileNotFoundException {
        Recording recording;
        final Path path = Paths.get(filePath);
        Object deserializedFile = deserializeFile(path.toFile());
        try {
            recording = (Recording) deserializedFile;
        } catch (ClassCastException e) {
            log.info("Unable to load file as a Troxy recording (format 2)");
            return null;
        }
        recording.setFilename(path.getFileName().toString());
        if (recording.getDelayStrategy() == null)
            recording.setDelayStrategy(Recording.DelayStrategy.NONE);
        recording.getDelayValues().putIfAbsent(Recording.DelayValueKeys.MIN, 0L);
        recording.getDelayValues().putIfAbsent(Recording.DelayValueKeys.MEAN, 0L);
        recording.getDelayValues().putIfAbsent(Recording.DelayValueKeys.MAX, 0L);
        return recording;
    }

    /**
     * Deserialize file to an Object.
     * @param file File to deserialize.
     * @return File deserialized to an Object.
     * @throws FileNotFoundException If the given file can't be found.
     */
    private static Object deserializeFile(File file) throws FileNotFoundException {
        try (XMLDecoder decoder = new XMLDecoder(new FileInputStream(file))) {
            return decoder.readObject();
        }
    }
}
