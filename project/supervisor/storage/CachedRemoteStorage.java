package supervisor.storage;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import supervisor.server.Count;
import supervisor.util.Logger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

public class CachedRemoteStorage extends RemoteStorage{

    static class StochasticBaditProblem {
        private final AtomicInteger hitc=new AtomicInteger(1);
        private final AtomicInteger updatec=new AtomicInteger(1);
        private final AtomicInteger totalc=new AtomicInteger(1);

        private final double base;

        StochasticBaditProblem(double base){
            this.base = base;
        }

        void hit(){
            hitc.addAndGet(1);
            totalc.addAndGet(1);
        }

        void update(){
            updatec.addAndGet(1);
            totalc.addAndGet(1);
        }

        double hitScore(){
            return base + Math.sqrt(2*Math.log(totalc.get())/hitc.get());
        }

        double updateScore(){
            return Math.sqrt(2*Math.log(totalc.get())/updatec.get());
        }

        boolean shouldUpdate(){
            double a = hitScore();
            double b = updateScore();

            return (b >= a);
        }
    }
    /* cache for a single table */
    private ConcurrentHashMap<String,Map<String, String>> cache =
            new ConcurrentHashMap<>();

    private Map<String,Map<String,Set<String>>> cachetree =
            new ConcurrentHashMap<>();

    private ConcurrentHashMap<String, StochasticBaditProblem> hittable =
            new ConcurrentHashMap<>();

    public CachedRemoteStorage(String table, String key) {
        super(table, key);
        cachetree.put("BFS", new ConcurrentHashMap<String,Set<String>>());
        cachetree.put("DLX", new ConcurrentHashMap<String,Set<String>>());
        cachetree.put("CP", new ConcurrentHashMap<String,Set<String>>());
    }

    @Override
    public String describe() {
        return super.describe();
    }

    @Override
    public void destroy() {
        super.destroy();
        cache.clear();
    }

    /**
     * */
    @Override
    public Map<String, String> get(String key) {
        Map<String, String> value = cache.get(key);
        if (value==null){
            // miss
            value = super.get(key);

            if(value==null)
                return null;

            this.cacheput(key,value);
            hittable.put(key, new StochasticBaditProblem(0.1));
            return value;
        } else {
            // hit
            value = this.updatePolicy(
                    hittable.get(key),
                    value,
                    key);

            return value;
        }
    }

    private void cacheput(String key, Map<String, String> value){
        cache.put(key,value);

        String[] sv = key.split(":");
        //String classe = sv[0] + ":" + sv[2] + ":" + sv[3];
        String solver = sv[0];
        String board = sv[2] + ":" + sv[3];
        String un = sv[1];

        Map<String,Set<String>> solvert =  cachetree.get(solver);

        if( !solvert.containsKey(board) ){
            solvert.put(board, new ConcurrentSkipListSet<String>());
        }

        solvert.get(board).add(un);
    }

    private boolean cachecontains( String solver, String board ){

        //Logger.log(solver+"+"+board);

        if( cachetree.containsKey(solver) ){
            return cachetree.get(solver).containsKey(board);
        }

        return false;
    }

    private double linint(double x0,double y0, double x1, double y1, double x ){
        return y0 + (x- x0)*(y1 - y0)/(x1 - x0);
    }

