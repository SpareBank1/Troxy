package no.sb1.troxy.http.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import no.sb1.troxy.common.Config;
import no.sb1.troxy.record.v3.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for filters, extend this class to implement a filter.
 * A new instance of the filter is created for each request/response-pair.
 * If you need a filter to affect subsequent requests/response-pairs you'll need to create static data fields in the filter and handle the required logic in the filter.
 * "filterClientRequest()" is called if regular expressions match.
 * "filterServerResponse()" is called if regular expressions match or "filterClientRequest()" was called.
 * "filterClientResponse()" is called if "filterClientRequest()" or "filterServerRequest()" were called.
 * The public methods are called in this order:
 * <code>
 * &lt;receive request&gt;
 * filterClientRequest(request)
 * &lt;load response from cache&gt;
 * &lt;if connecting to remote server[1]&gt;
 * filterServerRequest(request)
 * &lt;transmit request to and receive response from remote server&gt;
 * filterServerResponse(response)
 * &lt;end if&gt;
 * &lt;save request and response to cache&gt;
 * filterClientResponse(response)
 * &lt;transmit response&gt;
 * </code>
 * [1] - Will connect to remote server if in PASSTHROUGH mode, RECORD mode, or PLAYBACK_OR_RECORD mode and no response was found in cache.

 */
public abstract class Filter {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(Filter.class);
    /**
     * Map of groups connected to a map of fields and compiled patterns.
     * Group may be null, this denotes default expressions for all groups.
     * Fields may be "request.protocol", "response.header", etc. This is used to compare a Request or Response to the patterns to determine whether the filter should be applied or not.
     */
    private static Map<Class, Map<String, Map<String, List<Pattern>>>> groupPatterns = new HashMap<>();
    /**
     * Map saying which filters are enabled.
     */
    private static Map<String, Boolean> filterEnabled = new HashMap<>();
    /**
     * If we've already evaluated regular expressions used to find configuration group for request.
     * This is an optimization, no need to check regular expressions both for filterClientRequest() and filterServerRequest().
     * We can do this since each instantiation of a filter is only used for one request/response-pair.
     */
    private boolean checkedPatterns;
    /**
     * If we've already evaluated regular expressions used to find configuration group for response.
     * This is an optimization, no need to check regular expressions both for filterClientResponse() and filterServerResponse().
     * We can do this since each instantiation of a filter is only used for one request/response-pair.
     */
    private boolean checkedResponsePatterns;
    /**
     * If the request should be filtered. Optimization used along with checkedPatterns.
     */
    private boolean doFilter;
    /**
     * The name of the configuration group for this request.
     * This is used together with checkedPatterns.
     */
    private String filterGroup;

    /**
     * Check if filter should be applied to request, and invoke it if it should.
     * @param request The incoming request.
     * @param remoteRequest Should be <code>true</code> when we're forwarding the request to a remote server.
     */
    public final void doFilterRequest(Request request, boolean remoteRequest) {
        /* did we check the regular expressions earlier? */
        if (!checkedPatterns) {
            /* prevent us from comparing regular expressions again for successive calls to this method */
            checkedPatterns = true;
            /* iterate through the groups and see if any groups match the request */
            for (Map.Entry<String, Map<String, List<Pattern>>> group : groupPatterns.get(getClass()).entrySet()) {
                /* the null group is the default if no group match, we test that last */
                if (group.getKey() == null)
                    continue;
                if (compareRequestWithPatternGroup(request, group.getValue())) {
                    /* request match a group, set doFilter and filterGroup */
                    doFilter = true;
                    filterGroup = group.getKey();
                    break;
                }
            }
            if (!doFilter) {
                /* no groups match, check if the default patterns match */
                doFilter = compareRequestWithPatternGroup(request, groupPatterns.get(getClass()).get(null));
            }
        }
        if (!doFilter)
            return;
        /* invoke filter */
        if (remoteRequest) {
            log.debug("{}.filterRemoteRequest() start for filter group {}", getClass().getName(), filterGroup);
            try {
                filterServerRequest(request, filterGroup);
            } catch (Exception e) {
                log.warn("{}.filterRemoteRequest() failed", getClass().getName(), e);
            }
            log.debug("{}.filterRemoteRequest() finished", getClass().getName());
        } else {
            log.debug("{}.filterClientRequest() start for filter group {}", getClass().getName(), filterGroup);
            try {
                filterClientRequest(request, filterGroup);
            } catch (Exception e) {
                log.warn("{}.filterClientRequest() failed", getClass().getName(), e);
            }
            log.debug("{}.filterClientRequest() finished", getClass().getName());
        }
    }

