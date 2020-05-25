package no.sb1.troxy.util;

import no.sb1.troxy.http.common.Request;
import no.sb1.troxy.record.v3.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Cache contains all the loaded/activated Recordings.
 * It's a tree structure (acyclic graph) which allows matching a Request to a Recording fast:
 * <code>
 * Root   -  Protocol  -  Host  -  Port  -  Path  -  Query  -  Method  -  Header  -  Content
 * ===================================================================================================
 * root +--- ^http$ +---- ^.*$ --- ^80$ --- ^/.*$ -- ^.*$ ---- ^GET$ ---- ^.*$ ----- ^&lt;xml ...&gt;$
 *      |\           \
 *      | \- ^https$  \-- ... +--- ^80$
 *       \                     \
 *        \- ...                \- ...
 * </code>
 */
public class Cache {
    /**
     * An enum denoting the fields in the Cache.
     */
    private enum Field {
        /**
         * This field is only used for the root of the Cache.
         */
        NONE {
            @Override
            public String getValue(Request request) {
                return "";
            }
        },
        /**
         * The protocol field of the branch.
         */
        PROTOCOL {
            @Override
            public String getValue(Request request) {
                return request.getProtocol();
            }
        },
        /**
         * The host field of the branch.
         */
        HOST {
            @Override
            public String getValue(Request request) {
                return request.getHost();
            }
        },
        /**
         * The port field of the branch.
         */
        PORT {
            @Override
            public String getValue(Request request) {
                return request.getPort();
            }
        },
        /**
         * The path field of the branch.
         */
        PATH {
            @Override
            public String getValue(Request request) {
                return request.getPath();
            }
        },
        /**
         * The query field of the branch.
         */
        QUERY {
            @Override
            public String getValue(Request request) {
                return request.getQuery();
            }
        },
        /**
         * The method field of the branch.
         */
        METHOD {
            @Override
            public String getValue(Request request) {
                return request.getMethod();
            }
        },
        /**
         * The header field of the branch.
         */
        HEADER {
            @Override
            public String getValue(Request request) {
                return request.getHeader();
            }
        },
        /**
         * The content field of the branch.
         */
        CONTENT {
            @Override
            public String getValue(Request request) {
                return request.getContent();
            }
        };

        /**
         * Get the value of this field in the given Request.
         *
         * @param request The Request to retrieve the value from.
         * @return The value of this field in Request.
         */
        public abstract String getValue(Request request);
    }

    public static class Result {
        private Recording recording;
        private Map<String, Matcher> variables;

        public Result(Recording recording, Map<String, Matcher> variables) {
            this.recording = recording;
            this.variables = variables;
        }

        public Recording getRecording() {
            return recording;
        }

        public Map<String, Matcher> getVariables() {
            return variables;
        }
    }

    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(Cache.class);
    /**
     * The root of the cache.
     */
    private final Cache root; // = new Cache(Field.NONE, null);
    /**
     * The field of this level in the cache.
     */
    private final Field field;
    /**
     * The regular expression for this field and branch.
     */
    private final Pattern pattern;
    /**
     * All the branches forking out from this branch.
     */
    private List<Cache> children = new ArrayList<>();
    /**
     * At the end of a branch a single Recording may exist.
     */
    private Recording recording;

    public static Cache createCacheRoot() {
        return new Cache(null, Field.NONE, null);
    }

    /**
     * Private constructor setting field & pattern.
     *
     * @param field   The field for this branch.
     * @param pattern The pattern for this branch.
     */
    private Cache(Cache root, Field field, Pattern pattern) {
        this.root = root;
        this.field = field;
        this.pattern = pattern;
    }

    /**
     * Load Recordings into Cache.
     *
     * @param paths Files to load.
     */
    public static void loadRecordings(final Cache root, final TroxyFileHandler troxyFileHandler, final Set<String> paths) {
        if (root.root != null) {
            throw new IllegalStateException("Trying to add records to non root cache node");
        }

        for (String path : paths) {
            if (path.endsWith(".troxy") || path.endsWith(".xml")) {
                try {
                    Recording recording = troxyFileHandler.loadRecording(path);
                    if (recording != null)
                        root.addRecoding(recording);
                } catch (Exception e) {
                    log.warn("Error reading file: {}", path, e);
                }
            } else if (!troxyFileHandler.isDirectory(path)) {
                log.info("Skipping file (unknown type): {}", path);
            }
        }
    }

    public static void loadRecordingsWithPaths(final Cache root, final TroxyFileHandler troxyFileHandler, final Set<String> paths) {
        if (root.root != null) {
            throw new IllegalStateException("Trying to add records to non root cache node");
        }

        for (String path : paths) {
            if (path.endsWith(".troxy") || path.endsWith(".xml")) {
                try {
                    Recording recording = troxyFileHandler.loadRecording(new File(path).getParent(), new File(path).getName());
                    if (recording != null)
                        root.addRecoding(recording);
                } catch (Exception e) {
                    log.warn("Error reading file: {}", path, e);
                }
            } else if (!troxyFileHandler.isDirectory(path)) {
                log.info("Skipping file (unknown type): {}", path);
            }
        }
    }


