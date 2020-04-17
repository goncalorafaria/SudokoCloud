package supervisor.server;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import supervisor.util.Logger;


import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;

public class CNode {
    /*
    *  This is the Server Hypervisor class.
    * */
    private static AmazonEC2 ec2;

    private static Map< Long, Task > activetasks
            = new ConcurrentSkipListMap<>();
    /* tasks que estão a decorrer */

    private static Map<String, BlockingQueue<Task>> oldtasks
            = new ConcurrentSkipListMap<>();
    /* tasks que têm de se ter as metricas publicadas para a db.*/

    private static BlockingQueue<String> keyq = new LinkedBlockingQueue();
    /* Fila de espera para publicar as metricas das tasks terminadas */

    public static void init() throws AmazonClientException {

        AWSCredentials credentials = null;

        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }

        ec2 = AmazonEC2ClientBuilder.standard().withRegion( CloudStandart.region )
                .withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

    }

    /* Associa um novo pedido a um thread. */
    public static void registerTask(String taskkey){
        Logger.log("Registering task:" + taskkey);

        Task t = new Task(taskkey);
        t.addMetric("Count",new Count());

        activetasks.put(Thread.currentThread().getId(), t );


        if( !oldtasks.containsKey(t.getKey()) )
            oldtasks.put(t.getKey(), new LinkedBlockingQueue());

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
}
