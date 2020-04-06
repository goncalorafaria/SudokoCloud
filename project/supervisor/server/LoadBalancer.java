package supervisor.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import java.io.IOException;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;

import supervisor.server.CMonitor;
import supervisor.storage.LocalStorage;

public class LoadBalancer {

    private static Queue<Request> inqueue = new ConcurrentLinkedQueue<>();

    private static Queue<LoadBalancer.FulfilledRequest> outqueue = new ConcurrentLinkedQueue<>();

    public LoadBalancer() {
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/sudoku", new RequestHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        //server.start();
        //System.out.println(server.getAddress().toString());

        System.out.println("Starting the Load balancer");

        LocalStorage.init();

        CMonitor.init();


        for(int i=0;i<4;i++)
            CMonitor.summon();

        Thread.sleep(50000);

        System.out.println(CMonitor.describe());

        CMonitor.terminate();
    }

    static class Request {
        String query;
        HttpExchange tunel;

        Request(String query, HttpExchange t ){
            this.query = query;
            this.tunel = t;
        }
    }

    static class FulfilledRequest{
        
        Request r;
        JSONArray solution;

        FulfilledRequest(Request r, JSONArray solution){
            this.r = r;
            this.solution = solution;
        }
    }

    static class RequestHandler implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            
            String query = t.getRequestURI().getQuery();

            LoadBalancer.inqueue.add(
                new LoadBalancer.Request(query,t)
                );
        }

    }

    static class ResponseHandler {

        public void handle(LoadBalancer.FulfilledRequest fr) throws IOException {
            
            HttpExchange t = fr.r.tunel;
            JSONArray solution = fr.solution;

            Headers hdrs = t.getResponseHeaders();
            hdrs.add("Content-Type", "application/json");
            hdrs.add("Access-Control-Allow-Origin", "*");
            hdrs.add("Access-Control-Allow-Credentials", "true");
            hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
            hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
            t.sendResponseHeaders(200, (long)solution.toString().length());
            OutputStream os = t.getResponseBody();
            OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
            osw.write(solution.toString());
            osw.flush();
            osw.close();
            os.close();
            System.out.println("> Sent response to " + t.getRemoteAddress().toString());

        }

    }

}
