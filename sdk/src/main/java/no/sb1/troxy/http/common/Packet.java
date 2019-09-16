package no.sb1.troxy.http.common;

/**
 * Common utility functions for Request and Response.

 */
public abstract class Packet {
    protected String discoverCharset(String header) {
        int start = header!=null ? header.indexOf("charset=") : -1;
        if (start >= 0) {
            start += 8;
            int end = header.indexOf(";", start);
            int pos = header.indexOf(" ", start);
            if (end == -1 || (pos != -1 && pos < end))
                end = pos;
            pos = header.indexOf("\n", start);
            if (end == -1 || (pos != -1 && pos < end))
                end = pos;
            if (end == -1)
                end = header.length();
            if (header.charAt(start) == '"' && header.charAt(end - 1) == '"') {
                ++start;
                --end;
            }
            return header.substring(start, end);
        }
        return "iso-8859-1";
    }
}
