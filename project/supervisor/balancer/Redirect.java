package supervisor.balancer;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import supervisor.util.Logger;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class Redirect {
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
            ex.close();

        } catch (URISyntaxException e) {
            Logger.log(e.toString());
            throw new IOException();
        }
    }

    public static String passRequestandWait(HttpExchange t, String ip, int port, URI inu)
            throws URISyntaxException, IOException {

        URI outu = new URI(
                inu.getScheme(),
                inu.getRawUserInfo(),
                ip,
                port,
                inu.getPath(),
                inu.getRawQuery(),
                inu.getFragment());

        URL curl = new URL("http:" + outu.toString());
        Logger.log(curl.toString());
        HttpURLConnection cn = (HttpURLConnection)curl.openConnection();
        cn.setRequestMethod("GET");

        byte[] buffer = new byte[t.getRequestBody().available()];

        t.getRequestBody().read(buffer);

        cn.setUseCaches(false);
        cn.setDoInput(true);
        cn.setDoOutput(true);

        DataOutputStream wr = new DataOutputStream (
                cn.getOutputStream());

        wr.write(buffer);
        wr.flush();
        wr.close();

        int status = cn.getResponseCode();

        Logger.log("status code:" + status);

        InputStream is = cn.getInputStream();

        is.read(buffer);

        return new String(buffer);
    }

    public static void passResponse(String solution, HttpExchange t) throws IOException {

        Logger.log("solution");
        Headers hdrs = t.getResponseHeaders();
        hdrs.add("Content-Type", "application/json");
        hdrs.add("Access-Control-Allow-Origin", "*");
        hdrs.add("Access-Control-Allow-Credentials", "true");
        hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
        hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
        t.sendResponseHeaders(200, solution.length());

        OutputStream os = t.getResponseBody();
        OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        osw.write(solution);
        osw.flush();
        osw.close();
        os.close();
    }

}
