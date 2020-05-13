package supervisor.balancer.estimation;

import supervisor.server.Count;
import supervisor.storage.TaskStorage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicInteger;

public class Oracle {
    
    private final Map<String, Map<String, Group>> cachestructure =
            new ConcurrentHashMap<>();

    private final TaskStorage remote = new TaskStorage();

    private final AtomicInteger nelements = new AtomicInteger(0);

    private final TreeSet<Group> treesize =
            new TreeSet<>();

    private final int extra;

    public Oracle( int extra ) {
        cachestructure.put("BFS", new ConcurrentHashMap<String, Group>());
        cachestructure.put("DLX", new ConcurrentHashMap<String, Group>());
        cachestructure.put("CP", new ConcurrentHashMap<String, Group>());
        this.extra = extra;
    }

    public String describe() {
        return cachestructure.toString();
    }

    public void destroy() {
        cachestructure.clear();
        remote.destroy();
        treesize.clear();
    }

    private Group replenish(String key, String solver,String board,String un) {
        Map<String, Group> mg = cachestructure.get(solver);
        Group g;

        if (!mg.containsKey(board)) {
            g = new Group();
            mg.put(board, g);
        } else {
            g = mg.get(board);
        }

        if (g.shouldUpdate(un)) {
            Map<String, String> value = remote.get(key);
            int inc = g.put(un, value);
            int cur = nelements.addAndGet(inc);
            if( inc == 1 ){
                synchronized (this.treesize){
                    this.treesize.remove(g);
                    this.treesize.add(g);
                    if( cur > extra )
                        trim();
                }
            }
        }

        return g;
    }

    public double predict(String key){
        String[] sv = key.split(":");
        String solver = sv[0];
        String board = sv[2] + ":" + sv[3];
        String un = sv[1];
        double est;
        Group g = this.replenish(key,solver,board,un);

        Count c = g.get(un);
        if( c==null ){
            // nao tenho
            est = Estimator.estimate(solver,board,un,g);
        }else{
            // tenho.
            est = c.mean() + c.var();
        }

        return est;
    }

    private void trim(){
        Group g = this.treesize.first();
        if ( g.trim() ){
            nelements.decrementAndGet();
        }
        this.treesize.remove(g);
        this.treesize.add(g);
    }

}
