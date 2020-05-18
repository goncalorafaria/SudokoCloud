package supervisor.balancer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import supervisor.util.CloudStandart;
import supervisor.util.Logger;

import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoadBalancer {

    private static final long waitTime = 30 * 1000;
    /* Fila de pedidos http */
    private static final LinkedBlockingQueue<Request> inqueue = new LinkedBlockingQueue<>();
    /* thread workers state */
    private static final AtomicBoolean active = new AtomicBoolean(true);
    /* Workers : Idealmente seria um Set. */
    private static final Balancer worker = new LoadBalancer.Balancer();

    private static String me = null;

    private static boolean handle = true;

    public LoadBalancer() {
    }

    public static void main(String[] args){

        try {
            double lowerth = 0.25, upperth = 0.75;
            int cachesize = 200;

            if( args.length > 0 ){
                handle = Boolean.parseBoolean(args[0]);
                if( args.length > 2 ){
                    lowerth = Double.parseDouble(args[1]);
                    upperth = Double.parseDouble(args[2]);
                    if( args.length > 3){
                        cachesize = Integer.parseInt(args[3]);
                        if( args.length > 4){
                            me = args[4];
                        }
                    }
                }
            }

            Logger.publish(true, false);
            Logger.log("Starting the Load balancer");

            CloudStandart.init();
            // Load local db
            // Connect to aws
            CMonitor.init(
                    lowerth,
                    upperth,
                    cachesize,
                    me);

            // start redirection thread
            worker.start();

            HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

            if( handle ) {
                Logger.log("Handling failures directly.");
                server.createContext("/sudoku", new Request.RemoteServer());
            }else {
                Logger.log("Not handling failures directly.");
                server.createContext("/sudoku", new Request.Redirector());
            }
            server.setExecutor(Executors.newCachedThreadPool());

            // start http server.
            server.start();
        } catch (Exception e) {
            Logger.log("Terminating VMS" + e.toString());
        }
    }

    static class Request extends Thread {
        String query;
        Redirect redirect;
        URI uri;
        AtomicBoolean shoudlread = new AtomicBoolean(false);

        Request(String query, HttpExchange t) {
            this.query = query;
            this.redirect = new Redirect(t);
        }

        public String toString() {
            return this.query + "\n" + this.redirect.toString();
        }

        static class Redirector implements HttpHandler {

            public void handle(HttpExchange t) {

                String query = t.getRequestURI().getQuery();

                LoadBalancer.inqueue.add(
                        new LoadBalancer.Request(query, t)
                );
            }
        }

        static class RemoteServer implements HttpHandler {

            public void handle(HttpExchange t) {

                URI u = t.getRequestURI();
                String query = u.getQuery();

                LoadBalancer.Request r = new LoadBalancer.Request(query, t);
                r.uri = u;
                r.run();
            }

        }

        public void run() {
            boolean go = true;

            String key = CloudStandart.makeKey(this.query);
            String solution=null;
            while(go){
                go =false;
                try {
                    CMonitor.Endpoint ep = CMonitor.decide(
                            key);

                    //ep.listening(key,this);
                    int port = 8000;
                    this.redirect.passRequest(ep.getIp(), port, uri);

                    try {
                        this.redirect.cn.getInputStream();
                    }catch(ConnectException e ){
                        go = true;
                        ep.faultdetected();
                    }catch (IOException e){
                        Logger.log("justsend" + e.toString());
                    }
                    Logger.log("IS NOT BLOCKED");

                }catch (InterruptedException e){
                    Logger.log("error balancing:" + e.toString());
                }catch (IOException|URISyntaxException e){
                    Logger.log("error passing message:" + e.toString());
                }
            }

            try {
                solution = this.redirect.readResponse();
                this.redirect.passResponse(solution);
            }catch (IOException e){
                Logger.log("error responding client:" + e.toString());
            }
        }
    }

    static class Balancer extends Thread {

        public Balancer() {
        }

        @Override
        public void run() {
            Request r = null;

            while (LoadBalancer.active.get()) {
                try {

                    CMonitor.autoscale();
                    r = LoadBalancer.inqueue.poll(LoadBalancer.waitTime, TimeUnit.MILLISECONDS);

                    if (r != null) {
                        String redirectPath = CMonitor.decide(
                                CloudStandart.makeKey(r.query)).getIp();

                        String location = "http://" + redirectPath + ":8000/sudoku?" + r.query;

                        r.redirect.send(location);
                    }else{
                        Logger.log("Autoscalling round ");
                    }

                } catch (InterruptedException e) {
                    Logger.log(e.toString());
                    return;
                } catch (IOException e) {
                    Logger.log(e.toString());
                    LoadBalancer.inqueue.add(r);
                    return;
                }
            }

        }

    }
}
