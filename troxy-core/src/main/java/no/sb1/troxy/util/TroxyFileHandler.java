package no.sb1.troxy.util;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import no.sb1.troxy.http.common.Request;
import no.sb1.troxy.http.common.Response;
import no.sb1.troxy.record.v3.Recording;
import no.sb1.troxy.record.v3.RequestPattern;
import no.sb1.troxy.record.v3.ResponseTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for handling reading, parsing and writing recording files.

 */
public class TroxyFileHandler {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(TroxyFileHandler.class);
    
    private enum Tag {
        RECORDING("---RECORDING---"),
        COMMENT("[COMMENT]"),
        COMMENT_END("[COMMENT_END]"),
        RESPONSE_STRATEGY("RESPONSE_STRATEGY="),
        REQUEST("---REQUEST---"),
        PROTOCOL("PROTOCOL="),
        HOST("HOST="),
        PORT("PORT="),
        PATH("PATH="),
        QUERY("QUERY="),
        METHOD("METHOD="),
        HEADER("[HEADER]"),
        HEADER_END("[HEADER_END]"),
        CONTENT("[CONTENT]"),
        CONTENT_END("[CONTENT_END]"),
        ORIGINAL_REQUEST("---ORIGINAL_REQUEST---"),
        RESPONSE("---RESPONSE---"),
        DELAY_STRATEGY("DELAY_STRATEGY="),
        DELAY_MIN("DELAY_MIN="),
        DELAY_MEAN("DELAY_MEAN="),
        DELAY_MAX("DELAY_MAX="),
        WEIGHT("WEIGHT="),
        CODE("CODE="),
        ORIGINAL_RESPONSE("---ORIGINAL_RESPONSE---");
        
        private final String value;
        
