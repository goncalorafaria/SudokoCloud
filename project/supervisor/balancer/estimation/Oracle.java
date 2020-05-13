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

    private final AtomicInteger max = new AtomicInteger(0);
    private final AtomicInteger hitmax = new AtomicInteger(0);

    public Oracle() {
        cachestructure.put("BFS", new ConcurrentHashMap<String, Group>());
        cachestructure.put("DLX", new ConcurrentHashMap<String, Group>());
        cachestructure.put("CP", new ConcurrentHashMap<String, Group>());
    }

    public String describe() {
        return cachestructure.toString();
    }

    public void destroy() {
        cachestructure.clear();
        remote.destroy();
    }

    private Group replenish(String key, String solver,String board,String un) {
        Map<String, Group> mg = cachestructure.get(solver);
        Group g;

        if (!mg.containsKey(board)) {
            g = new Group(max,hitmax);
            mg.put(board, g);
        } else {
            g = mg.get(board);
        }

        if (g.shouldUpdate(un)) {
            Map<String, String> value = remote.get(key);
            int tmp = g.put(un, value);

            boolean b = false;
            while(!b){
                int m = max.get();
                if( m <= tmp )
                    b = max.compareAndSet(m,tmp);
                else
                    b=true;
            }

            tmp = g.getHit();
            b = false;
            while(!b){
                int m = hitmax.get();
                if( m <= tmp )
                    b = hitmax.compareAndSet(m,tmp);
                else
                    b=true;
            }
        }

        return g;
    }

    public double predict(String key){
        String[] sv = key.split(":");
        //String classe = sv[0] + ":" + sv[2] + ":" + sv[3];
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

    public void trim(){
        Logger.log
    }

}
