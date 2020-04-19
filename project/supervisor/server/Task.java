package supervisor.server;

import supervisor.util.Logger;

import java.util.*;

public class Task {

    private String key;

    private Map<String, Metric> metrics = new HashMap<>();

    // TODO : Extend to various types of Metrics.
    public static Set<Metric> condense(Set<Task> ts){

        Count nc = new Count();
        Set<Metric> ret = new HashSet<>();

        int i = 0;
        for( Task t : ts ){
            if( i == 0 ){
                nc = new Count(
                        (Count)t.getMetric("Count")
                );

                i++;
            }else {

                Count c = (Count) t.getMetric("Count");
                nc.aggregate(c);
            }
        }

        if( nc.valid() )
            ret.add(nc);

        return ret;
    }

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

    public static class Group{

        private List<Task> q = new ArrayList<>();

        public synchronized void add(Task t){
            q.add(t);
        }

        public synchronized void drainTo(Set<Task> repository ){
            repository.addAll(q);
            q = new ArrayList<>();
        }
    }
}
