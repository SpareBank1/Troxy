package no.sb1.troxy.util;

import no.sb1.troxy.record.v3.Recording;
import java.util.HashMap;
import java.util.Map;

public class CacheUtil {

    public static Map<String, Integer> getRequestCounterPerPath(Cache cache){
        Map<String, Integer> totalRequests = new HashMap<>();
        for (Recording recording : cache.getRecordings()) {
            String path = getTrimmedPath(recording);
            int count = totalRequests.getOrDefault(path, 0);
            totalRequests.put(path, count + recording.getResponseCounterTotal());
        }
        return totalRequests;
    }

    public static void resetTotalStatisticCounter(Cache cache){
        cache.getRecordings()
                .forEach(Recording::resetResponseCounterTotal);
    }

    public static Map<String, Integer> getRequestCounterPerRecording(Cache cache) {
        Map<String, Integer> totalRequests = new HashMap<>();
        cache.getRecordings()
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
}
