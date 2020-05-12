package supervisor.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Task {

    private final String key;
    private Long time = System.currentTimeMillis();;

    private final Map<String, Metric> metrics = new ConcurrentHashMap<>();

    // TODO : Extend to various types of Metrics.

    public Task(String key) {
        this.key = key;
    }

    public Set<String> metricsK() {
        return metrics.keySet();
    }

    public void addMetric(String name, Metric m) {
        metrics.put(name, m);
    }

    public Metric getMetric(String classname) {
        return metrics.get(classname);
    }

    public String getKey() {
        return key;
    }

    public void wrap(){
        this.time = System.currentTimeMillis() - this.time;
        ((Count)metrics.get("Count")).inc_count = this.time;
        ((Count)metrics.get("Count")).lock();
    }
}
