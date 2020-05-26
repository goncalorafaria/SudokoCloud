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
        private int active_requests;

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

    static String q1 = "http://localhost:8000/sudoku?s=BFS&un=81&n1=9&n2=9&i=SUDOKU_PUZZLE_9x9_101&board=[[2,0,0,8,0,5,0,9,1],[9,0,8,0,7,1,2,0,6],[0,1,4,2,0,3,7,5,8],[5,0,1,0,8,7,9,2,4],[0,4,9,6,0,2,0,8,7],[7,0,2,1,4,9,3,0,5],[1,3,7,5,0,6,0,4,9],[4,2,5,0,1,8,6,0,3],[0,9,6,7,3,4,0,1,2]]";

    //static String q2 = "http://localhost:8000/sudoku?s=BFS&un=40&n1=9&n2=9&i=SUDOKU_PUZZLE_9x9_105&board=[[2,0,0,3,0,9,0,8,6],[7,0,5,0,1,2,3,0,9],[0,9,8,5,0,6,1,7,2],[4,0,6,0,9,3,8,5,1],[0,3,7,1,5,8,2,6,4],[8,5,1,2,6,4,9,3,7],[1,7,9,6,3,5,4,2,8],[6,8,3,4,2,1,7,9,5],[5,4,2,9,8,7,6,1,3]]";
    
    static String q3 = "http://localhost:8000/sudoku?s=BFS&un=20&n1=9&n2=9&i=SUDOKU_PUZZLE_9x9_102&board=[[2,0,0,4,0,6,0,7,5],[8,0,3,0,2,9,4,0,1],[0,4,7,1,3,5,2,8,9],[7,8,5,3,6,2,9,1,4],[3,6,9,8,4,1,7,5,2],[1,2,4,9,5,7,6,3,8],[5,7,2,6,9,8,1,4,3],[4,1,8,2,7,3,5,9,6],[9,3,6,5,1,4,8,2,7]]";
    
    //static String q4 = "http://localhost:8000/sudoku?s=BFS&un=10&n1=9&n2=9&i=SUDOKU_PUZZLE_9x9_103&board=[[9,0,0,1,0,3,0,8,6],[3,2,6,8,9,5,1,4,7],[1,4,8,7,2,6,9,3,5],[6,8,9,4,7,1,5,2,3],[7,3,1,6,5,2,4,9,8],[4,5,2,9,3,8,6,7,1],[8,6,3,2,1,4,7,5,9],[2,1,7,5,8,9,3,6,4],[5,9,4,3,6,7,8,1,2]]";
    
    
    //static String q5 = "http://localhost:8000/sudoku?s=BFS&un=256&n1=16&n2=16&i=SUDOKU_PUZZLE_16x16_01&board=[[2,0,0,5,0,16,0,4,12,3,0,7,0,6,15,1],[0,12,0,10,3,6,0,11,13,5,8,1,0,7,0,2],[1,14,6,7,0,2,5,12,0,9,0,16,3,13,0,4],[16,13,8,3,0,1,10,7,4,6,0,15,0,11,5,12],[12,2,0,9,10,14,0,13,0,1,5,3,6,4,0,7],[6,7,0,11,5,12,8,16,0,15,4,2,14,10,3,13],[0,5,4,13,0,11,0,3,16,12,0,10,0,9,2,15],[0,8,10,15,4,7,2,9,6,14,13,11,1,12,0,5],[13,9,0,16,7,8,14,10,0,4,0,6,12,5,1,11],[5,4,14,6,0,13,0,1,10,16,11,8,0,3,7,9],[7,1,0,12,16,4,0,15,5,13,9,14,0,8,10,6],[10,15,0,8,0,5,11,6,2,7,1,12,4,14,0,16],[0,10,13,14,0,9,0,8,15,2,6,4,5,16,12,3],[15,3,0,4,12,10,6,5,1,8,16,13,11,2,0,14],[8,6,0,1,0,3,16,2,0,11,12,9,7,15,0,10],[0,16,12,2,11,15,4,14,7,10,0,5,13,1,6,8]]";
    
    static String q6 = "http://localhost:8000/sudoku?s=BFS&un=125&n1=16&n2=16&i=SUDOKU_PUZZLE_16x16_02&board=[[11,0,0,12,0,14,0,2,7,16,0,6,0,5,1,3],[0,16,0,6,10,12,0,1,2,11,14,5,0,13,0,15],[1,4,3,14,0,7,5,9,0,10,0,15,2,11,0,12],[7,5,2,13,0,16,15,4,3,9,0,12,0,14,6,8],[2,15,0,1,8,13,0,3,0,14,9,7,16,10,0,5],[13,12,0,10,9,11,4,7,0,5,15,3,14,8,2,6],[0,8,14,5,0,1,0,16,10,13,0,11,0,9,12,7],[0,9,7,11,14,5,2,10,12,6,16,8,15,1,4,13],[15,1,11,2,4,6,16,13,5,8,7,10,12,3,14,9],[12,7,5,4,2,9,1,15,14,3,11,13,8,6,10,16],[14,6,9,3,7,8,10,5,16,2,12,1,13,4,15,11],[16,13,10,8,12,3,14,11,15,4,6,9,7,2,5,1],[6,2,1,16,3,4,7,8,13,15,5,14,11,12,9,10],[5,3,4,15,16,10,9,6,11,12,8,2,1,7,13,14],[10,14,13,7,5,2,11,12,9,1,3,16,6,15,8,4],[8,11,12,9,1,15,13,14,6,7,10,4,5,16,3,2]]";
    
    //static String q7 = "http://localhost:8000/sudoku?s=BFS&un=625&n1=25&n2=25&i=SUDOKU_PUZZLE_16x16_05&board=[[1,0,0,20,0,24,0,15,17,10,0,8,0,14,22,6,0,9,0,16,2,7,0,3,5],[5,2,19,0,24,0,22,12,9,3,16,0,7,20,17,0,21,0,14,13,10,0,4,1,15],[17,14,0,6,3,25,21,5,0,20,0,10,2,1,13,4,0,24,23,15,0,12,0,22,19],[16,7,21,0,18,4,2,0,11,23,5,19,15,0,12,10,20,17,22,1,9,0,25,14,3],[0,13,0,12,22,14,0,18,0,16,23,9,0,4,3,7,5,19,11,2,8,24,20,21,17],[12,0,11,10,6,0,13,23,24,15,7,0,8,0,21,25,19,3,4,9,22,14,2,0,18],[0,19,13,21,9,16,0,25,12,2,15,3,0,11,20,14,0,23,18,22,1,10,0,24,6],[4,17,14,0,7,0,3,22,21,19,25,1,24,2,23,0,13,0,10,6,16,0,8,0,12],[22,3,24,15,23,18,20,11,1,7,0,13,4,6,14,16,2,12,21,8,5,19,0,25,9],[20,0,2,0,5,10,8,0,14,17,9,22,12,0,19,0,11,15,7,24,3,23,21,13,4],[0,25,3,5,10,2,0,14,4,18,22,15,0,19,24,20,7,1,0,21,0,16,6,8,11],[14,0,1,24,12,0,16,0,15,6,2,7,20,25,10,3,4,0,17,11,21,9,5,18,22],[7,8,18,11,17,20,0,21,22,9,0,4,0,12,16,2,0,14,19,5,25,13,15,10,23],[2,22,16,9,21,0,11,7,10,25,8,0,14,13,6,12,24,18,15,23,19,0,1,0,20],[6,15,0,19,4,13,12,3,0,1,18,11,23,21,9,8,0,16,25,10,7,17,0,2,14],[21,18,12,0,16,7,10,0,3,13,1,24,22,0,4,11,15,6,20,14,17,0,23,5,25],[0,24,8,13,1,6,25,4,0,12,17,14,3,7,18,23,16,22,0,19,0,21,10,15,2],[23,10,22,7,15,0,5,0,18,14,6,20,16,0,11,17,1,0,13,25,4,3,19,0,24],[25,5,6,14,11,1,0,2,8,24,0,21,0,23,15,9,0,10,12,4,20,18,22,16,7],[3,20,17,0,19,22,15,16,23,11,12,0,10,5,2,0,18,8,24,7,6,1,14,0,13],[19,6,0,22,8,15,18,1,0,4,14,2,9,3,7,13,10,11,16,20,0,5,0,17,21],[15,4,5,17,14,3,7,24,19,8,20,23,11,10,25,0,9,21,1,12,13,0,18,6,16],[11,12,7,16,20,23,0,17,2,21,24,18,0,15,1,19,25,5,0,3,0,22,9,4,10],[18,0,25,1,2,11,14,10,13,22,4,0,21,16,5,24,23,0,6,17,15,20,3,0,8],[0,21,10,3,13,12,0,20,16,5,19,17,0,22,8,15,0,4,0,18,23,25,11,7,1]]";
    //static String q7 = "http://localhost:8000/sudoku?s=BFS&un=81&n1=9&n2=9&i=SUDOKU_PUZZLE_9x9_101&board=[[2,0,0,8,0,5,0,9,1],[9,0,8,0,7,1,2,0,6],[0,1,4,2,0,3,7,5,8],[5,0,1,0,8,7,9,2,4],[0,4,9,6,0,2,0,8,7],[7,0,2,1,4,9,3,0,5],[1,3,7,5,0,6,0,4,9],[4,2,5,0,1,8,6,0,3],[0,9,6,7,3,4,0,1,2]]";
   
    //static String q8 = "http://localhost:8000/sudoku?s=BFS&un=300&n1=25&n2=25&i=SUDOKU_PUZZLE_16x16_06&board=[[24,0,0,9,0,21,0,19,4,10,0,20,0,1,16,7,0,18,0,13,5,15,0,8,6],[14,3,8,0,12,0,24,15,11,2,21,0,19,13,9,0,20,0,1,6,10,0,23,16,25],[16,7,0,11,4,13,25,1,0,18,0,22,15,6,5,9,0,17,10,23,0,12,0,21,24],[22,5,1,0,19,16,6,0,23,7,4,24,14,0,25,15,21,12,8,11,17,0,20,18,3],[0,6,0,10,17,20,0,3,0,5,11,18,0,8,23,16,24,4,19,25,1,13,22,7,9],[20,0,3,15,5,0,16,23,21,13,1,0,22,0,7,18,6,19,14,10,24,11,4,0,17],[0,17,9,21,24,10,0,14,1,3,19,13,0,11,18,2,0,23,22,12,25,7,0,15,5],[13,14,25,0,11,0,18,17,7,19,10,12,9,4,21,0,3,0,15,8,22,0,2,0,16],[12,16,23,8,1,25,5,22,15,11,0,17,3,24,20,13,4,9,21,7,6,10,0,19,14],[7,0,22,0,10,2,8,0,6,12,23,5,16,0,14,0,25,24,20,17,13,21,3,9,1],[0,20,5,23,2,14,0,10,13,4,16,6,0,19,17,3,8,7,0,15,0,24,25,22,18],[3,0,6,1,8,0,15,0,20,23,24,2,10,18,13,14,9,0,11,22,19,17,16,4,7],[10,24,17,16,18,7,9,25,19,8,22,3,21,12,11,6,5,20,4,2,23,14,13,1,15],[21,19,4,22,15,18,11,16,2,24,9,25,7,14,8,23,10,13,17,1,20,5,6,3,12],[11,13,12,14,7,22,3,6,17,1,15,23,20,5,4,19,18,25,16,24,9,8,10,2,21],[5,11,10,24,21,6,19,18,22,17,13,4,12,3,2,8,15,14,23,16,7,9,1,25,20],[2,9,18,20,14,1,10,8,16,21,17,19,11,7,24,4,22,3,25,5,12,6,15,23,13],[17,15,7,12,3,4,23,2,9,20,5,16,25,22,6,10,11,1,13,18,21,19,24,14,8],[19,1,13,4,23,3,12,24,14,25,8,10,18,9,15,20,17,6,7,21,2,16,5,11,22],[6,8,16,25,22,11,13,7,5,15,14,21,23,20,1,24,12,2,9,19,3,18,17,10,4],[18,22,11,3,16,5,2,12,10,9,25,14,8,21,19,17,13,15,6,20,4,1,7,24,23],[23,12,15,7,13,8,17,20,25,6,18,11,4,2,22,21,1,10,24,9,16,3,14,5,19],[1,4,24,5,20,15,7,11,3,16,6,9,13,23,12,25,19,8,2,14,18,22,21,17,10],[25,10,14,17,6,19,1,21,24,22,7,15,5,16,3,12,23,11,18,4,8,20,9,13,2],[8,21,19,2,9,23,4,13,18,14,20,1,24,17,10,22,7,16,5,3,15,25,12,6,11]]";
    
    
    //static String q9 = "http://localhost:8000/sudoku?s=BFS&un=625&n1=25&n2=25&i=SUDOKU_PUZZLE_25x25_01&board=[[24,0,0,9,0,21,0,19,4,10,0,20,0,1,16,7,0,18,0,13,5,15,0,8,6],[14,3,8,0,12,0,24,15,11,2,21,0,19,13,9,0,20,0,1,6,10,0,23,16,25],[16,7,0,11,4,13,25,1,0,18,0,22,15,6,5,9,0,17,10,23,0,12,0,21,24],[22,5,1,0,19,16,6,0,23,7,4,24,14,0,25,15,21,12,8,11,17,0,20,18,3],[0,6,0,10,17,20,0,3,0,5,11,18,0,8,23,16,24,4,19,25,1,13,22,7,9],[20,0,3,15,5,0,16,23,21,13,1,0,22,0,7,18,6,19,14,10,24,11,4,0,17],[0,17,9,21,24,10,0,14,1,3,19,13,0,11,18,2,0,23,22,12,25,7,0,15,5],[13,14,25,0,11,0,18,17,7,19,10,12,9,4,21,0,3,0,15,8,22,0,2,0,16],[12,16,23,8,1,25,5,22,15,11,0,17,3,24,20,13,4,9,21,7,6,10,0,19,14],[7,0,22,0,10,2,8,0,6,12,23,5,16,0,14,0,25,24,20,17,13,21,3,9,1],[0,20,5,23,2,14,0,10,13,4,16,6,0,19,17,3,8,7,0,15,0,24,25,22,18],[3,0,6,1,8,0,15,0,20,23,24,2,10,18,13,14,9,0,11,22,19,17,16,4,7],[10,24,17,16,18,7,0,25,19,8,0,3,0,12,11,6,0,20,4,2,23,14,13,1,15],[21,19,4,22,15,0,11,16,2,24,9,0,7,14,8,23,10,13,17,1,20,0,6,0,12],[11,13,0,14,7,22,3,6,0,1,15,23,20,5,4,19,0,25,16,24,9,8,0,2,21],[5,11,10,0,21,6,19,0,22,17,13,4,12,0,2,8,15,14,23,16,7,0,1,25,20],[0,9,18,20,14,1,10,8,0,21,17,19,11,7,24,4,22,3,0,5,0,6,15,23,13],[17,15,7,12,3,0,23,0,9,20,5,16,25,0,6,10,11,0,13,18,21,19,24,0,8],[19,1,13,4,23,3,0,24,14,25,0,10,0,9,15,20,0,6,7,21,2,16,5,11,22],[6,8,16,0,22,11,13,7,5,15,14,0,23,20,1,0,12,2,9,19,3,18,17,0,4],[18,22,0,3,16,5,2,12,0,9,25,14,8,21,19,17,13,15,6,20,0,1,0,24,23],[23,12,15,7,13,8,17,20,25,6,18,11,4,2,22,0,1,10,24,9,16,0,14,5,19],[1,4,24,5,20,15,0,11,3,16,6,9,0,23,12,25,19,8,0,14,0,22,21,17,10],[25,0,14,17,6,19,1,21,24,22,7,0,5,16,3,12,23,0,18,4,8,20,9,0,2],[0,21,19,2,9,23,0,13,18,14,20,1,0,17,10,22,0,16,0,3,15,25,12,6,11]]";
    //static String q9 = "http://localhost:8000/sudoku?s=BFS&un=40&n1=9&n2=9&i=SUDOKU_PUZZLE_9x9_105&board=[[2,0,0,3,0,9,0,8,6],[7,0,5,0,1,2,3,0,9],[0,9,8,5,0,6,1,7,2],[4,0,6,0,9,3,8,5,1],[0,3,7,1,5,8,2,6,4],[8,5,1,2,6,4,9,3,7],[1,7,9,6,3,5,4,2,8],[6,8,3,4,2,1,7,9,5],[5,4,2,9,8,7,6,1,3]]";
    
    static String q10 = "http://localhost:8000/sudoku?s=BFS&un=300&n1=25&n2=25&i=SUDOKU_PUZZLE_25x25_01&board=[[24,0,0,9,0,21,0,19,4,10,0,20,0,1,16,7,0,18,0,13,5,15,0,8,6],[14,3,8,0,12,0,24,15,11,2,21,0,19,13,9,0,20,0,1,6,10,0,23,16,25],[16,7,0,11,4,13,25,1,0,18,0,22,15,6,5,9,0,17,10,23,0,12,0,21,24],[22,5,1,0,19,16,6,0,23,7,4,24,14,0,25,15,21,12,8,11,17,0,20,18,3],[0,6,0,10,17,20,0,3,0,5,11,18,0,8,23,16,24,4,19,25,1,13,22,7,9],[20,0,3,15,5,0,16,23,21,13,1,0,22,0,7,18,6,19,14,10,24,11,4,0,17],[0,17,9,21,24,10,0,14,1,3,19,13,0,11,18,2,0,23,22,12,25,7,0,15,5],[13,14,25,0,11,0,18,17,7,19,10,12,9,4,21,0,3,0,15,8,22,0,2,0,16],[12,16,23,8,1,25,5,22,15,11,0,17,3,24,20,13,4,9,21,7,6,10,0,19,14],[7,0,22,0,10,2,8,0,6,12,23,5,16,0,14,0,25,24,20,17,13,21,3,9,1],[0,20,5,23,2,14,0,10,13,4,16,6,0,19,17,3,8,7,0,15,0,24,25,22,18],[3,0,6,1,8,0,15,0,20,23,24,2,10,18,13,14,9,0,11,22,19,17,16,4,7],[10,24,17,16,18,7,9,25,19,8,22,3,21,12,11,6,5,20,4,2,23,14,13,1,15],[21,19,4,22,15,18,11,16,2,24,9,25,7,14,8,23,10,13,17,1,20,5,6,3,12],[11,13,12,14,7,22,3,6,17,1,15,23,20,5,4,19,18,25,16,24,9,8,10,2,21],[5,11,10,24,21,6,19,18,22,17,13,4,12,3,2,8,15,14,23,16,7,9,1,25,20],[2,9,18,20,14,1,10,8,16,21,17,19,11,7,24,4,22,3,25,5,12,6,15,23,13],[17,15,7,12,3,4,23,2,9,20,5,16,25,22,6,10,11,1,13,18,21,19,24,14,8],[19,1,13,4,23,3,12,24,14,25,8,10,18,9,15,20,17,6,7,21,2,16,5,11,22],[6,8,16,25,22,11,13,7,5,15,14,21,23,20,1,24,12,2,9,19,3,18,17,10,4],[18,22,11,3,16,5,2,12,10,9,25,14,8,21,19,17,13,15,6,20,4,1,7,24,23],[23,12,15,7,13,8,17,20,25,6,18,11,4,2,22,21,1,10,24,9,16,3,14,5,19],[1,4,24,5,20,15,7,11,3,16,6,9,13,23,12,25,19,8,2,14,18,22,21,17,10],[25,10,14,17,6,19,1,21,24,22,7,15,5,16,3,12,23,11,18,4,8,20,9,13,2],[8,21,19,2,9,23,4,13,18,14,20,1,24,17,10,22,7,16,5,3,15,25,12,6,11]]";
    
    
    
    static String dlx1 = "http://localhost:8000/sudoku?s=DLX&un=81&n1=9&n2=9&i=SUDOKU_PUZZLE_9x9_101&board=[[2,0,0,8,0,5,0,9,1],[9,0,8,0,7,1,2,0,6],[0,1,4,2,0,3,7,5,8],[5,0,1,0,8,7,9,2,4],[0,4,9,6,0,2,0,8,7],[7,0,2,1,4,9,3,0,5],[1,3,7,5,0,6,0,4,9],[4,2,5,0,1,8,6,0,3],[0,9,6,7,3,4,0,1,2]]";

    static String dlx3 = "http://localhost:8000/sudoku?s=DLX&un=20&n1=9&n2=9&i=SUDOKU_PUZZLE_9x9_102&board=[[2,0,0,4,0,6,0,7,5],[8,0,3,0,2,9,4,0,1],[0,4,7,1,3,5,2,8,9],[7,8,5,3,6,2,9,1,4],[3,6,9,8,4,1,7,5,2],[1,2,4,9,5,7,6,3,8],[5,7,2,6,9,8,1,4,3],[4,1,8,2,7,3,5,9,6],[9,3,6,5,1,4,8,2,7]]";

    static String dlx6 = "http://localhost:8000/sudoku?s=DLX&un=125&n1=16&n2=16&i=SUDOKU_PUZZLE_16x16_02&board=[[11,0,0,12,0,14,0,2,7,16,0,6,0,5,1,3],[0,16,0,6,10,12,0,1,2,11,14,5,0,13,0,15],[1,4,3,14,0,7,5,9,0,10,0,15,2,11,0,12],[7,5,2,13,0,16,15,4,3,9,0,12,0,14,6,8],[2,15,0,1,8,13,0,3,0,14,9,7,16,10,0,5],[13,12,0,10,9,11,4,7,0,5,15,3,14,8,2,6],[0,8,14,5,0,1,0,16,10,13,0,11,0,9,12,7],[0,9,7,11,14,5,2,10,12,6,16,8,15,1,4,13],[15,1,11,2,4,6,16,13,5,8,7,10,12,3,14,9],[12,7,5,4,2,9,1,15,14,3,11,13,8,6,10,16],[14,6,9,3,7,8,10,5,16,2,12,1,13,4,15,11],[16,13,10,8,12,3,14,11,15,4,6,9,7,2,5,1],[6,2,1,16,3,4,7,8,13,15,5,14,11,12,9,10],[5,3,4,15,16,10,9,6,11,12,8,2,1,7,13,14],[10,14,13,7,5,2,11,12,9,1,3,16,6,15,8,4],[8,11,12,9,1,15,13,14,6,7,10,4,5,16,3,2]]";

    //static String dlx8 = "http://localhost:8000/sudoku?s=DLX&un=300&n1=25&n2=25&i=SUDOKU_PUZZLE_16x16_06&board=[[24,0,0,9,0,21,0,19,4,10,0,20,0,1,16,7,0,18,0,13,5,15,0,8,6],[14,3,8,0,12,0,24,15,11,2,21,0,19,13,9,0,20,0,1,6,10,0,23,16,25],[16,7,0,11,4,13,25,1,0,18,0,22,15,6,5,9,0,17,10,23,0,12,0,21,24],[22,5,1,0,19,16,6,0,23,7,4,24,14,0,25,15,21,12,8,11,17,0,20,18,3],[0,6,0,10,17,20,0,3,0,5,11,18,0,8,23,16,24,4,19,25,1,13,22,7,9],[20,0,3,15,5,0,16,23,21,13,1,0,22,0,7,18,6,19,14,10,24,11,4,0,17],[0,17,9,21,24,10,0,14,1,3,19,13,0,11,18,2,0,23,22,12,25,7,0,15,5],[13,14,25,0,11,0,18,17,7,19,10,12,9,4,21,0,3,0,15,8,22,0,2,0,16],[12,16,23,8,1,25,5,22,15,11,0,17,3,24,20,13,4,9,21,7,6,10,0,19,14],[7,0,22,0,10,2,8,0,6,12,23,5,16,0,14,0,25,24,20,17,13,21,3,9,1],[0,20,5,23,2,14,0,10,13,4,16,6,0,19,17,3,8,7,0,15,0,24,25,22,18],[3,0,6,1,8,0,15,0,20,23,24,2,10,18,13,14,9,0,11,22,19,17,16,4,7],[10,24,17,16,18,7,9,25,19,8,22,3,21,12,11,6,5,20,4,2,23,14,13,1,15],[21,19,4,22,15,18,11,16,2,24,9,25,7,14,8,23,10,13,17,1,20,5,6,3,12],[11,13,12,14,7,22,3,6,17,1,15,23,20,5,4,19,18,25,16,24,9,8,10,2,21],[5,11,10,24,21,6,19,18,22,17,13,4,12,3,2,8,15,14,23,16,7,9,1,25,20],[2,9,18,20,14,1,10,8,16,21,17,19,11,7,24,4,22,3,25,5,12,6,15,23,13],[17,15,7,12,3,4,23,2,9,20,5,16,25,22,6,10,11,1,13,18,21,19,24,14,8],[19,1,13,4,23,3,12,24,14,25,8,10,18,9,15,20,17,6,7,21,2,16,5,11,22],[6,8,16,25,22,11,13,7,5,15,14,21,23,20,1,24,12,2,9,19,3,18,17,10,4],[18,22,11,3,16,5,2,12,10,9,25,14,8,21,19,17,13,15,6,20,4,1,7,24,23],[23,12,15,7,13,8,17,20,25,6,18,11,4,2,22,21,1,10,24,9,16,3,14,5,19],[1,4,24,5,20,15,7,11,3,16,6,9,13,23,12,25,19,8,2,14,18,22,21,17,10],[25,10,14,17,6,19,1,21,24,22,7,15,5,16,3,12,23,11,18,4,8,20,9,13,2],[8,21,19,2,9,23,4,13,18,14,20,1,24,17,10,22,7,16,5,3,15,25,12,6,11]]";

    static String dlx10 = "http://localhost:8000/sudoku?s=DLX&un=300&n1=25&n2=25&i=SUDOKU_PUZZLE_25x25_01&board=[[24,0,0,9,0,21,0,19,4,10,0,20,0,1,16,7,0,18,0,13,5,15,0,8,6],[14,3,8,0,12,0,24,15,11,2,21,0,19,13,9,0,20,0,1,6,10,0,23,16,25],[16,7,0,11,4,13,25,1,0,18,0,22,15,6,5,9,0,17,10,23,0,12,0,21,24],[22,5,1,0,19,16,6,0,23,7,4,24,14,0,25,15,21,12,8,11,17,0,20,18,3],[0,6,0,10,17,20,0,3,0,5,11,18,0,8,23,16,24,4,19,25,1,13,22,7,9],[20,0,3,15,5,0,16,23,21,13,1,0,22,0,7,18,6,19,14,10,24,11,4,0,17],[0,17,9,21,24,10,0,14,1,3,19,13,0,11,18,2,0,23,22,12,25,7,0,15,5],[13,14,25,0,11,0,18,17,7,19,10,12,9,4,21,0,3,0,15,8,22,0,2,0,16],[12,16,23,8,1,25,5,22,15,11,0,17,3,24,20,13,4,9,21,7,6,10,0,19,14],[7,0,22,0,10,2,8,0,6,12,23,5,16,0,14,0,25,24,20,17,13,21,3,9,1],[0,20,5,23,2,14,0,10,13,4,16,6,0,19,17,3,8,7,0,15,0,24,25,22,18],[3,0,6,1,8,0,15,0,20,23,24,2,10,18,13,14,9,0,11,22,19,17,16,4,7],[10,24,17,16,18,7,9,25,19,8,22,3,21,12,11,6,5,20,4,2,23,14,13,1,15],[21,19,4,22,15,18,11,16,2,24,9,25,7,14,8,23,10,13,17,1,20,5,6,3,12],[11,13,12,14,7,22,3,6,17,1,15,23,20,5,4,19,18,25,16,24,9,8,10,2,21],[5,11,10,24,21,6,19,18,22,17,13,4,12,3,2,8,15,14,23,16,7,9,1,25,20],[2,9,18,20,14,1,10,8,16,21,17,19,11,7,24,4,22,3,25,5,12,6,15,23,13],[17,15,7,12,3,4,23,2,9,20,5,16,25,22,6,10,11,1,13,18,21,19,24,14,8],[19,1,13,4,23,3,12,24,14,25,8,10,18,9,15,20,17,6,7,21,2,16,5,11,22],[6,8,16,25,22,11,13,7,5,15,14,21,23,20,1,24,12,2,9,19,3,18,17,10,4],[18,22,11,3,16,5,2,12,10,9,25,14,8,21,19,17,13,15,6,20,4,1,7,24,23],[23,12,15,7,13,8,17,20,25,6,18,11,4,2,22,21,1,10,24,9,16,3,14,5,19],[1,4,24,5,20,15,7,11,3,16,6,9,13,23,12,25,19,8,2,14,18,22,21,17,10],[25,10,14,17,6,19,1,21,24,22,7,15,5,16,3,12,23,11,18,4,8,20,9,13,2],[8,21,19,2,9,23,4,13,18,14,20,1,24,17,10,22,7,16,5,3,15,25,12,6,11]]";
    
    
    static String cp1 = "http://localhost:8000/sudoku?s=CP&un=81&n1=9&n2=9&i=SUDOKU_PUZZLE_9x9_101&board=[[2,0,0,8,0,5,0,9,1],[9,0,8,0,7,1,2,0,6],[0,1,4,2,0,3,7,5,8],[5,0,1,0,8,7,9,2,4],[0,4,9,6,0,2,0,8,7],[7,0,2,1,4,9,3,0,5],[1,3,7,5,0,6,0,4,9],[4,2,5,0,1,8,6,0,3],[0,9,6,7,3,4,0,1,2]]";

    static String cp3 = "http://localhost:8000/sudoku?s=CP&un=20&n1=9&n2=9&i=SUDOKU_PUZZLE_9x9_102&board=[[2,0,0,4,0,6,0,7,5],[8,0,3,0,2,9,4,0,1],[0,4,7,1,3,5,2,8,9],[7,8,5,3,6,2,9,1,4],[3,6,9,8,4,1,7,5,2],[1,2,4,9,5,7,6,3,8],[5,7,2,6,9,8,1,4,3],[4,1,8,2,7,3,5,9,6],[9,3,6,5,1,4,8,2,7]]";

    static String cp6 = "http://localhost:8000/sudoku?s=CP&un=125&n1=16&n2=16&i=SUDOKU_PUZZLE_16x16_02&board=[[11,0,0,12,0,14,0,2,7,16,0,6,0,5,1,3],[0,16,0,6,10,12,0,1,2,11,14,5,0,13,0,15],[1,4,3,14,0,7,5,9,0,10,0,15,2,11,0,12],[7,5,2,13,0,16,15,4,3,9,0,12,0,14,6,8],[2,15,0,1,8,13,0,3,0,14,9,7,16,10,0,5],[13,12,0,10,9,11,4,7,0,5,15,3,14,8,2,6],[0,8,14,5,0,1,0,16,10,13,0,11,0,9,12,7],[0,9,7,11,14,5,2,10,12,6,16,8,15,1,4,13],[15,1,11,2,4,6,16,13,5,8,7,10,12,3,14,9],[12,7,5,4,2,9,1,15,14,3,11,13,8,6,10,16],[14,6,9,3,7,8,10,5,16,2,12,1,13,4,15,11],[16,13,10,8,12,3,14,11,15,4,6,9,7,2,5,1],[6,2,1,16,3,4,7,8,13,15,5,14,11,12,9,10],[5,3,4,15,16,10,9,6,11,12,8,2,1,7,13,14],[10,14,13,7,5,2,11,12,9,1,3,16,6,15,8,4],[8,11,12,9,1,15,13,14,6,7,10,4,5,16,3,2]]";

    //static String cp8 = "http://localhost:8000/sudoku?s=CP&un=300&n1=25&n2=25&i=SUDOKU_PUZZLE_16x16_06&board=[[24,0,0,9,0,21,0,19,4,10,0,20,0,1,16,7,0,18,0,13,5,15,0,8,6],[14,3,8,0,12,0,24,15,11,2,21,0,19,13,9,0,20,0,1,6,10,0,23,16,25],[16,7,0,11,4,13,25,1,0,18,0,22,15,6,5,9,0,17,10,23,0,12,0,21,24],[22,5,1,0,19,16,6,0,23,7,4,24,14,0,25,15,21,12,8,11,17,0,20,18,3],[0,6,0,10,17,20,0,3,0,5,11,18,0,8,23,16,24,4,19,25,1,13,22,7,9],[20,0,3,15,5,0,16,23,21,13,1,0,22,0,7,18,6,19,14,10,24,11,4,0,17],[0,17,9,21,24,10,0,14,1,3,19,13,0,11,18,2,0,23,22,12,25,7,0,15,5],[13,14,25,0,11,0,18,17,7,19,10,12,9,4,21,0,3,0,15,8,22,0,2,0,16],[12,16,23,8,1,25,5,22,15,11,0,17,3,24,20,13,4,9,21,7,6,10,0,19,14],[7,0,22,0,10,2,8,0,6,12,23,5,16,0,14,0,25,24,20,17,13,21,3,9,1],[0,20,5,23,2,14,0,10,13,4,16,6,0,19,17,3,8,7,0,15,0,24,25,22,18],[3,0,6,1,8,0,15,0,20,23,24,2,10,18,13,14,9,0,11,22,19,17,16,4,7],[10,24,17,16,18,7,9,25,19,8,22,3,21,12,11,6,5,20,4,2,23,14,13,1,15],[21,19,4,22,15,18,11,16,2,24,9,25,7,14,8,23,10,13,17,1,20,5,6,3,12],[11,13,12,14,7,22,3,6,17,1,15,23,20,5,4,19,18,25,16,24,9,8,10,2,21],[5,11,10,24,21,6,19,18,22,17,13,4,12,3,2,8,15,14,23,16,7,9,1,25,20],[2,9,18,20,14,1,10,8,16,21,17,19,11,7,24,4,22,3,25,5,12,6,15,23,13],[17,15,7,12,3,4,23,2,9,20,5,16,25,22,6,10,11,1,13,18,21,19,24,14,8],[19,1,13,4,23,3,12,24,14,25,8,10,18,9,15,20,17,6,7,21,2,16,5,11,22],[6,8,16,25,22,11,13,7,5,15,14,21,23,20,1,24,12,2,9,19,3,18,17,10,4],[18,22,11,3,16,5,2,12,10,9,25,14,8,21,19,17,13,15,6,20,4,1,7,24,23],[23,12,15,7,13,8,17,20,25,6,18,11,4,2,22,21,1,10,24,9,16,3,14,5,19],[1,4,24,5,20,15,7,11,3,16,6,9,13,23,12,25,19,8,2,14,18,22,21,17,10],[25,10,14,17,6,19,1,21,24,22,7,15,5,16,3,12,23,11,18,4,8,20,9,13,2],[8,21,19,2,9,23,4,13,18,14,20,1,24,17,10,22,7,16,5,3,15,25,12,6,11]]";

    static String cp10 = "http://localhost:8000/sudoku?s=CP&un=300&n1=25&n2=25&i=SUDOKU_PUZZLE_25x25_01&board=[[24,0,0,9,0,21,0,19,4,10,0,20,0,1,16,7,0,18,0,13,5,15,0,8,6],[14,3,8,0,12,0,24,15,11,2,21,0,19,13,9,0,20,0,1,6,10,0,23,16,25],[16,7,0,11,4,13,25,1,0,18,0,22,15,6,5,9,0,17,10,23,0,12,0,21,24],[22,5,1,0,19,16,6,0,23,7,4,24,14,0,25,15,21,12,8,11,17,0,20,18,3],[0,6,0,10,17,20,0,3,0,5,11,18,0,8,23,16,24,4,19,25,1,13,22,7,9],[20,0,3,15,5,0,16,23,21,13,1,0,22,0,7,18,6,19,14,10,24,11,4,0,17],[0,17,9,21,24,10,0,14,1,3,19,13,0,11,18,2,0,23,22,12,25,7,0,15,5],[13,14,25,0,11,0,18,17,7,19,10,12,9,4,21,0,3,0,15,8,22,0,2,0,16],[12,16,23,8,1,25,5,22,15,11,0,17,3,24,20,13,4,9,21,7,6,10,0,19,14],[7,0,22,0,10,2,8,0,6,12,23,5,16,0,14,0,25,24,20,17,13,21,3,9,1],[0,20,5,23,2,14,0,10,13,4,16,6,0,19,17,3,8,7,0,15,0,24,25,22,18],[3,0,6,1,8,0,15,0,20,23,24,2,10,18,13,14,9,0,11,22,19,17,16,4,7],[10,24,17,16,18,7,9,25,19,8,22,3,21,12,11,6,5,20,4,2,23,14,13,1,15],[21,19,4,22,15,18,11,16,2,24,9,25,7,14,8,23,10,13,17,1,20,5,6,3,12],[11,13,12,14,7,22,3,6,17,1,15,23,20,5,4,19,18,25,16,24,9,8,10,2,21],[5,11,10,24,21,6,19,18,22,17,13,4,12,3,2,8,15,14,23,16,7,9,1,25,20],[2,9,18,20,14,1,10,8,16,21,17,19,11,7,24,4,22,3,25,5,12,6,15,23,13],[17,15,7,12,3,4,23,2,9,20,5,16,25,22,6,10,11,1,13,18,21,19,24,14,8],[19,1,13,4,23,3,12,24,14,25,8,10,18,9,15,20,17,6,7,21,2,16,5,11,22],[6,8,16,25,22,11,13,7,5,15,14,21,23,20,1,24,12,2,9,19,3,18,17,10,4],[18,22,11,3,16,5,2,12,10,9,25,14,8,21,19,17,13,15,6,20,4,1,7,24,23],[23,12,15,7,13,8,17,20,25,6,18,11,4,2,22,21,1,10,24,9,16,3,14,5,19],[1,4,24,5,20,15,7,11,3,16,6,9,13,23,12,25,19,8,2,14,18,22,21,17,10],[25,10,14,17,6,19,1,21,24,22,7,15,5,16,3,12,23,11,18,4,8,20,9,13,2],[8,21,19,2,9,23,4,13,18,14,20,1,24,17,10,22,7,16,5,3,15,25,12,6,11]]";
    
    
    //cp6,dlx3,
    //dlx3
    
    //cp1
    
    
       
    static String[] samples = {
        q1,dlx1,cp1,dlx6,q3,cp6,dlx3,cp3,
        q1,q6,q3,q1,
        dlx1,dlx6,dlx3,dlx1,
        cp1,cp6,cp3,cp1};
    
    static String[] solvers = {
        "BFS","DLX","CP","DLX","BFS","CP","DLX","CP",
        "BFS","BFS","BFS","BFS",
        "DLX","DLX","DLX","DLX",
        "CP","CP","CP","CP"};
    
    static int number_of_active_requests =0;
    
    static class Request extends Thread {
        
        private final String threadId;
        private final int id;


        private Request(long freq, int id) {
            this.threadId = freq+"-"+id;
            this.id = id;
        }

        @Override
        public void run() {
            int timeout = 10*60;
            RequestConfig config = RequestConfig.custom()
                  .setConnectTimeout(timeout * 1000)
                  .setConnectionRequestTimeout(timeout * 1000)
                  .setSocketTimeout(timeout * 1000).build();
            
            HttpClient httpclient = HttpClients.custom().setDefaultRequestConfig(config)
                .setRedirectStrategy(new LaxRedirectStrategy()).setRetryHandler(new DefaultHttpRequestRetryHandler(3, false))
                .build();
            

            HttpPost httppost = new HttpPost(samples[id]);
            

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
                number_of_active_requests++;
                //Execute and get the response.
                response = httpclient.execute(httppost);
                //assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
                entity = response.getEntity();
                System.out.println(threadId+" responded, response size: "+entity.getContent().available());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                watch.stop();
                number_of_active_requests--;
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
                    r.active_requests = number_of_active_requests;
                    String solver = solvers[id];
                    while (!endpoint.last_count.containsKey(key) || endpoint.last_count.get(key).size()<1){
                        if(endpoint.getLoad()==0){
                            Thread.sleep(5000);
                            if(!endpoint.last_count.containsKey(key) || endpoint.last_count.get(key).size()<1) throw new IOException("no values");
                        }
                        System.out.println(threadId+" waiting for values");
                        Thread.sleep(5000);
                    }
                    r.count = Estimator.transform(endpoint.last_count.get(key).pollFirst().getlocked(),solver);
                    results.put(threadId, r);
                    
                } catch (IOException ex) {
                    Logger.getLogger(Tester.class.getName()).log(Level.SEVERE, null, ex);
                    Result r = new Result();
                    r.duration = rec_time;
                    r.start_time = start_time;
                    r.estimate = oracle.predict(key);
                    r.start_time_readable = start_time_readable;
                    r.avg_cpu = getLastMinAvgCpu();
                    r.vm_total_load = endpoint.getLoad();
                    r.active_requests = number_of_active_requests;
                    String solver = solvers[id];
                    r.count = -1;
                    results.put(threadId, r);
                } catch (UnsupportedOperationException ex) {
                    Logger.getLogger(Tester.class.getName()).log(Level.SEVERE, null, ex);
                    Result r = new Result();
                    r.duration = rec_time;
                    r.start_time = start_time;
                    r.estimate = oracle.predict(key);
                    r.start_time_readable = start_time_readable;
                    r.avg_cpu = getLastMinAvgCpu();
                    r.vm_total_load = endpoint.getLoad();
                    String solver = solvers[id];
                    r.count = -1;
                    results.put(threadId, r);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Tester.class.getName()).log(Level.SEVERE, null, ex);
                    Result r = new Result();
                    r.duration = rec_time;
                    r.start_time = start_time;
                    r.estimate = oracle.predict(key);
                    r.start_time_readable = start_time_readable;
                    r.avg_cpu = getLastMinAvgCpu();
                    r.vm_total_load = endpoint.getLoad();
                    r.active_requests = number_of_active_requests;
                    String solver = solvers[id];
                    r.count = -1;
                    results.put(threadId, r);
                }
            }
            System.out.println(threadId+" finished");
        }
    }
    public static void request(long freq, int id) {
        Request r = new Request(freq,id);
        es.execute(r);
        //r.start();
    }
    
    static String[] balancer_param = {"false", "0.25", "0.75", "50", "2"};
    
    public static void main(String[] args) throws Exception {
				boolean startInstance = false;
        System.out.println("===========================================");
        System.out.println("Testing CPU usage - v1.2.9");
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
                    LoadBalancer.main(balancer_param);
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
        
        
        
        for (long freq = 20000; freq<=120000; freq=freq+20000){
            StopWatch watch = new StopWatch();
            long start_time = System.currentTimeMillis();
            String start_time_readable = Calendar.getInstance().getTime().toString();
            watch.start();
        
            es = Executors.newCachedThreadPool();
            System.out.println("Tester.main(): freq = "+freq);
            for (int i = 0; i<20; i++){
                System.out.println(i);
                request(freq,i);
                Thread.sleep(freq);
            }
            
            Thread.sleep(freq);
            watch.stop();
            long rec_time = watch.getTotalTimeMillis();
            csvWriter_extra(String.valueOf(freq),String.valueOf(rec_time),String.valueOf(getAvgCpu(rec_time)));
            
            System.out.println("===========================================");
            System.out.println(freq+" Run:\ntime: "+String.valueOf(rec_time)+" cpu: "+String.valueOf(getAvgCpu(rec_time)));
            System.out.println("Waiting for responses...");
            System.out.println("===========================================");

            es.shutdown();
            while(!es.isTerminated() && endpoint.getLoad()!=0){
                System.out.println("Active threads: "+Thread.activeCount());
                //Thread.sleep(10000);
                es.awaitTermination(10, TimeUnit.SECONDS);
            }
            Thread.sleep(20000);
            System.out.println("awaiting for last threads to finish...");
            es.shutdownNow();

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
    
    public static Double getAvgCpu(long offsetInMilliseconds){
        
        try {

            
            /* total observation time in milliseconds */
            //long offsetInMilliseconds = 1000 * 60;
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
                    return -1d;
                 }
            
            
            
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
                return -1d;
        }
        return getLastMinAvgCpu();
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
                    return -1d;
                 }
            
            
            
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
                return -1d;
        }
        return getLastMinAvgCpu();
    }
    
    public static void csvWriter(Map<String,Result> myHashMap) throws IOException {
        String eol = System.getProperty("line.separator");

        try (Writer writer = new FileWriter("results_"+balancer_param.toString()+".csv")) {
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
                  .append(',')
                  .append(String.valueOf(r.active_requests))
                  .append(eol);
          }
        } catch (IOException ex) {
          ex.printStackTrace(System.err);
        }
    }

    public static void csvWriter_extra(String id, String message1, String message2) throws IOException {
        String eol = System.getProperty("line.separator");

        try (Writer writer = new FileWriter("results_"+balancer_param.toString()+".csv")) {
            writer.append(id)
                  .append(',')
                  .append(message1)
                  .append(',')
                  .append(message2)
                  .append(eol);
        } catch (IOException ex) {
          ex.printStackTrace(System.err);
        }
    }

}