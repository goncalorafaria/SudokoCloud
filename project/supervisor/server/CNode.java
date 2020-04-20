package supervisor.server;

import supervisor.storage.LocalStorage;
import supervisor.storage.Storage;
import supervisor.util.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class CNode {
    /*
    *  This is the Server Hypervisor class.
    * */

    private static Map< Long, Task > activetasks
            = new ConcurrentSkipListMap<>();
    /* tasks que estão a decorrer */

    private static Map<String, Task.Group > oldtasks
            = new ConcurrentSkipListMap<>();
    /* tasks que têm de se ter as metricas publicadas para a db.*/

    private static BlockingQueue<String> keyq = new LinkedBlockingQueue();
    /* Fila de espera para publicar as metricas das tasks terminadas */

    private static Storage<String> requestTable;

    private static Publisher worker;// thread that publishes the collected metrics.

    public static void init() {

        try{
            CNode.requestTable = new LocalStorage<String>("RequestTable");
        }catch(Exception e){
            Logger.log("error loading RequestTable");
            Logger.log(e.toString());
        }

        CNode.worker = new Publisher();
        CNode.worker.start();

    }

    /* Associa um novo pedido a um thread. */
    public static void registerTask(String taskkey){
        registerTask( taskkey,false);
    }

    public static void registerTask(String taskkey, boolean overhead ){
        Logger.log("Registering task:" + taskkey);

        Task t = new Task(taskkey);
        t.addMetric("Count",new Count());
        t.addMetric("Overhead", new Count());

        activetasks.put(Thread.currentThread().getId(), t );


        if( !oldtasks.containsKey(t.getKey()) )
            oldtasks.put(t.getKey(), new Task.Group());

        Logger.log("start " +  Thread.currentThread().getId());
    }

    public static void finishTask(){

        Long tid = Thread.currentThread().getId();
        Task t = activetasks.remove(tid);

        Logger.log("Finish task:" + t.getKey());

        oldtasks.get(t.getKey()).add(t);
        keyq.add(t.getKey());/* shedule for publishing*/

        Logger.log("(Explain):" + ((Count)t.getMetric("Count")).explain());
        Logger.log("(Metrics):" + t.getMetric("Count"));

    }

    public static Task getTask(){
        return activetasks.get(Thread.currentThread().getId());
    }

    public static void terminate(){
        Publisher.off();
        worker.interrupt();
    }

    static class Publisher extends Thread {

        private static AtomicBoolean active = new AtomicBoolean(true);

        public static void off(){
            active.getAndSet(false);
        }

        @Override
        public void run() {

            while( active.get() ){

                try {
                    Logger.log("Publisher:Halt");
                    String tsk = CNode.keyq.take();
                    Logger.log("Publisher:Working");
                    Set<Task> taskr = new HashSet<>();
                    oldtasks.get(tsk).drainTo(taskr);

                    for( Task itask : taskr ) {
                        Map<String, String> row;

                        if (requestTable.contains(tsk)) {
                            row = requestTable.get(tsk);
                        } else {
                            row = new HashMap<>();
                        }

                        for (String mname : itask.metricsK()) {

                            Metric m = itask.getMetric(mname);
                            Logger.log(mname);
                            Count c = (Count) m;

                            if(c.valid()) {
                                if( row.containsKey(mname) ) {
                                    Count cold = Count.fromString(row.get(mname));
                                    c.aggregate(cold);
                                }
                                row.put(mname, c.toBinary());
                            }
                        }
                        requestTable.put(tsk, row);
                    }
                    oldtasks.remove(tsk);

                }catch(InterruptedException e){
                    Logger.log(e.getMessage());
                }catch (IOException e){
                    Logger.log(e.getMessage());
                }catch (ClassNotFoundException e){
                    Logger.log(e.getMessage());
                }
            }

        }
    }

}
