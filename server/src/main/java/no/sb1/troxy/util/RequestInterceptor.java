package no.sb1.troxy.util;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * This "Handler" only saves who have used Troxy and when.
 */
public class RequestInterceptor extends AbstractHandler {
    private static Map<String, Date> lastUsers = new HashMap<>();

    public static Map<String, Date> getLastUsers() {
        return lastUsers;
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
        lastUsers.put(request.getRemoteAddr(), new Date());
    }
}
