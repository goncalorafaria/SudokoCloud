package supervisor.server;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import supervisor.storage.LocalStorage;
import supervisor.util.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

public class CNode {

    private static AmazonEC2 ec2;

    private static Map< Long, Task > activetasks = new ConcurrentSkipListMap<>();

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

    public static void registerTask(String taskkey){
        activetasks.put(Thread.currentThread().getId(), new Task(taskkey) );
    }
}