    /**
     * Check if filter should be applied to response, and invoke it if it should.
     * @param response The outgoing response.
     * @param remoteResponse Should be <code>true</code> when we're filtering the response from a remote server.
     */
    public final void doFilterResponse(Response response, boolean remoteResponse) {
        /* did we check the regular expressions earlier? */
        if (!doFilter && !checkedResponsePatterns) {
            /* prevent us from comparing regular expressions again for successive calls to this method */
            checkedResponsePatterns = true;
            /* iterate through the groups and see if any groups match the response */
            for (Map.Entry<String, Map<String, List<Pattern>>> group : groupPatterns.get(getClass()).entrySet()) {
                /* the null group is the default if no group match, we test that last */
                if (group.getKey() == null)
                    continue;
                if (compareResponseWithPatternGroup(response, group.getValue())) {
                    /* response match a group, set doFilter and filterGroup */
                    doFilter = true;
                    filterGroup = group.getKey();
                    break;
                }
            }
            if (!doFilter) {
                /* no groups match, check if the default patterns match */
                doFilter = compareResponseWithPatternGroup(response, groupPatterns.get(getClass()).get(null));
            }
        }
        if (!doFilter)
            return;
        /* invoke filter */
        if (remoteResponse) {
            log.debug("{}.filterRemoteResponse() start for filter group {}", getClass().getName(), filterGroup);
            try {
                filterServerResponse(response, filterGroup);
            } catch (Exception e) {
                log.warn("{}.filterServerResponse() failed", getClass().getName(), e);
            }
            log.debug("{}.filterRemoteResponse() finished", getClass().getName());
        } else {
            log.debug("{}.filterClientResponse() start for filter group {}", getClass().getName(), filterGroup);
            try {
                filterClientResponse(response, filterGroup);
            } catch (Exception e) {
                log.warn("{}.filterClientResponse() failed", getClass().getName(), e);
            }
            log.debug("{}.filterClientResponse() finished", getClass().getName());
        }
    }

    /**
     * Check if filter should be applied to the recording, and invoke it if it should.
     * @param recording The recording.
     */
    public final void doFilterRecording(Recording recording) {
        /* no regular expressions limiting when this method should be called, it's always invoked */
        log.debug("{}.filterNewRecording() start", getClass().getName());
        try {
            filterNewRecording(recording);
        } catch (Exception e) {
            log.warn("{}.filterNewRecording() failed", getClass().getName(), e);
        }
        log.debug("{}.filterNewRecording() finished", getClass().getName());
    }

        /**
         * Check whether this filter is enabled.
         * @return Whether filter is enabled.
         */
    public final boolean isEnabled() {
        return Boolean.TRUE.equals(filterEnabled.get(getClass().getSimpleName()));
    }

