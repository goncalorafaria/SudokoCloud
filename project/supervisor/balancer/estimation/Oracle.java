package supervisor.balancer.estimation;

import supervisor.server.Count;
import supervisor.storage.TaskStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Oracle {
    
    private final Map<String, Map<String, Group>> cachestructure =
            new ConcurrentHashMap<>();

    private final TaskStorage remote = new TaskStorage();

    public Oracle() {
        cachestructure.put("BFS", new ConcurrentHashMap<>());
        cachestructure.put("DLX", new ConcurrentHashMap<>());
        cachestructure.put("CP", new ConcurrentHashMap<>());

        TaskStorage.init(false);
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
            g = new Group();
            mg.put(board, g);
        } else {
            g = mg.get(board);
        }

        if (g.shouldUpdate(un)) {
            Map<String, String> value = remote.get(key);
            g.put(un, value);
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



}