    /**
     * Add a Recording to the Cache.
     *
     * @param recording The Recording to add.
     */
    public void addRecoding(Recording recording) {
        log.info("Adding recording to Cache: {}", recording);
        Queue<Field> queue = new ArrayDeque<>();
        queue.add(Field.PROTOCOL);
        queue.add(Field.HOST);
        queue.add(Field.PORT);
        queue.add(Field.PATH);
        queue.add(Field.QUERY);
        queue.add(Field.METHOD);
        queue.add(Field.HEADER);
        queue.add(Field.CONTENT);

        Cache root = this.root == null ? this : this.root;
        Cache current = root;
        while (!queue.isEmpty()) {
            Field f = queue.poll();
            String value = f.getValue(recording.getRequestPattern());
            boolean found = false;
            log.debug("Looking for Cache branch with field \"{}\" and pattern: {}", f, value);
            for (Cache c : current.children) {
                if (value.equals(c.pattern.pattern())) {
                    log.debug("Branch found");
                    current = c;
                    found = true;
                    break;
                }
            }
            if (!found) {
                log.debug("Branch not found, creating one");
                Cache tmp = new Cache(root, f, Pattern.compile(value, Pattern.DOTALL));
                current.children.add(tmp);
                current = tmp;
            }
        }
        if (current.recording == null) {
            /* new Recording  */
            log.debug("Recording added to cache");
        } else {
            /* overwriting existing Recording, hmm */
            log.info("Overwriting existing recording in cache");
        }
        current.recording = recording;
    }

    /**
     * Clear entire cache.
     */
    public void clear() {
        /* we'll rely on the garbage collector and just create a new root */
        log.info("Clearing Cache");

        if (root != null) {
            throw new IllegalStateException("Trying to clear records to non root cache node");
        }

        children.clear();
    }

    /**
     * Search cache for entries matching given Request.
     *
     * @param request The Request to search for.
     * @return A list of entries matching the Request.
     */
    public List<Cache.Result> searchCache(Request request) {

        if (root != null) {
            throw new IllegalStateException("Trying to search records to non root cache node");
        }

        log.info("Searching Cache for Recording matching Request: {}", request);
        return searchCache(new ArrayList<>(), new HashMap<>(), request);
    }

    /**
     * Get all the Recordings in the cache.
     *
     * @return All the Recordings in the cache.
     */
    public Set<Recording> getRecordings() {

        if (root != null) {
            throw new IllegalStateException("Trying to get all records to non root cache node");
        }

        Set<Recording> recordings = new HashSet<>();
        List<Cache> queue = new ArrayList<>();
        queue.add(this);
        while (!queue.isEmpty()) {
            Cache current = queue.remove(0);
            queue.addAll(current.children);
            if (current.recording != null)
                recordings.add(current.recording);
        }
        return recordings;
    }

    public Map<String, Integer> getRequestCounterPerPath(){
        Map<String, Integer> totalRequests = new HashMap<>();
        for (Recording recording : getRecordings()) {
            String path = getTrimmedPath(recording);
            int count = totalRequests.getOrDefault(path, 0);
            totalRequests.put(path, count + recording.getResponseCounterTotal());
        }
        return totalRequests;
    }

    public void resetTotalStatisticCounter(){
        getRecordings()
                .forEach(Recording::resetResponseCounterTotal);
    }

    public Map<String, Integer> getRequestCounterPerRecording() {
        Map<String, Integer> totalRequests = new HashMap<>();
        getRecordings()
                .forEach(recording -> totalRequests.put(
                        recording.getFilename(),
                        recording.getResponseCounterTotal()
                ));
        return totalRequests;
    }

    private static String getTrimmedPath(Recording recording) {
        String path = recording.getRequestPattern().getPath();

        if(path.endsWith("$"))
        {
            path = path.substring(0, path.length() - 1);
        }
        if(path.startsWith("^"))
        {
            path = path.substring(1);
        }
        return path;
    }

    /**
     * Helper method for searching the cache for an entry matching the Request.
     * Variables discovered while searching the cache is passed on.
     *
     * @param results   The found entries matching the Request.
     * @param variables The keys and values discovered when searching cache.
     * @param request   The Request to search for.
     * @return A list of entries matching the Request.
     */
    private List<Cache.Result> searchCache(List<Cache.Result> results, Map<String, Matcher> variables, Request request) {
        if (root != null) {
            /* need to match pattern with current field, except for root where pattern is null */
            Matcher matcher = pattern.matcher(field.getValue(request));
            if (!matcher.find()) {
                log.debug("Field {}: Pattern «{}» did not match value «{}»", field, pattern.pattern(), field.getValue(request));
                return results;
            }
            variables.put(field.toString(), matcher);
        }
        for (Cache c : children)
            c.searchCache(results, variables, request);
        if (recording != null)
            results.add(new Result(recording, variables));
        return results;
    }
}