    /**
     * Reload configuration common for all filters.
     */
    public final void reload(final Config config) {
        String className = getClass().getSimpleName();
        String key = "filter." + className + ".";
        /* is filter enabled? */
        boolean enabled = "true".equalsIgnoreCase(config.getValue(key + "enabled", "false"));
        filterEnabled.put(className, enabled);
        if (!groupPatterns.containsKey(getClass()))
            groupPatterns.put(getClass(), new HashMap<>());
        if (enabled) {
            /* load regular expressions for triggering filter and filter specific configuration */
            groupPatterns.get(getClass()).clear();
            Map<String, Map<String, String>> filterConfiguration = new HashMap<>();
            Map<String, String> expressions = config.getKeysAndValues(key);
            for (Map.Entry<String, String> entry : expressions.entrySet()) {
                String[] keyArray = entry.getKey().split("\\.");
                /* configuration "enabled" is a bit special, and shouldn't be parsed, so skip it */
                if (keyArray.length == 3 && "enabled".equals(keyArray[2]))
                    continue;
                String group = null;
                String type = null;
                String field = null;
                if (keyArray.length == 4) {
                    /* filter.<name>.<type>.<field>=<value> */
                    type = keyArray[2];
                    field = keyArray[3];
                } else if (keyArray.length == 5) {
                    /* filter.<name>.<group>.(request|response|config).<field>=<value>
                     * or
                     * filter.<name>.(request|response).<field>.<id>=<value>
                     * or
                     * filter.<name>.config.<field_with_dot>=<value>
                     */
                    group = keyArray[2];
                    type = keyArray[3];
                    field = keyArray[4];
                    if (!"request".equals(type) && !"response".equals(type) && !"config".equals(type)) {
                        /* filter.<name>.(request|response).<field>.<id>=<value>
                         * or
                         * filter.<name>.config.<field_with_dot>=<value>
                         */
                        field = type;
                        type = group;
                        group = null;
                        if ("config".equals(type)) {
                            /* filter.<name>.config.<field_with_dot>=<value> */
                            field += '.' + keyArray[4];
                        }
                    }
                } else if (keyArray.length >= 6) {
                    /* filter.<name>.<group>.(request|response|config).<field>.<id>=<value>
                     * or
                     * filter.<name>.<group>.config.<field_with_dots>=<value>
                     * or
                     * filter.<name>.config.<field_with_dots>=<value>
                     */
                    group = keyArray[2];
                    type = keyArray[3];
                    field = keyArray[4];
                    if ("config".equals(type)) {
                        /* filter.<name>.<group>.config.<field_with_dots>=<value> */
                        for (int i = 5; i < keyArray.length; ++i)
                            field += '.' + keyArray[i];
                    } else if ("config".equals(group)) {
                        /* filter.<name>.config.<field_with_dots>=<value> */
                        field = type;
                        type = group;
                        group = null;
                        for (int i = 4; i < keyArray.length; ++i)
                            field += '.' + keyArray[i];
                    }
                }
                /* if (request.<protocol/host/port/path/query/method/header/content> || response.<code/header/content>) */
                if (("request".equals(type) && ("protocol".equals(field) || "host".equals(field) || "port".equals(field) || "path".equals(field) || "query".equals(field) || "method".equals(field) || "header".equals(field) || "content".equals(field))) || ("response".equals(type) && ("code".equals(field) || "header".equals(field) || "content".equals(field)))) {
                    /* regular expression common for all filters */
                    Map<String, List<Pattern>> fieldPatterns = groupPatterns.get(getClass()).get(group);
                    if (fieldPatterns == null) {
                        fieldPatterns = new HashMap<>();
                        groupPatterns.get(getClass()).put(group, fieldPatterns);
                    }
                    String typeAndField = type + "." + field;
                    List<Pattern> patterns = fieldPatterns.get(typeAndField);
                    if (patterns == null) {
                        patterns = new ArrayList<>();
                        fieldPatterns.put(typeAndField, patterns);
                    }
                    patterns.add(Pattern.compile(entry.getValue(), Pattern.DOTALL));
                } else if ("config".equals(type)) {
                    /* filter specific configuration */
                    Map<String, String> conf = filterConfiguration.get(group);
                    if (conf == null) {
                        conf = new HashMap<>();
                        filterConfiguration.put(group, conf);
                    }
                    conf.put(field, entry.getValue());
                } else {
                    log.warn("Unable to parse filter setting (type: {}): {}={}", type, entry.getKey(), entry.getValue());
                }
            }
            /* in case there's no configuration for the default group, we need to create that */
            if (!filterConfiguration.containsKey(null)) {
                filterConfiguration.put(null, new HashMap<>());
            }
            /* tell filter to reload its configuration */
            loadConfig(filterConfiguration);
        }
    }

    /**
     * Load filter specific configuration.
     * The filter must handle multiple groups and optimize the data in this method rather than doing so in the filter-methods.
     * In the given configuration map, the first string is the group name, and the map associated with this group contains keys and values.
     * @param configuration Configuration to be loaded.
     */
    protected abstract void loadConfig(Map<String, Map<String, String>> configuration);

    /**
     * Filter incoming Request before sending it to the remote server and saving it to the Cache.
     * This method is called for all requests to the simulator, regardless of its current mode.
     * @param request The incoming Request.
     * @param group The group the request matched.
     */
    protected void filterClientRequest(Request request, String group) {
    }

    /**
     * Filter incoming Request after checking the Cache for a matching response and before sending the request to a remote server.
     * This method is only called when in record mode and the request is sent to a remote server.
     * @param request The incoming Request.
     * @param group The group the request matched.
     */
    protected void filterServerRequest(Request request, String group) {
    }

    /**
     * Filter outgoing Response after receiving a response from the remote server and before saving it to the Cache.
     * This method is only called when in record mode and a response is received from the remote server.
     * @param response The outgoing Response.
     * @param group The group the response matched.
     */
    protected void filterServerResponse(Response response, String group) {
    }

