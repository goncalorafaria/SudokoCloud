package supervisor.server;

import java.util.*;

public class Task {

    private final String key;

    private final Map<String, Metric> metrics = new HashMap<>();

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

    public static class Group {

        private List<Task> q = new ArrayList<>();

        public synchronized void add(Task t) {
            q.add(t);
        }

        public synchronized void drainTo(Set<Task> repository) {
            repository.addAll(q);
            q = new ArrayList<>();
        }
    }
}
