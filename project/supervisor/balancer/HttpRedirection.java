package supervisor.balancer;

import com.sun.net.httpserver.HttpExchange;
import supervisor.util.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpRedirection {
    /**
     * Esta classe trata dos detalhos de mandar http redirect.
     */

    public static void send(HttpExchange ex, String redirectPath)
            throws IOException {

        URI path;

        try {
            path = new URI(redirectPath);

            Logger.log("Redirection path");
            Logger.log(path.toString());

            ex.getResponseHeaders().set("Location", path.toString());

            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            //Required by firefox or js. (Super unsafe don't use in a serious project)

            ex.sendResponseHeaders(307, -1);
            // 303 does not work properly. ()
            // ex.close();

        } catch (URISyntaxException e) {
            Logger.log(e.toString());
            throw new IOException();
        }
    }

}