        Tag(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private final String recordingDirectory;
    private final String loadedRecordingsFile;

    public TroxyFileHandler(final String recordingDirectory, final String loadedRecordingsFile) {
        this.recordingDirectory = recordingDirectory;
        this.loadedRecordingsFile = loadedRecordingsFile;
    }

    /**
     * Test if given path is a directory.
     * @param path Path to potential directory.
     * @return <code>true</code> if path points to a directory, <code>false</code> otherwise.
     */
    public boolean isDirectory(String path) {
        return Files.isDirectory(Paths.get(recordingDirectory, path));
    }

    /**
     * Test if file exists (used for creating new recordings).
     * @param path Path to file.
     * @return <code>true</code> if file exists, <code>false</code> otherwise.
     */
    public boolean fileExists(String path) {
        return Files.exists(Paths.get(recordingDirectory, path));
    }

    /**
     * Get all files in a given directory.
     * @param directory The directory to find files in.
     * @return A list of all files found in the given directory.
     */
    public static Set<String> getFilesInDirectory(Path directory) throws IOException {
        log.debug("Fetching files recursively from directory {}", directory);

        Set<String> files = new HashSet<>();
        Files.walkFileTree(directory, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                files.add(dir.toString().replace("\\","/") + "/");
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                files.add(file.toString().replace("\\","/"));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    /**
     * Create an empty directory.
     * @param path Path to the new directory.
     */
    public void createEmptyDirectory(String path) throws IOException {
        Files.createDirectories(Paths.get(recordingDirectory, path));
    }

    /**
     * Copy directory or recording.
     * @param path Source path.
     * @param newPath Target path.
     */
    public  void copyDirectoryOrRecording(String path, String newPath) throws IOException {
        Path source = Paths.get(recordingDirectory, path);
        Path destination = Paths.get(recordingDirectory, newPath);
        try {
            Files.walk(source).collect(Collectors.toList()).stream().forEach(p -> {
                try {
                    Files.copy(p, Paths.get(p.toString().replace(source.toString(), destination.toString())));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException)
                throw (IOException) e.getCause();
            else throw e;
        }
    }

    /**
     * Move directory or recording.
     * @param path Source path.
     * @param newPath Target path.
     */
    public void moveDirectoryOrRecording(String path, String newPath) throws IOException {
        Files.move(Paths.get(recordingDirectory, path), Paths.get(recordingDirectory, newPath));
    }

    /**
     * Delete directory or recording file.
     * @param path Path to directory or recording file.
     */
    public void deleteDirectoryOrRecording(String path) throws IOException {
        Path recordingDir = Paths.get(recordingDirectory);
        Path source = Paths.get(recordingDirectory, path);
        if (!Files.exists(source))
            return; // trying to erase file that doesn't exist

        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.debug("Deleting file: {}", file);
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (!Files.isSameFile(dir, recordingDir)) {
                    log.debug("Deleting directory: {}", dir);
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                // may happen when deleting directory and file within directory at the same time
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Get all files in the given recording directory.
     * @param directory Recording directory to search for recordings.
     * @return All files in the given recording directory.
     */
    public Set<String> getAllFilesInRecordingDir(String directory) throws IOException {
        Path recordingDir = Paths.get(recordingDirectory, directory);
        int chopChars = recordingDirectory.length() + 1;
        Set<String> recordings = getFilesInDirectory(recordingDir).stream().map(recording -> recording.substring(chopChars)).collect(Collectors.toSet());
        recordings.remove("/"); // remove root node
        return recordings;
    }

    /**
     * Store the loaded recordings to a file.
     * This is used to automatically load the same recordings when Troxy is restarted.
     * @param loadedRecordings A list of loaded recordings.
     */
    public void storeLoadedRecordings(Set<Recording> loadedRecordings) {
        File loadedRecordingsFile = new File(this.loadedRecordingsFile);
        File tmpFile = new File(loadedRecordingsFile + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tmpFile))) {
            for (Recording recording : loadedRecordings)
                writer.write(recording.getFilename() + System.lineSeparator());
            writer.close();
            Files.move(tmpFile.toPath(), loadedRecordingsFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            log.warn("Unable to store loaded recordings", e);
            try {
                if (!tmpFile.delete())
                    throw new IOException("Unable to delete file");
            } catch (Exception e2) {
                log.warn("Unable to delete temporary file for loaded recordings", e2);
            }
        }
    }

    /**
     * Save a Recording.
     * @param recording The Recording to save.
     */
    public boolean saveRecording(Recording recording) {
        log.info("Saving recording: {}", recording);
        StringBuilder sb = new StringBuilder();
        sb.append("This is a Troxy recording file.\n");
        sb.append("You can modify this file in your editor of choice, but there are some rules you must follow:\n");
        sb.append("* All fields except \"COMMENT\", \"HEADER\" and \"CONTENT\" must stay in one line.\n");
        sb.append("* Everything after \"=\" for the fields will be included (text won't be trimmed), this includes whitespace.\n");
        sb.append("* The \"[COMMENT<_END>]\", \"[HEADER<_END>]\" and \"[CONTENT<_END>]\" markers must be the only text on their lines.\n");
        sb.append("* If the comment contains \"[COMMENT_END]\", this must be escaped as \"[[COMMENT_END]]\".\n");
        sb.append("* If the header contains \"[HEADER_END]\", this must be escaped as \"[[HEADER_END]]\".\n");
        sb.append("* If the content contains \"[CONTENT_END]\", this must be escaped as \"[[CONTENT_END]]\".\n");
        sb.append("* Any text outside a field will be ignored, and erased if recording is modified in the user interface.\n");
        sb.append('\n').append(Tag.RECORDING);
        sb.append('\n').append(Tag.COMMENT).append('\n').append(recording.getComment().replace(Tag.COMMENT_END.value, "[" + Tag.COMMENT_END.value + "]")).append('\n').append(Tag.COMMENT_END);
        sb.append('\n').append(Tag.RESPONSE_STRATEGY).append(recording.getResponseStrategy());

        sb.append('\n');
        sb.append('\n').append(Tag.REQUEST);
        RequestPattern requestPattern = recording.getRequestPattern();
        sb.append('\n').append(Tag.PROTOCOL).append(requestPattern.getProtocol());
        sb.append('\n').append(Tag.HOST).append(requestPattern.getHost());
        sb.append('\n').append(Tag.PORT).append(requestPattern.getPort());
        sb.append('\n').append(Tag.PATH).append(requestPattern.getPath());
        sb.append('\n').append(Tag.QUERY).append(requestPattern.getQuery());
        sb.append('\n').append(Tag.METHOD).append(requestPattern.getMethod());
        sb.append('\n').append(Tag.HEADER).append('\n').append(requestPattern.getHeader().replace(Tag.HEADER_END.value, "[" + Tag.HEADER_END.value + "]")).append('\n').append(Tag.HEADER_END);
        sb.append('\n').append(Tag.CONTENT).append('\n').append(requestPattern.getContent().replace(Tag.CONTENT_END.value, "[" + Tag.CONTENT_END.value + "]")).append('\n').append(Tag.CONTENT_END);

        Request request = recording.getRequestPattern().getOriginalRequest();
        if (request != null) {
            sb.append('\n');
            sb.append('\n').append(Tag.ORIGINAL_REQUEST);
            sb.append('\n').append(Tag.PROTOCOL).append(request.getProtocol());
            sb.append('\n').append(Tag.HOST).append(request.getHost());
            sb.append('\n').append(Tag.PORT).append(request.getPort());
            sb.append('\n').append(Tag.PATH).append(request.getPath());
            sb.append('\n').append(Tag.QUERY).append(request.getQuery());
            sb.append('\n').append(Tag.METHOD).append(request.getMethod());
            sb.append('\n').append(Tag.HEADER).append('\n').append(request.getHeader().replace(Tag.HEADER_END.value, "[" + Tag.HEADER_END.value + "]")).append('\n').append(Tag.HEADER_END);
            sb.append('\n').append(Tag.CONTENT).append('\n').append(request.getContent().replace(Tag.CONTENT_END.value, "[" + Tag.CONTENT_END.value + "]")).append('\n').append(Tag.CONTENT_END);
        }

        for (ResponseTemplate responseTemplate : recording.getResponseTemplates()) {
            sb.append('\n');
            sb.append('\n').append(Tag.RESPONSE);
            sb.append('\n').append(Tag.DELAY_STRATEGY).append(responseTemplate.getDelayStrategy().name());
            sb.append('\n').append(Tag.DELAY_MIN).append(responseTemplate.getDelayMin());
            sb.append('\n').append(Tag.DELAY_MEAN).append(responseTemplate.getDelayMean());
            sb.append('\n').append(Tag.DELAY_MAX).append(responseTemplate.getDelayMax());
            sb.append('\n').append(Tag.WEIGHT).append(responseTemplate.getWeight());
            sb.append('\n').append(Tag.CODE).append(responseTemplate.getCode());
            sb.append('\n').append(Tag.HEADER).append('\n').append(responseTemplate.getHeader().replace(Tag.HEADER_END.value, "[" + Tag.HEADER_END.value + "]")).append('\n').append(Tag.HEADER_END);
            sb.append('\n').append(Tag.CONTENT).append('\n').append(responseTemplate.getContent().replace(Tag.CONTENT_END.value, "[" + Tag.CONTENT_END.value + "]")).append('\n').append(Tag.CONTENT_END);

            Response response = responseTemplate.getOriginalResponse();
            if (response != null) {
                sb.append('\n');
                sb.append('\n').append(Tag.ORIGINAL_RESPONSE);
                sb.append('\n').append(Tag.CODE).append(response.getCode());
                sb.append('\n').append(Tag.HEADER).append('\n').append(response.getHeader().replace(Tag.HEADER_END.value, "[" + Tag.HEADER_END.value + "]")).append('\n').append(Tag.HEADER_END);
                sb.append('\n').append(Tag.CONTENT).append('\n').append(response.getContent().replace(Tag.CONTENT_END.value, "[" + Tag.CONTENT_END.value + "]")).append('\n').append(Tag.CONTENT_END);
            }
        }

        Path path = Paths.get(recordingDirectory, recording.getFilename());
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            log.warn("Unable to create directory: " + path.getParent(), e);
        }
        try (FileOutputStream outputStream = new FileOutputStream(path.toFile());
                OutputStreamWriter streamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                BufferedWriter writer = new BufferedWriter(streamWriter)) {
            writer.append(sb);
        } catch (Exception e) {
            /* saving failed */
            log.warn("Saving recording failed (disk full? wrong permissions?): {}", path, e);
            return false;
        }
        return true;
    }



    public Recording loadRecording(String filepath) throws IOException {
        return loadRecording(recordingDirectory, filepath);
    }

    /**
     * Load a Recording.
     * @param filepath Filename of recording.
     * @return Loaded Recording.
     */
    public Recording loadRecording(String recordingDirectory2, String filepath) throws IOException {
        log.info("Loading file: {}", filepath);
        File file = Paths.get(recordingDirectory2, filepath).toFile();
        if (filepath.endsWith(".xml")) {
            log.info("Attempting to convert file in old format to new format");
            Recording recording = loadOldFormat(filepath);
            if (recording != null) {
                recording.setFilename(filepath.substring(0, filepath.length() - 4) + ".troxy");
                if (saveRecording(recording))
                    file.delete();
            }
            return recording;
        }
        Tag metaTag = null;
        Tag stopTag = null;
        StringBuilder multiLine = new StringBuilder();
        try (FileInputStream inputStream = new FileInputStream(file);
                InputStreamReader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader)) {
            Recording recording = new Recording();
            recording.setFilename(filepath);
            recording.setRequestPattern(new RequestPattern());
            String line;
            while ((line = reader.readLine()) != null) {
                if (stopTag == null) {
                    /* no stop tag defined, not parsing multiline data */
                    if (line.startsWith(Tag.RECORDING.value)) {
                        metaTag = Tag.RECORDING;
                    } else if (metaTag == Tag.RECORDING && line.startsWith(Tag.COMMENT.value)) {
                        multiLine.setLength(0);
                        stopTag = Tag.COMMENT_END;
                    } else if (metaTag == Tag.RECORDING && line.startsWith(Tag.RESPONSE_STRATEGY.value)) {
                        recording.setResponseStrategy(Recording.ResponseStrategy.valueOf(line.substring(Tag.RESPONSE_STRATEGY.value.length())));
                    } else if (line.startsWith(Tag.REQUEST.value)) {
                        metaTag = Tag.REQUEST;
                    } else if (line.startsWith(Tag.PROTOCOL.value)) {
                        String protocol = line.substring(Tag.PROTOCOL.value.length());
                        if (metaTag == Tag.REQUEST) {
                            recording.getRequestPattern().setProtocol(protocol);
                        } else if (metaTag == Tag.ORIGINAL_REQUEST) {
                            recording.getRequestPattern().getOriginalRequest().setProtocol(protocol);
                        }
                    } else if (line.startsWith(Tag.HOST.value)) {
                        String host = line.substring(Tag.HOST.value.length());
                        if (metaTag == Tag.REQUEST) {
                            recording.getRequestPattern().setHost(host);
                        } else if (metaTag == Tag.ORIGINAL_REQUEST) {
                            recording.getRequestPattern().getOriginalRequest().setHost(host);
                        }
                    } else if (line.startsWith(Tag.PORT.value)) {
                        String port = line.substring(Tag.PORT.value.length());
                        if (metaTag == Tag.REQUEST) {
                            recording.getRequestPattern().setPort(port);
                        } else if (metaTag == Tag.ORIGINAL_REQUEST) {
                            recording.getRequestPattern().getOriginalRequest().setPort(port);
                        }
                    } else if (line.startsWith(Tag.PATH.value)) {
                        String path = line.substring(Tag.PATH.value.length());
                        if (metaTag == Tag.REQUEST) {
                            recording.getRequestPattern().setPath(path);
                        } else if (metaTag == Tag.ORIGINAL_REQUEST) {
                            recording.getRequestPattern().getOriginalRequest().setPath(path);
                        }
                    } else if (line.startsWith(Tag.QUERY.value)) {
                        String query = line.substring(Tag.QUERY.value.length());
                        if (metaTag == Tag.REQUEST) {
                            recording.getRequestPattern().setQuery(query);
                        } else if (metaTag == Tag.ORIGINAL_REQUEST) {
                            recording.getRequestPattern().getOriginalRequest().setQuery(query);
                        }
                    } else if (line.startsWith(Tag.METHOD.value)) {
                        String method = line.substring(Tag.METHOD.value.length());
                        if (metaTag == Tag.REQUEST) {
                            recording.getRequestPattern().setMethod(method);
                        } else if (metaTag == Tag.ORIGINAL_REQUEST) {
                            recording.getRequestPattern().getOriginalRequest().setMethod(method);
                        }
                    } else if (line.equals(Tag.HEADER.value)) {
                        multiLine.setLength(0);
                        stopTag = Tag.HEADER_END;
                    } else if (line.equals(Tag.CONTENT.value)) {
                        multiLine.setLength(0);
                        stopTag = Tag.CONTENT_END;
                    } else if (line.startsWith(Tag.ORIGINAL_REQUEST.value)) {
                        metaTag = Tag.ORIGINAL_REQUEST;
                        recording.getRequestPattern().setOriginalRequest(new Request());
                    } else if (line.startsWith(Tag.RESPONSE.value)) {
                        recording.getResponseTemplates().add(new ResponseTemplate());
                        metaTag = Tag.RESPONSE;
                    } else if (metaTag == Tag.RESPONSE && line.startsWith(Tag.DELAY_STRATEGY.value)) {
                        recording.getResponseTemplates().get(recording.getResponseTemplates().size() - 1).setDelayStrategy(ResponseTemplate.DelayStrategy.valueOf(line.substring(Tag.DELAY_STRATEGY.value.length())));
                    } else if (metaTag == Tag.RESPONSE && line.startsWith(Tag.DELAY_MIN.value)) {
                        recording.getResponseTemplates().get(recording.getResponseTemplates().size() - 1).setDelayMin(Long.parseLong(line.substring(Tag.DELAY_MIN.value.length())));
                    } else if (metaTag == Tag.RESPONSE && line.startsWith(Tag.DELAY_MEAN.value)) {
                        recording.getResponseTemplates().get(recording.getResponseTemplates().size() - 1).setDelayMean(Long.parseLong(line.substring(Tag.DELAY_MEAN.value.length())));
                    } else if (metaTag == Tag.RESPONSE && line.startsWith(Tag.DELAY_MAX.value)) {
                        recording.getResponseTemplates().get(recording.getResponseTemplates().size() - 1).setDelayMax(Long.parseLong(line.substring(Tag.DELAY_MAX.value.length())));
                    } else if (metaTag == Tag.RESPONSE && line.startsWith(Tag.WEIGHT.value)) {
                        recording.getResponseTemplates().get(recording.getResponseTemplates().size() - 1).setWeight(Long.parseLong(line.substring(Tag.WEIGHT.value.length())));
                    } else if (line.startsWith(Tag.CODE.value)) {
                        String code = line.substring(Tag.CODE.value.length());
                        if (metaTag == Tag.RESPONSE) {
                            recording.getResponseTemplates().get(recording.getResponseTemplates().size() - 1).setCode(code);
                        } else if (metaTag == Tag.ORIGINAL_RESPONSE) {
                            recording.getResponseTemplates().get(recording.getResponseTemplates().size() - 1).getOriginalResponse().setCode(code);
                        }
                    } else if (line.startsWith(Tag.ORIGINAL_RESPONSE.value)) {
                        recording.getResponseTemplates().get(recording.getResponseTemplates().size() - 1).setOriginalResponse(new Response());
                        metaTag = Tag.ORIGINAL_RESPONSE;
                    }
                } else {
                    /* parsing multiline data, add to buffer until stop tag */
                    if (line.equals(stopTag.value)) {
                        if (multiLine.length() > 0)
                            multiLine.deleteCharAt(multiLine.length() - 1); // remove trailing newline
                        if (stopTag == Tag.COMMENT_END) {
                            recording.setComment(multiLine.toString().replace("[" + Tag.COMMENT_END.value + "]", Tag.COMMENT_END.value));
                        } else if (stopTag == Tag.HEADER_END) {
                            String header = multiLine.toString().replace("[" + Tag.HEADER_END.value + "]", Tag.HEADER_END.value);
                            if (metaTag == Tag.REQUEST) {
                                recording.getRequestPattern().setHeader(header);
                            } else if (metaTag == Tag.ORIGINAL_REQUEST) {
                                recording.getRequestPattern().getOriginalRequest().setHeader(header);
                            } else if (metaTag == Tag.RESPONSE) {
                                recording.getResponseTemplates().get(recording.getResponseTemplates().size() - 1).setHeader(header);
                            } else if (metaTag == Tag.ORIGINAL_RESPONSE) {
                                recording.getResponseTemplates().get(recording.getResponseTemplates().size() - 1).getOriginalResponse().setHeader(header);
                            }
                        } else if (stopTag == Tag.CONTENT_END) {
                            String content = multiLine.toString().replace("[" + Tag.CONTENT_END.value + "]", Tag.CONTENT_END.value);
                            if (metaTag == Tag.REQUEST) {
                                recording.getRequestPattern().setContent(content);
                            } else if (metaTag == Tag.ORIGINAL_REQUEST) {
                                recording.getRequestPattern().getOriginalRequest().setContent(content);
                            } else if (metaTag == Tag.RESPONSE) {
                                recording.getResponseTemplates().get(recording.getResponseTemplates().size() - 1).setContent(content);
                            } else if (metaTag == Tag.ORIGINAL_RESPONSE) {
                                recording.getResponseTemplates().get(recording.getResponseTemplates().size() - 1).getOriginalResponse().setContent(content);
                            }
                        }
                        stopTag = null;
                    } else {
                        multiLine.append(line).append('\n');
                    }
                }
            }
            return recording;
        } catch (Exception e) {
            /* loading failed */
            log.warn("Loading file failed (corrupt file?): {}", file, e);
        }
        return null;
    }

    /**
     * Fetch a raw file from the recording directory.
     * @param filepath Path to file.
     * @return File bytes.
     */
    public byte[] readRawFile(String filepath) throws IOException {
        return Files.readAllBytes(Paths.get(recordingDirectory, filepath));
    }

    /**
     * Fetch a zip of files and/or directories.
     * @param paths Paths to files and/or directories.
     * @return A zip file.
     */
    public byte[] createZipFile(Set<String> paths) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                BufferedOutputStream bos = new BufferedOutputStream(baos);
                ZipOutputStream zos = new ZipOutputStream(bos)) {
            Set<String> alreadyAdded = new HashSet<>();
            for (String path : paths) {
                if (isDirectory(path)) {
                    Set<String> files = getAllFilesInRecordingDir(path);
                    for (String file : files) {
                        if (isDirectory(file) || alreadyAdded.contains(file))
                            continue;
                        zos.putNextEntry(new ZipEntry(file));
                        zos.write(readRawFile(file));
                        zos.closeEntry();
                        alreadyAdded.add(file);
                    }
                } else if (fileExists(path)) {
                    if (alreadyAdded.contains(path))
                        continue;
                    zos.putNextEntry(new ZipEntry(path));
                    zos.write(readRawFile(path));
                    zos.closeEntry();
                    alreadyAdded.add(path);
                } else {
                    log.warn("Unable to find file/directory: " + path);
                }
            }
            zos.close();
            bos.close();
            return baos.toByteArray();
        }
    }

    public void writeRawFile(String targetDir, String filename, InputStream inputStream) throws IOException {
        Path path = Paths.get(recordingDirectory, targetDir, filename);
        Files.createDirectories(path.getParent());
        byte[] buffer = new byte[4096];
        int len;
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            while ((len = inputStream.read(buffer)) > 0)
                fos.write(buffer, 0, len);
        }
    }

    public void unpackZipFile(String targetDir, InputStream inputStream) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    Path path = Paths.get(recordingDirectory, targetDir, entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(path);
                    } else {
                        Files.createDirectories(path.getParent());
                        byte[] buffer = new byte[4096];
                        int len;
                        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                            while ((len = zis.read(buffer)) > 0)
                                fos.write(buffer, 0, len);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed creating path, skipping file/directory: {}", entry.getName(), e);
                }
            }
        }
    }

    /**
     * Load Recording in old file format.
     * @param filename Filename of recording.
     * @return Loaded Recording.
     * @deprecated Old format, this method should be removed in the future
     */
    @Deprecated
    private static Recording loadOldFormat(String filename) throws IOException {
        no.sb1.troxy.record.v2.Recording recording = RecordingFileHandler.loadRecording(filename);
        if (recording == null)
            return null;

        // convert to new format
        recording.setFilename(filename.substring(0, filename.length() - 4) + ".troxy");

        RequestPattern v3RequestPattern = new RequestPattern();
        no.sb1.troxy.record.v2.RequestPattern v2RequestPattern = recording.getRequestPattern();
        v3RequestPattern.setProtocol(v2RequestPattern.getProtocol());
        v3RequestPattern.setHost(v2RequestPattern.getHost());
        v3RequestPattern.setPort(v2RequestPattern.getPort());
        v3RequestPattern.setPath(v2RequestPattern.getPath());
        v3RequestPattern.setQuery(v2RequestPattern.getQuery());
        v3RequestPattern.setMethod(v2RequestPattern.getMethod());
        v3RequestPattern.setHeader(v2RequestPattern.getHeader());
        v3RequestPattern.setContent(v2RequestPattern.getContent());
        v3RequestPattern.setOriginalRequest(v2RequestPattern.getOriginalRequest());

        List<ResponseTemplate> v3ResponseTemplates = new ArrayList<>();
        for (no.sb1.troxy.record.v2.ResponseTemplate v2ResponseTemplate : recording.getResponseTemplates()) {
            ResponseTemplate v3ResponseTemplate = new ResponseTemplate();
            v3ResponseTemplate.setCode(v2ResponseTemplate.getCode());
            v3ResponseTemplate.setHeader(v2ResponseTemplate.getHeader());
            v3ResponseTemplate.setContent(v2ResponseTemplate.getContent());
            v3ResponseTemplate.setOriginalResponse(v2ResponseTemplate.getOriginalResponse());
            v3ResponseTemplate.setDelayStrategy(ResponseTemplate.DelayStrategy.valueOf(recording.getDelayStrategy().name()));
            v3ResponseTemplate.setDelayMin(recording.getDelayValues().get(no.sb1.troxy.record.v2.Recording.DelayValueKeys.MIN));
            v3ResponseTemplate.setDelayMean(recording.getDelayValues().get(no.sb1.troxy.record.v2.Recording.DelayValueKeys.MEAN));
            v3ResponseTemplate.setDelayMax(recording.getDelayValues().get(no.sb1.troxy.record.v2.Recording.DelayValueKeys.MAX));
            v3ResponseTemplates.add(v3ResponseTemplate);
        }

        Recording v3Recording = new Recording();
        v3Recording.setResponseStrategy(Recording.ResponseStrategy.SEQUENTIAL);
        v3Recording.setRequestPattern(v3RequestPattern);
        v3Recording.setResponseTemplates(v3ResponseTemplates);
        return v3Recording;
    }
}
