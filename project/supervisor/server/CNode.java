package supervisor.server;

import supervisor.storage.TaskStorage;
import supervisor.util.CloudStandart;
import supervisor.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CNode {
    /**
     *  This is the Server Hypervisor class.
     * */

    /* tasks que estão a decorrer, indexadas pelo thread que está a correr. */
    private static final Map<Long, Task> activetasks
            = new ConcurrentHashMap<>();

    /* Fila de espera para publicar as metricas das tasks terminadas */
    private static final BlockingQueue<Task> taskq = new LinkedBlockingQueue();

    /* storage persistente para request metrics. */
    private static TaskStorage requestTable;

    /* thread that takes care of publishing metrics to db. */
    private static Publisher worker;

    /* class that comunicates with the balancer. */
    private static CNode.EndPoint tunnel;

    /** Initializes storage, workers and communication channel with balancer. */
    public static void init() {

        TaskStorage.init(true);

        CNode.requestTable = new TaskStorage();

        CNode.worker = new Publisher();
        CNode.worker.start();

        CNode.tunnel = new CNode.EndPoint();

        Logger.publish(false,false);
    }

    /** Associa um novo pedido a um thread. */
    public static void registerTask(String taskkey) {
        registerTask(taskkey, false);
    }

    /** Associa um novo pedido a um thread. */
    public static void registerTask(String taskkey, boolean overhead) {
        //Logger.log("Registering task:" + taskkey);

        if (!activetasks.containsKey(Thread.currentThread().getId())) {
            Task t = new Task(taskkey);
            t.addMetric("Count", new Count());
            if(overhead)
                t.addMetric("Overhead", new Count());

            activetasks.put(Thread.currentThread().getId(), t);

            String solver = taskkey.split(":")[0];

            CNode.tunnel.increment(
                    Thread.currentThread().getId(),
                    solver);

            Logger.log("Registering task:" + taskkey);
            Logger.log("with "+ Thread.currentThread().getId());

        }
        Logger.log("start " + Thread.currentThread().getId());
    }

    /** Termina um novo pedido. */
    public static void finishTask() {
        Long tid = Thread.currentThread().getId();
        Task t = activetasks.remove(tid);

        Logger.log("Finish task:" + t.getKey());
        t.wrap();
        taskq.add(t);

        CNode.tunnel.decrement(tid,
                ((Count)t.getMetric("Count")).getlocked());
    }

    public static void performBriefing(){

        Set<Map.Entry<Long, Task>> ss = CNode.activetasks.entrySet();
        double sest = 0.0;
        for (Map.Entry<Long, Task> tuple : ss) {
            Count c = (Count) tuple.getValue().getMetric("Count");
            long v = c.getlocked();
            sest += CNode.tunnel.briefing(tuple.getKey(), v);
        }
        CNode.tunnel.sendbriefing(sest);

    }
    public static Task getTask() {
        return CNode.getTask(Thread.currentThread().getId());
    }

    public static Task getTask(long tid) {
        return activetasks.get(tid);
    }

    public static void terminate() {
        Publisher.off();
        worker.interrupt();
    }

    static class Publisher extends Thread {

        private static final AtomicBoolean active = new AtomicBoolean(true);

        public static void off() {
            active.getAndSet(false);
        }

        @Override
        public void run() {

            while (active.get()) {

                try {
                    Task itask = CNode.taskq.take();
                    String tsk = itask.getKey();
                    Map<String, String> row;

                    row = requestTable.get(tsk);
                    if (row == null)
                        row = new HashMap<>();

                    for (String mname : itask.metricsK()) {

                        Count c = itask.getMetric(mname);

                        if (c.valid()) {
                            if (row.containsKey(mname)) {
                                Count cold = Count.fromString(row.get(mname));
                                cold.aggregate(c);
                                c = cold;
                            }
                            String bin = c.toBinary();

                            if( mname.equals("Count") )
                                CNode.tunnel.stream(tsk, bin);

                            row.put(mname, c.toBinary());
                        }
                    }
                    requestTable.put(tsk, row);

                } catch (InterruptedException e) {
                    Logger.log(e.getMessage());
                } catch (IOException e) {
                    Logger.log(e.getMessage());
                } catch (ClassNotFoundException e) {
                    Logger.log(e.getMessage());
                }
            }

        }
    }

    static class EndPoint extends Thread {
        private PrintWriter out=null;
        private BufferedReader in;

        private final BlockingQueue<String> lbq
                = new LinkedBlockingQueue<>();

        private final ConcurrentHashMap<Long, Object[]> deltaset
                = new ConcurrentHashMap<>();

        private static int LOAD = 0;
        private static int SOLVER = 1;
        private static int TURN = 2;

        public EndPoint() {
            this.start();
        }

        public void increment(long tid, String solver) {

            Object[] v = new Object[3];
            v[LOAD] = new AtomicLong(0L);
            v[SOLVER] = solver;
            v[TURN] = new AtomicInteger(0);

            deltaset.put(
                    tid,
                    v
            );

            lbq.add("queue:"+"1");
        }

        void sendbriefing(double value){
            lbq.add("loadreport:" + ((long)value));
        }

        public double briefing(long tid, double value){
            Object[] v = deltaset.get(tid);

            AtomicLong al = (AtomicLong)v[LOAD];
            String solver = (String)v[SOLVER];

            long delta = ((long)value-al.get());
            
            double est = senddelta(tid, delta, solver,1);

            ((AtomicInteger)v[TURN]).getAndIncrement();
            al.addAndGet(delta);
            return est;
        }

        public void stream(String tsk, String c){
            lbq.add("data:"+tsk+":"+c);
        }
        private void recovery(){

            Thread th = Thread.currentThread();

            //####################### BADASS MODE ON :-
            int p = th.getPriority();
            try {
                th.setPriority(Thread.MAX_PRIORITY);
            }catch ( SecurityException e){
                Logger.log(e.getMessage());
            }
            //####################### BADASS MODE ON :-

            Set<Map.Entry<Long,Object[]>> dcache = deltaset.entrySet();
            this.lbq.clear();
            long val = 0;

            for( Map.Entry<Long,Object[]>  me: dcache ){

                Task t = CNode.activetasks.get(me.getKey());

                if( t != null ){
                    Object[] v = me.getValue();
                    AtomicLong al = (AtomicLong) v[LOAD];
                    val += al.get();
                    String k = t.getKey();
                    lbq.add("fault-key:" + k);
                }
            }

            int q = CNode.activetasks.size();
            lbq.add("loadreport:" + val);
            lbq.add("queue:" + q);

            //####################### BADASS MODE OFF
            try{
                th.setPriority(p);
            }catch ( SecurityException e){
                Logger.log(e.getMessage());
            }
            //####################### BADASS MODE OFF
        }

        private double senddelta(long tid, long delta, String solver, int turn){
            double est = (double)delta;
            int fixed = 0;
            //String solver = "BFS";
            if(turn == 0)
                fixed = 1;

            if( solver != null ) {
                switch (solver) {
                    case "BFS":
                        est = delta * 12.88852179 + fixed*14068.78484095;
                        break;

                    case "CP":
                        est = delta * 14.16131419 + fixed*19312.86569091;
                        break;

                    case "DLX":
                        // delta *0.005052572765698524 + fixed*109378.21280323979
                        est = ((delta*0.00430531+ fixed*75100.9879752195)*0.09105526 + fixed*556.56763109) * 12.88852179 + fixed*14068.78484095;
                        break;
                }
                return est;
            }
            return 0;
        }

        public void decrement(long tid, long load) {
            lbq.add("queue:"+"-1");
            Object[] v = deltaset.remove(tid);

            long tmp = load - ((AtomicLong)v[LOAD]).get();

            // ((AtomicInteger)v[TURN]).get()
            double est = senddelta(tid, tmp, (String)v[SOLVER], 0);

            sendbriefing(est);
        }

        public void run() {
            boolean downed = false;

            while(true) {
                try {
                    ServerSocket ssc = (new ServerSocket(
                            CloudStandart.inbound_channel_port
                    ));

                    Socket sc = ssc.accept();

                    ssc.close();

                    Logger.log("Initiating tunnel");
                    sc.setTcpNoDelay(true);
                    boolean go = true;

                    this.out = new PrintWriter(
                            sc.getOutputStream()
                    );

                    this.in = new BufferedReader(
                           new InputStreamReader(
                                   sc.getInputStream()
                           )
                    );

                    if(downed) {
                        this.recovery();
                        downed = false;
                    }

                    String message;
                    int mcounter = 0;

                    while (go) {
                        message = this.lbq.poll(20, TimeUnit.SECONDS);

                        if (message != null) {
                            this.out.println(message);
                            this.out.flush();
                        } else {
                            Logger.log("+ sch briefing.");
                            CNode.performBriefing();
                            Logger.log("- sch briefing.");
                            if( this.in.ready() ) {
                                mcounter = 0;
                                String[] args = this.in.
                                        readLine().split(":");

                                switch (args[0]) {
                                    case "confirmation":
                                        Logger.log("confirmation");
                                        break;
                                }
                            }else{
                                mcounter++;
                            }
                            if( mcounter > 2*3 ){

                                go = false;
                                downed = true;
                                sc.close();
                                Logger.log("Load Balancer most likely went down.");
                            }
                        }
                    }
                    //Logger.log("Tunnel open");

                } catch (IOException e) {
                    Logger.log(e.toString());
                    Logger.log("Load Balancer most likely went down. Exception");
                    downed=true;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    }
}
