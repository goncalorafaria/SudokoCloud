package supervisor.server;

import supervisor.util.Logger;

import java.util.*;

public class Task {

    private String key;

    private Map<String, Metric> metrics = new HashMap<>();

    // TODO : Extend to various types of Metrics.

    public Set<String> metricsK(){
        return metrics.keySet();
    }

    public static String makeKey( String[] args ){
        //StringBuilder sb = new StringBuilder();
        //String[] v = new String[4];
        String tmp=args[1]+ ":" + args[3]+ ":"  + args[5]+ ":" + args[7];
        //int c = 0;

        //for( int i = 0 ; i< args.length; i+=2)
        //   if( !(args[i].equals("-i") || args[i].equals("-b")) ){
        //        v[c] = args[c];
        //        c++;
        //    }

        return tmp;
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
