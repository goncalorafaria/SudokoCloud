package supervisor.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;

public class Task {

    private String key;

    private Map<String, Metric> metrics = new HashMap<>();

    public static String makeKey( String[] args ){
        return args[1] + ":" + args[3]+ ":" + args[5] + ":" + args[9] + ":" + args[11];
    }
    public void addMetric(String name, Metric m){
        metrics.put( name, m);
    }

    public Metric getMetric(String classname){
        return metrics.get(classname);
    }

    public String getKey(){
        return key;
    }

    public Task(String key){
        this.key=key;
    }
}
