package supervisor.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;

import java.net.InetSocketAddress;

import java.util.Set;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;


import supervisor.storage.LocalStorage;
import supervisor.util.HttpRedirection;
import supervisor.util.Logger;

import com.amazonaws.services.ec2.model.Instance;


public class LoadBalancer {


    /* Fila de pedidos http */
    private static LinkedBlockingQueue<Request> inqueue = new LinkedBlockingQueue<>();

    /* thread workers state */
    private static AtomicBoolean active = new AtomicBoolean(true);

    /* Workers : Idealmente seria um Set. */
    private static Balancer worker = new LoadBalancer.Balancer();

    public LoadBalancer() {
    }

    public static void main(String[] args) throws Exception {

        try {
            Logger.publish(true, false);
            Logger.log("Starting the Load balancer");

            // Load local db
            LocalStorage.init();

            // Connect to aws
            CMonitor.init();

            // start redirection thread
            worker.start();

            //CMonitor.summon();

            HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
            server.createContext("/sudoku", new Request.Handler());
            server.setExecutor(Executors.newCachedThreadPool());

            //Thread.sleep(10000);

            CMonitor.terminate();
            // start http server.
            server.start();
        }catch (Exception e ){
            Logger.log("Terminating VMS" + e.toString());
            //CMonitor.terminate();
        }

    }

    static class Request {
        String query;
        HttpExchange tunel;

        Request(String query, HttpExchange t ){
            this.query = query;
            this.tunel = t;
        }

        public String toString(){
            return this.query + "\n" + this.tunel.toString();
        }

        static class Handler implements HttpHandler {

            public void handle(HttpExchange t) {

                String query = t.getRequestURI().getQuery();

                LoadBalancer.inqueue.add(
                        new LoadBalancer.Request(query,t)
                );
            }

        }

    }

    static class Balancer extends Thread{

        public Balancer(){
        }

        @Override
        public void run() {
            Request r=null;

            while ( LoadBalancer.active.get() ) {
                try {
                    r = LoadBalancer.inqueue.take();
                    String redirectPath = this.decide(r);

                    String location = "http://" + redirectPath + ":8000/sudoku?" + r.query ;

                    HttpRedirection.send(r.tunel,location);

                }catch(InterruptedException e){
                    Logger.log(e.toString());
                    return;
                }catch(IOException e){
                    Logger.log(e.toString());
                    LoadBalancer.inqueue.add(r);
                    return;
                }
            }

        }

        /*
        * Função que toma a decisão de encaminhamento do pedido.
        * */
        private String decide(Request r) throws InterruptedException{
            Logger.log("This code Delivers the task to the servers");
            Logger.log(r.toString());
            Logger.log("#####");

            Set<String> tmp = CMonitor.keys();
            while( tmp.size() == 0 ){
                Logger.log(".");
                Thread.sleep(100);
                tmp = CMonitor.keys();
            }

            Logger.log(tmp.toString());

            String vm = tmp.iterator().next();

            try {
                String result = CMonitor.get(vm).get("public.ip");
                return result;
            }catch (Exception e){
                Logger.log(" error fetching IP. ");
                LoadBalancer.inqueue.add(r);
                throw new InterruptedException();
            }

        }
    }
}
