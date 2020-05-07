package supervisor.server;

import supervisor.storage.TaskStorage;
import supervisor.util.CloudStandart;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CNode {
    /**
     *  This is the Server Hypervisor class.
     * */

    /* tasks que estão a decorrer, indexadas pelo thread que está a correr. */
    private static final Map<Long, Task> activetasks
            = new ConcurrentSkipListMap<>();

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
            t.addMetric("Overhead", new Count());

            activetasks.put(Thread.currentThread().getId(), t);

            CNode.tunnel.increment(
                    Thread.currentThread().getId());
        }
        //Logger.log("start " + Thread.currentThread().getId());
    }

    /** Termina um novo pedido. */
    public static void finishTask() {
        Long tid = Thread.currentThread().getId();
        Task t = activetasks.remove(tid);

        //Logger.log("Finish task:" + t.getKey());
        t.wrap();
        taskq.add(t);

        CNode.tunnel.decrement(tid,
                ((Count)t.getMetric("Count")).getlocked());
    }
    public static void performBriefing(){
        for( Map.Entry<Long,Task> tuple : CNode.activetasks.entrySet()){
            Count c = (Count)tuple.getValue().getMetric("Count");
            CNode.tunnel.briefing(tuple.getKey(),c.getlocked());
        }
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
                    //Logger.log("Publisher:Halt");
                    Task itask = CNode.taskq.take();
                    String tsk = itask.getKey();

                    Map<String, String> row;

                    row = requestTable.get(tsk);
                    if( row == null)
                        row = new HashMap<>();


                    for (String mname : itask.metricsK()) {

                        Metric m = itask.getMetric(mname);
                        Count c = (Count) m;

                        if (c.valid()) {
                            if (row.containsKey(mname)) {
                                Count cold = Count.fromString(row.get(mname));
                                cold.aggregate(c);
                                c = cold;
                            }
                            row.put(mname, c.toBinary());
                        }
                    }
                    requestTable.put(tsk, row);

                } catch (InterruptedException e) {
                    //Logger.log(e.getMessage());
                } catch (IOException e) {
                    //Logger.log(e.getMessage());
                } catch (ClassNotFoundException e) {
                    //Logger.log(e.getMessage());
                }
            }

        }
    }

    static class EndPoint extends Thread {
        private PrintWriter out=null;
        private final BlockingQueue<String> lbq = new LinkedBlockingQueue<>();
        private final ConcurrentHashMap<Long, AtomicLong> deltaset = new ConcurrentHashMap<>();
        public EndPoint() {
            this.start();
        }

        public void increment(long tid) {

            deltaset.put(
                    tid,
                    new AtomicLong(0L)
            );
            //Logger.log("Increment");
            lbq.add("queue:"+"1");
            //this.flush();
        }

        public void briefing(long tid, double value){
            AtomicLong v = deltaset.get(tid);

            long delta = ((long)value-deltaset.get(tid).get());

            senddelta(tid, delta);

            v.addAndGet(delta);
        }


        private void senddelta(long tid, long delta){
            double est = 0.0;

            String solver = CNode.getTask(tid)
                    .getKey().split(":")[0];

            switch (solver){
                case "BFS":
                    est = delta*12.88852179+14068.78484095;
                    break;

                case "CP":
                    est = delta*14.16131419+19312.86569091;
                    break;

                case "DLX":
                    est = delta*24.39689662-1392680.19952047;
                    break;
            }

            lbq.add("loadreport:"+((long)est));
        }

        public void decrement(long tid, long load) {
            lbq.add("queue:"+"-1");

            this.senddelta(tid,load);
            deltaset.remove(tid);
        }

        public void run() {
            try {
                Socket sc = (new ServerSocket(CloudStandart.inbound_channel_port)).accept();

                this.out = new PrintWriter(
                        sc.getOutputStream()
                );

                String message;

                while (true){
                    message = this.lbq.poll(20, TimeUnit.SECONDS);
                    if( message == null){
                        CNode.performBriefing();
                    }else{
                        this.out.println(message);
                        this.out.flush();
                    }
                }
                //Logger.log("Tunnel open");

            } catch (UnknownHostException e) {
                //Logger.log(e.toString());
            } catch (IOException e) {
                //Logger.log(e.toString());

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
