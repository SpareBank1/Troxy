package no.sb1.troxy.rest;

import no.sb1.troxy.Troxy;
import no.sb1.troxy.common.Config;
import no.sb1.troxy.http.common.Request;
import no.sb1.troxy.record.v3.Recording;
import no.sb1.troxy.record.v3.RequestPattern;
import no.sb1.troxy.util.*;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * Rest API for configuring and controlling Troxy.
 */
@Singleton
@Path("/")
public class ApiHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiHandler.class);

    private static final int MAX_LOG_LINES = 2000;
    private static final Pattern LOGFILE_PATTERN = Pattern.compile("simulator.log.*");
    private final Troxy troxy;
    private final Config config;
    private final StatisticsCollector statisticsCollector;
    private final TroxyFileHandler troxyFileHandler;
    private final Cache cache;

    @Inject
    public ApiHandler(final Troxy troxy, final Config config, final StatisticsCollector statisticsCollector,
                      final TroxyFileHandler troxyFileHandler, final Cache cache) {
        this.troxy = troxy;
        this.config = config;
        this.statisticsCollector = statisticsCollector;
        this.troxyFileHandler = troxyFileHandler;
        this.cache = cache;
    }

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("mode", troxy.getMode());
        status.put("version", VersionUtil.getVersion());
        status.put("release", VersionUtil.getRelease());
        status.put("statisticsInterval", statisticsCollector.getStatisticsInterval());
        Map<String, Date> lastUsers = new HashMap<>();
        RequestInterceptor.getLastUsers().entrySet().stream()
                .filter(lastUser -> lastUser.getValue().getTime() + 86400000 > System.currentTimeMillis())
                .forEach(lastUser -> lastUsers.put(lastUser.getKey(), lastUser.getValue()));
        status.put("activity", lastUsers);
        return status;
    }

    @PUT
    @Path("status/mode")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> setMode(String mode) throws IOException {
        troxy.setMode(mode);
        return getStatus();
    }

    @PUT
    @Path("status/statisticsInterval")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> setStatisticsInterval(String statisticsInterval) throws IOException {
        statisticsCollector.setStatisticsInterval(Integer.parseInt(statisticsInterval));
        return getStatus();
    }

    @GET
    @Path("recordings")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Boolean> getRecordings() throws IOException {
        // fetch all available recordings into map, mark all as deactivated
        Map<String, Boolean> recordings = troxyFileHandler.getAllFilesInRecordingDir("/").stream().collect(Collectors.toMap(String::toString, s -> false));
        // update map with activated recordings and directories
        cache.getRecordings().stream().map(Recording::getFilename).collect(Collectors.toSet()).stream().forEach(path -> {
            // mark recording as activated
            recordings.put(path, true);
            // mark parent directories as activated
            int pos = path.length();
            while ((pos = path.lastIndexOf('/', pos - 1)) > 0)
                recordings.put(path.substring(0, pos + 1), true);
        });
        return recordings;
    }

    @PUT
    @Path("recordings")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Integer> setRecordings(Map<String, Boolean> recordings) throws IOException {
        // find actual files
        Map<String, Boolean> actualFiles = new HashMap<>();
        recordings.entrySet().stream().forEach(entry -> {
            String path = entry.getKey();
            if (troxyFileHandler.isDirectory(path)) {
                try {
                    troxyFileHandler.getAllFilesInRecordingDir(path).stream().forEach(filename -> {
                        if (!troxyFileHandler.isDirectory(filename))
                            actualFiles.put(filename, entry.getValue());
                    });
                } catch (IOException e) {
                    log.warn("Problems encountered when traversing directory {}", path, e);
                }
            } else {
                actualFiles.put(path, entry.getValue());
            }
        });
        // find activated recordings
        Set<String> recordingFiles = cache.getRecordings().stream().map(Recording::getFilename).collect(Collectors.toSet());
        // add/remove activated/deactivated recordings
        for (Map.Entry<String, Boolean> file : actualFiles.entrySet()) {
            if (file.getValue())
                recordingFiles.add(file.getKey());
            else
                recordingFiles.remove(file.getKey());
        }
        // recordingFiles now contains all the recordings we want activated, clear cache and load in the recordings
        cache.clear();
        Cache.loadRecordings(cache, troxyFileHandler, recordingFiles);
        Map<String, Boolean> loadedRecordings = getRecordings();
        Map<String, Integer> result = new HashMap<>();
        for (String file : actualFiles.keySet()) {
            if (file.endsWith(".xml"))
                file = file.replaceAll("xml$", "troxy");
            if (loadedRecordings.containsKey(file))
                result.compute(loadedRecordings.get(file) ? "loaded" : "skipped", (s, integer) -> integer == null ? 1 : integer + 1);
        }
        return result;
    }

    @GET
    @Path("recordings/{recordingFile: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Recording getRecordingFile(@PathParam("recordingFile") String recordingFile) throws IOException {
        return troxyFileHandler.loadRecording(recordingFile);
    }

    @POST
    @Path("recordings/{path: .*}")
    public void createDirectoryOrRecording(@PathParam("path") String path) throws IOException {
        if (path.endsWith(".troxy")) {
            // create empty recording
            Recording emptyRecording = Recording.createEmptyRecording();
            emptyRecording.setFilename(path);
            troxyFileHandler.saveRecording(emptyRecording);
        } else {
            // create directory
            troxyFileHandler.createEmptyDirectory(path);
        }
    }

    @PUT
    @Path("recordings/{recordingFile: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Long> saveRecording(@PathParam("recordingFile") String recordingFile, Recording recording) throws IOException {
        recording.setFilename(recordingFile);

        if (!troxyFileHandler.saveRecording(recording))
            throw new IllegalArgumentException("Unable to save file, see log for details");

        boolean reload = false;
        Set<String> reloadRecordings = new HashSet<>();
        for (Recording loadedRecording : cache.getRecordings()) {
            if (recordingFile.equals(loadedRecording.getFilename()))
                reload = true;
            reloadRecordings.add(loadedRecording.getFilename());
        }
        if (reload) {
            cache.clear();
            Cache.loadRecordings(cache, troxyFileHandler, reloadRecordings);
        }

        Map<String, Long> matchResult = new HashMap<>();
        matchResult.put("match_status", (long) -2);
        RequestPattern requestPattern = recording.getRequestPattern();
        if (requestPattern == null)
            return matchResult;
        matchResult.put("match_status", (long) -1);
        Request originalRequest = requestPattern.getOriginalRequest();
        if (originalRequest == null)
            return matchResult;
        matchResult.put("match_status", (long) 0);
        matchResult.put("protocol", getRegexMatchTime(requestPattern.getProtocol(), originalRequest.getProtocol()));
        matchResult.put("host", getRegexMatchTime(requestPattern.getHost(), originalRequest.getHost()));
        matchResult.put("port", getRegexMatchTime(requestPattern.getPort(), originalRequest.getPort()));
        matchResult.put("path", getRegexMatchTime(requestPattern.getPath(), originalRequest.getPath()));
        matchResult.put("query", getRegexMatchTime(requestPattern.getQuery(), originalRequest.getQuery()));
        matchResult.put("method", getRegexMatchTime(requestPattern.getMethod(), originalRequest.getMethod()));
        matchResult.put("header", getRegexMatchTime(requestPattern.getHeader(), originalRequest.getHeader()));
        matchResult.put("content", getRegexMatchTime(requestPattern.getContent(), originalRequest.getContent()));
        return matchResult;
    }

    @DELETE
    @Path("recordings/{path: .*}")
    public void deleteRecording(@PathParam("path") String path) throws IOException {
        troxyFileHandler.deleteDirectoryOrRecording(path);
        setRecordings(cache.getRecordings().stream().collect(Collectors.toMap(Recording::getFilename, recording -> troxyFileHandler.fileExists(recording.getFilename()))));
    }

    @POST
    @Path("recordings_copy/{path: .*}")
    public void copyDirectoryOrRecording(@PathParam("path") String path, String newPath) throws IOException {
        troxyFileHandler.copyDirectoryOrRecording(path, newPath);
    }

    @POST
    @Path("recordings_move/{path: .*}")
    public void moveDirectoryOrRecording(@PathParam("path") String path, String newPath) throws IOException {
        troxyFileHandler.moveDirectoryOrRecording(path, newPath);
    }

    @GET
    @Path("log")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getLog() {
        File logDir = new File(troxy.logDirectory);
        List<String> files = new ArrayList<>();
        if (logDir.exists())
            Collections.addAll(files, logDir.list((dir, name) -> LOGFILE_PATTERN.matcher(name).matches() && (new File(dir, name)).isFile()));
        return files;
    }

    @GET
    @Path("log/{logfile}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getLogfile(@PathParam("logfile") String logfile) throws IOException {
        return getLogfile(logfile, MAX_LOG_LINES);
    }

    @GET
    @Path("log/{logfile}/{limit}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getLogfile(@PathParam("logfile") String logfile, @PathParam("limit") int limit) throws IOException {
        return getLogfile(logfile, limit, -1);
    }

    @GET
    @Path("log/{logfile}/{limit}/{start}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getLogfile(@PathParam("logfile") String logfile, @PathParam("limit") int limit, @PathParam("start") int start) throws IOException {
        try {
            return getLogData(logfile, limit, start, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return getLogData(logfile, limit, start, StandardCharsets.ISO_8859_1);
        }
    }

    @GET
    @Path("healthcheck")
    @Produces(MediaType.TEXT_PLAIN)
    public String healthcheck() throws IOException {
        return "OK";
    }

    @GET
    @Path("configuration")
    @Produces(MediaType.TEXT_PLAIN)
    public String getConfiguration() throws IOException {
        return new String(Files.readAllBytes(Paths.get(config.getConfigFile())), StandardCharsets.UTF_8);
    }

    @PUT
    @Path("configuration")
    @Produces(MediaType.TEXT_PLAIN)
    public String setConfiguration(String data) throws IOException {
        Files.write(Paths.get(config.getConfigFile()), data.getBytes());
        troxy.reconfigure();
        return getConfiguration();
    }

    @GET
    @Path("statistics")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getStatistics() {
        File statisticsDir = statisticsCollector.getStatisticsDirectory();
        List<String> files = new ArrayList<>();
        if (statisticsDir.exists())
            Collections.addAll(files, statisticsDir.list((dir, name) -> (new File(dir, name)).isFile()));
        return files;
    }

    @GET
    @Path("statistics/current")
    @Produces(MediaType.TEXT_PLAIN)
    public String getCurrentStatistics() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Filename, Total response count since loaded, Response count since last statistics").append(System.lineSeparator());
        for (Recording recording : cache.getRecordings()) {
            sb.append(recording.getFilename()).append(", ")
                    .append(recording.getResponseCounterTotal()).append(", ")
                    .append(recording.getResponseCounterCurrent())
                    .append(System.lineSeparator());
        }
        return sb.toString();
    }

    @GET
    @Path("statistics/totals/recording")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Integer> getRequestCounterPerRecording() {
        return cache.getRequestCounterPerRecording();
    }

    @GET
    @Path("statistics/totals/path")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Integer> getRequestCounterPerPath() {
        return cache.getRequestCounterPerPath();
    }

    @POST
    @Path("statistics/totals/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public void resetTotalStatisticCounter() {
        cache.resetTotalStatisticCounter();
    }

    @GET
    @Path("statistics/{statisticsfile}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getStatisticsfile(@PathParam("statisticsfile") String statisticsfile) throws IOException {
        return new String(Files.readAllBytes(Paths.get(statisticsCollector.getStatisticsDirectory().getPath(), statisticsfile)), StandardCharsets.UTF_8);
    }

    @POST
    @Path("download")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@FormParam("paths") Set<String> paths) throws IOException {
        if (paths.isEmpty())
            return Response.status(Response.Status.BAD_REQUEST).build();
        String filename = paths.iterator().next();
        if (paths.size() == 1 && !troxyFileHandler.isDirectory(filename)) {
            return Response.ok(troxyFileHandler.readRawFile(filename)).header("Content-Disposition", "attachment; filename=" + filename.substring(filename.lastIndexOf('/') + 1)).build();
        } else {
            return Response.ok(troxyFileHandler.createZipFile(paths)).header("Content-Disposition", "attachment; filename=recordings-" + new SimpleDateFormat("yyyyMMddHHmm").format(new Date()) + ".zip").build();
        }
    }

    @POST
    @Path("upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(@FormDataParam("directory") String directory, @FormDataParam("file") FormDataContentDisposition fileDetails, @FormDataParam("file") InputStream fileStream) throws IOException {
        if (fileDetails.getFileName().toLowerCase().endsWith(".zip")) {
            troxyFileHandler.unpackZipFile(directory, fileStream);
        } else {
            troxyFileHandler.writeRawFile(directory, fileDetails.getFileName(), fileStream);
        }
        return Response.ok().build();
    }


    private Map<String, Object> getLogData(String logfile, int limit, int start, Charset charset) throws IOException {
        if (!LOGFILE_PATTERN.matcher(logfile).matches())
            throw new IllegalArgumentException("Only \"simulator.log\" files may be read");
        if (start < 0)
            start = Integer.MAX_VALUE - MAX_LOG_LINES;
        limit = limit > MAX_LOG_LINES ? MAX_LOG_LINES : limit;
        int index = 0;
        int stopIndex = start + limit;
        List<String> lines = new LinkedList<>();
        try (FileInputStream fis = new FileInputStream(Paths.get(troxy.logDirectory, logfile).toFile());
             InputStream is = logfile.endsWith(".gz") ? new GZIPInputStream(fis) : fis;
             InputStreamReader isr = new InputStreamReader(is, charset);
             BufferedReader reader = new BufferedReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (++index <= stopIndex)
                    lines.add(line);
                if (lines.size() > limit)
                    lines.remove(0);
            }
        } catch (FileNotFoundException e) {
            // no logfile present
        }
        Map<String, Object> logData = new HashMap<>();
        logData.put("offset", start + limit > index ? index - limit : start);
        logData.put("limit", limit);
        logData.put("total", index);
        logData.put("lines", lines);
        return logData;
    }

    private long getRegexMatchTime(String regex, String text) {
        if (regex == null || text == null)
            return -2;
        Pattern pattern; // pattern is compiled & cached in server, should not add this to the matching time
        try {
            pattern = Pattern.compile(regex, Pattern.DOTALL);
        } catch (Exception e) {
            return -2;
        }
        long startTime = System.currentTimeMillis();
        if (pattern.matcher(text).matches())
            return System.currentTimeMillis() - startTime;
        return -1;
    }


}
