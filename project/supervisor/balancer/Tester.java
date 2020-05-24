package supervisor.balancer;



import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Date;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.util.StopWatch;
import supervisor.balancer.CMonitor;
import supervisor.balancer.CMonitor;
import static supervisor.balancer.CMonitor.vmstates;
import supervisor.balancer.LoadBalancer;
import supervisor.balancer.LoadBalancer;
import supervisor.balancer.estimation.Estimator;
import supervisor.balancer.estimation.Oracle;
import supervisor.server.Count;
import supervisor.storage.TaskStorage;
import supervisor.util.CloudStandart;

/*
Tester procedure to gather
frequency : n_int : %cpu 
For 1 webserver
*/
public class Tester {

    static LinkedList<Point> rec_data = new LinkedList<Point>();
    static AmazonEC2      ec2;
    static AmazonCloudWatch cloudWatch;
    
    static ConcurrentHashMap<String,Result> results = new ConcurrentHashMap<String,Result>();
    static ExecutorService es;
    private static Instance instance;
    
    static Oracle oracle; 
    private static CMonitor.Endpoint endpoint;
    
    
    private static class Result {
        
        long start_time;
        long duration;
        double estimate;
        double avg_cpu;
        String start_time_readable;
        long vm_total_load;
        double count;

        public Result() {
        }
    }
    
