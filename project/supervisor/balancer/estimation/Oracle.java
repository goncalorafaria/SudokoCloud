package supervisor.balancer.estimation;

import supervisor.server.Count;
import supervisor.storage.TaskStorage;
import supervisor.util.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

public class Oracle {
    
    private final Map<String, Map<String, Group>> cachestructure =
            new ConcurrentHashMap<>();

    private final TaskStorage remote = new TaskStorage();

    private final AtomicInteger nelements = new AtomicInteger(0);


    private final ConcurrentSkipListSet<Group> treesize =
            new ConcurrentSkipListSet<>();

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

    public void response(String key, Count c){
        String[] sv = key.split(":");
        String solver = sv[0];
        String board = sv[2] + ":" + sv[3];
        String un = sv[1];

        Map<String, Group> mg = cachestructure.get(solver);
        Group g = mg.get(board);
        int inc = g.response(un,c);
        if (inc == 1) {
            int cur = nelements.addAndGet(inc);
            Logger.log(" synchronizing treesize ");
            increment(g,cur);
        }

        Logger.log("response ------------:"+key);
    }

    private Group replenish(String key, String solver,String board,String un) {
        Map<String, Group> mg = cachestructure.get(solver);
        Group g;

        if (!mg.containsKey(board)) {
            g = new Group();
            mg.put(board, g);

            synchronized (this.treesize) {
                this.treesize.add(g);
            }
        } else {
            g = mg.get(board);
        }

        if (g.shouldUpdate(un)) {
            Map<String, String> value = remote.get(key);
            Logger.log("go fetch");
            if(value != null) {
                int inc = g.put(un, value);
                if (inc == 1) {
                    int cur = nelements.addAndGet(inc);
                    Logger.log(" synchronizing treesize ");
                    increment(g,cur);
                }
            }else{
                g.revertUpdate();
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
            est = Estimator.transform(c.mean() + c.var(),solver);
        }

        return est;
    }

    private void increment(Group g, int cur){
        Logger.log(" synchronizing treesize ");
        synchronized (this.treesize) {
            this.treesize.remove(g);
            g.blockKey();
            this.treesize.add(g);
            if (cur > extra)
                trim();
        }
    }

    private void trim(){

        Group g = this.treesize.first();
        this.treesize.remove(g);

        Logger.log("trim everything:" + g.toString());

        if ( g.trim() ){
            nelements.decrementAndGet();
        }
        g.blockKey();
        this.treesize.add(g);
    }

}