    private double estimateBranchesTaken(String key, String solver, String un, String board){

        double est=0.0;

        try {
            Map<String, String> v = this.get(key);
            if (v == null) {
                // was never performed.
                if( this.cachecontains(solver,board) ){
                    // has classe element.
                    Set<String> ks = cachetree.get(solver).get(board);
                    //Logger.log(">>>>>>>> contains: " + ks);

                    if( ks.size() > 1 && !solver.equals("DLX") ){
                        // more than 2 sizes.
                        int target = Integer.parseInt(un);
                        int before = Integer.MIN_VALUE;
                        int after = Integer.MAX_VALUE;

                        for( String k : ks){
                            int candidate = Integer.parseInt(k);
                            if( candidate < target ){
                                if( before < candidate )
                                    before = candidate;
                            }else{
                                if( candidate < after )
                                    after = candidate;
                            }
                        }

                        if(before == Integer.MIN_VALUE || after == Integer.MAX_VALUE){
                            // chooses closet.
                            int choice;
                            if( before == Integer.MIN_VALUE ){
                                choice = after;
                            }else{
                                choice = before;
                            }
                            Count c = Count.fromString(
                                    cache.get(solver+":"+choice+":"+board)
                                            .get("Count"));

                            est = c.mean() + Math.sqrt(c.var());
                        }
                        else{
                            // does linear interpolation.
                            Count cbefore = Count.fromString(
                                    cache.get(solver+":"+before+":"+board)
                                            .get("Count"));

                            Count cafter  = Count.fromString(
                                    cache.get(solver+":"+after+":"+board)
                                            .get("Count"));

                            est = this.linint((double)before,
                                    cbefore.mean(),
                                    (double)after,
                                    cafter.mean(),
                                    (double)target);

                            est += Math.sqrt(Math.max(cafter.var(),cbefore.var()));
                        }

                    }
                    else{
                        String kclose = ks.iterator().next();
                        v = this.get(solver+":"+kclose+":"+board);
                        Count c = Count.fromString(v.get("Count"));
                        est = c.mean() + Math.sqrt(c.var());
                    }
                }
                else{
                    //Logger.log(">>>>>>>> does not contain");
                    // does not have class elements.
                    est = 4000.0;
                }
            }
            else {
                // was already performed.
                Count c = Count.fromString(v.get("Count"));
                est = c.mean() + Math.sqrt(c.var());
            }

        }catch (IOException e){
            Logger.log(e.toString());
        }catch (ClassNotFoundException e){
            Logger.log(e.toString());
        }

        return est;
    }

    public double estimate(String key){

        String[] sv = key.split(":");
        String solver = sv[0];
        String un = sv[1];
        String board = sv[2] + ":" + sv[3];

        double est = estimateBranchesTaken(key,solver,un,board);

        switch (solver){
            case "BFS":
                est = est*12.88852179+14068.78484095;
                break;
            case "CP":
                est = est*14.16131419+19312.86569091;
                break;
            case "DLX":
                est = est*24.39689662-1392680.19952047;
                break;
        }
        if( est < 0 ){
            est = 400000*24.39689662 - 1392680.19952047;
        }
        return est;
    }
    private Map<String,String> updatePolicy(
            StochasticBaditProblem ucb,
            Map<String,String> value,
            String key){

            // sqrt( 2 * log(total)/ refreshc ) >= 1
            if( ucb.shouldUpdate() ){
                // updating cache.
                ucb.update();
                value = super.get(key);
                this.cacheput(key,value);
                Logger.log("Updating the cache for:  " + key);
            }else{
                ucb.hit();
                Logger.log("Hit on cache with: " + key);
            }
        return value;
    }
    
    // to be sure we have all the keys this needs to be remote
    @Override
    public Set<String> keys() {
        return super.keys();
    }

    @Override
    public boolean contains(String key) {
        if (!cache.containsKey(key))
            return super.contains(key);
        else
            return true;
    }

    public List<Map<String, String>> queryMetrics(String method, String n1, String n2){
        super.setup();
        Map<String, String> expressionAttributesNames = new HashMap<>();
        /* **
        expressionAttributesNames.put("#classe", "classe");

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":classeValue", new AttributeValue().withS(method+":"+n1+":"+n2));

        QueryResult r = dynamoDB.query(new QueryRequest(super.table).
                withKeyConditionExpression("#classe = :classeValue")
                .withExpressionAttributeNames(expressionAttributesNames).
                        withExpressionAttributeValues(expressionAttributeValues));

        List<Map<String,String>> l = new ArrayList<>();

        for( Map<String,AttributeValue> m : r.getItems()){
            Map<String, String> it = new HashMap<>();

            for (Map.Entry<String, AttributeValue> tp :m.entrySet())
                it.put(
                        tp.getKey(),
                        tp.getValue().getS()
                );
            l.add(it);
        }
        */

        return new ArrayList<>();
    }

    public List<Map<String, String>> getAll(){
        super.setup();

        List<Map<String, String>> l = new ArrayList<>();
        Table r = new Table( dynamoDB, table);

        try {
            ItemCollection<ScanOutcome> items = r.scan(new ScanSpec());

            Iterator<Item> iter = items.iterator();
            while (iter.hasNext()) {
                Item item = iter.next();
                Map<String, String> it = new HashMap<>();
                for (Map.Entry<String, Object> tp :item.asMap().entrySet()) {
                    String av = (String) tp.getValue();
                    it.put(
                            tp.getKey(),
                            av
                    );
                    l.add(it);
                    //System.out.println(av);
                }
            }
        }
        catch (Exception e) {
            System.err.println("Unable to scan the table:");
            System.err.println(e.getMessage());
        }
        return l;
    }

}