    /**
     * Filter a Recording before it's serialized to disk.
     * If the filter is only meant to modify Recordings from requests or responses limited by a set of regular expressions,
     * then this limitation should be set up in the methods filterClientRequest() or filterServerResponse().
     * This is useful for automatically modifying RequestPattern and ResponseTemplate, or setting metadata such as response delay.
     * @param recording The newly created Recording.
     */
    protected void filterNewRecording(Recording recording) {
    }

    /**
     * Filter outgoing Response after receiving a response from the remote server or after loading it from the Cache.
     * This method is called for all responses sent from the simulator (except generated error responses), regardless of its current mode.
     * Use this method when you need to modify every response, a good example is adding delay to a response.
     * @param response The outgoing Response.
     * @param group The group the response matched.
     */
    protected void filterClientResponse(Response response, String group) {
    }

    /**
     * Check that the request match the given pattern group.
     * @param request The incoming request.
     * @param group The pattern group to match the request with.
     * @return Whether the request match the given pattern group.
     */
    private boolean compareRequestWithPatternGroup(Request request, Map<String, List<Pattern>> group) {
        if (group == null || group.size() <= 0) {
            /* no regular expressions at all for this group, all match */
            return true;
        }
        List<Pattern> protocolPatterns = group.get("request.protocol");
        List<Pattern> hostPatterns = group.get("request.host");
        List<Pattern> portPatterns = group.get("request.port");
        List<Pattern> pathPatterns = group.get("request.path");
        List<Pattern> queryPatterns = group.get("request.query");
        List<Pattern> methodPatterns = group.get("request.method");
        List<Pattern> headerPatterns = group.get("request.header");
        List<Pattern> contentPatterns = group.get("request.content");
        if (protocolPatterns == null && hostPatterns == null && portPatterns == null && pathPatterns == null && queryPatterns == null && methodPatterns == null && headerPatterns == null && contentPatterns == null) {
            /* no pattern for neither of the request fields, no match */
            return false;
        }
        if (!compareValueWithRegularExpressions(request.getProtocol(), protocolPatterns))
            return false;
        if (!compareValueWithRegularExpressions(request.getHost(), hostPatterns))
            return false;
        if (!compareValueWithRegularExpressions(request.getPort(), portPatterns))
            return false;
        if (!compareValueWithRegularExpressions(request.getPath(), pathPatterns))
            return false;
        if (!compareValueWithRegularExpressions(request.getQuery(), queryPatterns))
            return false;
        if (!compareValueWithRegularExpressions(request.getMethod(), methodPatterns))
            return false;
        if (!compareValueWithRegularExpressions(request.getHeader(), headerPatterns))
            return false;
        if (!compareValueWithRegularExpressions(request.getContent(), contentPatterns))
            return false;
        return true;
    }

    /**
     * Check that the response match the given pattern group.
     * @param response The outgoing response.
     * @param group The pattern group to match the response with.
     * @return Whether the response match the given pattern group.
     */
    private boolean compareResponseWithPatternGroup(Response response, Map<String, List<Pattern>> group) {
        if (group == null || group.size() <= 0) {
            /* no regular expressions at all for this group, all match */
            return true;
        }
        List<Pattern> codePatterns = group.get("response.code");
        List<Pattern> headerPatterns = group.get("response.header");
        List<Pattern> contentPatterns = group.get("response.content");
        if (codePatterns == null && headerPatterns == null && contentPatterns == null) {
            /* no pattern for neither of the response fields, no match */
            return false;
        }
        if (!compareValueWithRegularExpressions(response.getCode(), codePatterns))
            return false;
        if (!compareValueWithRegularExpressions(response.getHeader(), headerPatterns))
            return false;
        if (!compareValueWithRegularExpressions(response.getContent(), contentPatterns))
            return false;
        return true;
    }

    /**
     * Compare a value with regular expressions for a given field.
     * @param value The value to compare with regular expressions.
     * @param patterns The regular expressions to compare with the value.
     * @return <code>true</code> if there are no regular expressions for the given field or if one or more of the regular expressions match, <code>false</code> otherwise.
     */
    private boolean compareValueWithRegularExpressions(String value, List<Pattern> patterns) {
        if (patterns == null) {
            /* no regular expressions, same as specifying a ".*" regular pattern */
            return true;
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(value).matches()) {
                /* this regular pattern match */
                return true;
            }
        }
        /* no regular pattern match */
        return false;
    }
}
