
package supervisor.storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import supervisor.util.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class CachedRemoteStorage extends RemoteStorage{

    static class StochasticBaditProblem {
        private AtomicInteger hitc=new AtomicInteger(1);
        private AtomicInteger updatec=new AtomicInteger(1);
        private AtomicInteger totalc=new AtomicInteger(1);
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
            return Math.sqrt(2*Math.log(totalc.get())/hitc.get());
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

    private ConcurrentHashMap<String, StochasticBaditProblem> hittable =

            new ConcurrentHashMap<>();

    public CachedRemoteStorage(String table, String key) {
        super(table, key);
    }
    
    public static void init(boolean instance) throws AmazonClientException {
        RemoteStorage.init(instance);
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
            cache.put(key,value);
            hittable.put(key, new StochasticBaditProblem(0.1));
            return value;
        } else {
            // hit
            value = this.updatePolicy(
                    hittable.get(key),
                    value,
                    key);

            System.out.println("get "+key);
            return value;
        }
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
                cache.put(key,value);
                Logger.log("Updating the cache for:  " + key);
            }else{
                ucb.hit();
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
        if (!cache.contains(key))
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