    private static void init() throws Exception {

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
        ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-east-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

        cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion("us-east-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
    }

    static class Request extends Thread {
        
        private final String threadId;

        public Request(String id) {
            this.threadId = id;
        }

        @Override
        public void run() {
            HttpClient httpclient = HttpClients.custom()
                .setRedirectStrategy(new LaxRedirectStrategy())
                .build();
            HttpPost httppost = new HttpPost("http://localhost:8000/sudoku?s=BFS&un=81&n1=9&n2=9&i=SUDOKU_PUZZLE_9x9_101&board=[[2,0,0,8,0,5,0,9,1],[9,0,8,0,7,1,2,0,6],[0,1,4,2,0,3,7,5,8],[5,0,1,0,8,7,9,2,4],[0,4,9,6,0,2,0,8,7],[7,0,2,1,4,9,3,0,5],[1,3,7,5,0,6,0,4,9],[4,2,5,0,1,8,6,0,3],[0,9,6,7,3,4,0,1,2]]");

            // Request parameters and other properties.
            //List<NameValuePair> params = new ArrayList<NameValuePair>(2);
            //params.add(new BasicNameValuePair("param-1", "12345"));
            //params.add(new BasicNameValuePair("param-2", "Hello!"));
            //httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
            
            httppost.addHeader("Cache-Control","no-cache");
            httppost.addHeader("Connection","keep-alive");
//            httppost.addHeader("Content-Type","text/plain;charset=UTF-8");
//            httppost.addHeader("Accept","*/*");

            StopWatch watch = new StopWatch();
            HttpResponse response = null;
            HttpEntity entity = null;
            
            long start_time = System.currentTimeMillis();
            String start_time_readable = Calendar.getInstance().getTime().toString();
            try {
                watch.start();
                //Execute and get the response.
                response = httpclient.execute(httppost);
                //assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
                entity = response.getEntity();
                System.out.println(threadId+" responded, response size: "+entity.getContent().available());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                watch.stop();
            }

            long rec_time = watch.getTotalTimeMillis();

            String key = CloudStandart.makeKey(httppost.getURI().getQuery());
            if (entity != null) {
                try (InputStream instream = entity.getContent()) {
                    // store results
                    Result r = new Result();
                    r.duration = rec_time;
                    r.start_time = start_time;
                    r.estimate = oracle.predict(key);
                    r.start_time_readable = start_time_readable;
                    r.avg_cpu = getLastMinAvgCpu();
                    r.vm_total_load = endpoint.getLoad();
                    String solver = "BFS";
                    while (!endpoint.last_count.containsKey(key) || endpoint.last_count.get(key).size()<1){
                        Thread.sleep(1000);
                    }
                    r.count = Estimator.transform(endpoint.last_count.get(key).pollFirst().getlocked(),solver);
                    results.put(threadId, r);
                    
                } catch (IOException ex) {
                    Logger.getLogger(Tester.class.getName()).log(Level.SEVERE, null, ex);
                } catch (UnsupportedOperationException ex) {
                    Logger.getLogger(Tester.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Tester.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            System.out.println(threadId+" finished");
            Thread.yield();
        }
    }
    public static void request(String id) {
        Request r = new Request(id);
        es.execute(r);
        //r.start();
    }
    public static void main(String[] args) throws Exception {
				boolean startInstance = false;
        System.out.println("===========================================");
        System.out.println("Testing CPU usage - v1.1");
        System.out.println("- Initializing Load Balancer and client");
        System.out.println("===========================================");

        
        init();
        
        
        DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
        List<Reservation> reservations = describeInstancesResult.getReservations();
        Set<Instance> instances = new HashSet<Instance>();

        System.out.println("total reservations = " + reservations.size());
        for (Reservation reservation : reservations) {
            instances.addAll(reservation.getInstances());
        }
        instance = (Instance) instances.toArray()[0];
        
        //TaskStorage.init(false);
        //oracle = new Oracle(50);
            
        // Starting the load balancer with auto-scaling disabled
        
        CMonitor.skip_autoscale = true;
        Thread t = new Thread(
            new Runnable() {
                @Override
                public void run() {
                    String[] s = {"false", "0.25", "0.75", "50", "2"};
                    LoadBalancer.main(s);
                }
            }
        );
        t.start();
        
        Thread.sleep(20000);
        
        while(CMonitor.requestTable==null){
            Thread.sleep(500);
        }
        oracle = CMonitor.requestTable;
        
        while(CMonitor.vmstates.size()<1 || CMonitor.activevms.size()<1){
            Thread.sleep(500);
        }
        
                
        for (CMonitor.Endpoint e : CMonitor.vmstates.values()) {
            endpoint = e;
            break;
        }
        
        
        
        System.out.println("===========================================");
        System.out.println("Starting the requests");
        System.out.println("===========================================");
        

        for (long freq = 10000; freq<40000; freq=freq*2){
            es = Executors.newCachedThreadPool();
            System.out.println("Tester.main(): freq = "+freq);
            for (int i = 0; i<5; i++){
                System.out.println(i);
                request(""+freq+"-"+i);
                Thread.sleep(freq);
            }
            
            System.out.println("===========================================");
            System.out.println("Waiting for responses...");
            System.out.println("===========================================");

            es.shutdown();
            while(!es.isTerminated()){
                System.out.println("Active threads: "+Thread.activeCount());
                //Thread.sleep(10000);
                es.awaitTermination(10, TimeUnit.SECONDS);
            }
            System.out.println("awaiting for last threads to finish...");

            //boolean finished = es.awaitTermination(10, TimeUnit.MINUTES);
            // all tasks have finished or the time has been reached.
            
            System.out.println("===========================================");
            System.out.println("Storing results in csv");
            System.out.println("===========================================");

            csvWriter(results);
        }
        
//        System.out.println("===========================================");
//        System.out.println("Waiting for responses...");
//        System.out.println("===========================================");
//        
//        es.shutdown();
//        while(Thread.activeCount() > 3){
//            System.out.println("Active threads: "+Thread.activeCount());
//            Thread.sleep(10000);
//        }
//        System.out.println("awaiting for last threads to finish...");
//        
//        boolean finished = es.awaitTermination(10, TimeUnit.MINUTES);
//        // all tasks have finished or the time has been reached.
//        
//        
//        System.out.println("===========================================");
//        System.out.println("Storing results in csv");
//        System.out.println("===========================================");
//        
//        csvWriter(results);
        

        // http://ec2-52-201-237-28.compute-1.amazonaws.com:8000/sudoku?s=BFS&un=81&n1=9&n2=9&i=SUDOKU_PUZZLE_9x9_101&board=[[2,0,0,8,0,5,0,9,1],[9,0,8,0,7,1,2,0,6],[0,1,4,2,0,3,7,5,8],[5,0,1,0,8,7,9,2,4],[0,4,9,6,0,2,0,8,7],[7,0,2,1,4,9,3,0,5],[1,3,7,5,0,6,0,4,9],[4,2,5,0,1,8,6,0,3],[0,9,6,7,3,4,0,1,2]]
        
        
        try {

//            DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
//            List<Reservation> reservations = describeInstancesResult.getReservations();
//            Set<Instance> instances = new HashSet<Instance>();

            System.out.println("total reservations = " + reservations.size());
            for (Reservation reservation : reservations) {
                instances.addAll(reservation.getInstances());
            }
            System.out.println("total instances = " + instances.size());
            /* total observation time in milliseconds */
            long offsetInMilliseconds = 1000 * 60 * 10;
            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            List<Dimension> dims = new ArrayList<Dimension>();
            dims.add(instanceDimension);
            for (Instance instance : instances) {
                String name = instance.getInstanceId();
                String state = instance.getState().getName();
                if (state.equals("running")) { 
                    System.out.println("running instance id = " + name);
                    instanceDimension.setValue(name);
                    
                    List<String> metrics = new ArrayList<String>();
                    metrics.add("Average");
                    metrics.add("Maximum");
                    metrics.add("Minimum");
            GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                    .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                    .withNamespace("AWS/EC2")
                    .withPeriod(60)
                    .withMetricName("CPUUtilization")
                    .withStatistics(metrics)
                    .withDimensions(instanceDimension)
                    .withEndTime(new Date());
                     GetMetricStatisticsResult getMetricStatisticsResult = 
                         cloudWatch.getMetricStatistics(request);
                     List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
                     for (Datapoint dp : datapoints) {
                       System.out.println(" CPU utilization for instance " + name +
                           " = " + dp.getAverage());
                       System.out.println(" CPU max = " + dp.getMaximum());
                       System.out.println(" CPU min = " + dp.getMinimum());
                       rec_data.add(new Point(dp.getAverage()));
                     }
                 }
                 else {
                    System.out.println("instance id = " + name);
                 }
                System.out.println("Instance State : " + state +".");
            }
            
            
            
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    private static class Point {
        
        private static int id_count=0;
        
        final int id;
        
        double average;

        public Point(double av) {
            this.id = id_count++;
            this.average = av;
        }
    }
    
    
    public static Double getLastMinAvgCpu(){
        
        try {

            
            /* total observation time in milliseconds */
            long offsetInMilliseconds = 1000 * 60;
            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            List<Dimension> dims = new ArrayList<Dimension>();
            dims.add(instanceDimension);
            
            
                String name = instance.getInstanceId();
                String state = instance.getState().getName();
                if (state.equals("running")) { 
                    instanceDimension.setValue(name);
                    
                    List<String> metrics = new ArrayList<String>();
                    metrics.add("Average");
                    metrics.add("Maximum");
                    metrics.add("Minimum");
            GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                    .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                    .withNamespace("AWS/EC2")
                    .withPeriod(60)
                    .withMetricName("CPUUtilization")
                    .withStatistics(metrics)
                    .withDimensions(instanceDimension)
                    .withEndTime(new Date());
                     GetMetricStatisticsResult getMetricStatisticsResult = 
                         cloudWatch.getMetricStatistics(request);
                     List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
                     for (Datapoint dp : datapoints) {
                       return dp.getAverage();
                       
                     }
                 }
                 else {
                    System.out.println("instance not running id = " + name);
                 }
            
            
            
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
        return -1d;
    }
    
    public static void csvWriter(Map<String,Result> myHashMap) throws IOException {
        String eol = System.getProperty("line.separator");

        try (Writer writer = new FileWriter("results.csv")) {
          for (Map.Entry<String,Result> entry : myHashMap.entrySet()) {
            Result r = entry.getValue();
            writer.append(entry.getKey())
                  .append(',')
                  .append(String.valueOf(r.start_time))
                  .append(',')
                  .append(r.start_time_readable)
                  .append(',')
                  .append(String.valueOf(r.duration))
                  .append(',')
                  .append(Double.toString(r.estimate))
                  .append(',')
                  .append(Double.toString(r.avg_cpu))
                  .append(',')
                  .append(String.valueOf(r.vm_total_load))
                  .append(',')
                  .append(String.valueOf(r.count))
                  .append(eol);
          }
        } catch (IOException ex) {
          ex.printStackTrace(System.err);
        }
    }


}