package supervisor.storage.remote;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.PriorityBlockingQueue;
import supervisor.util.Logger;

public class CachedRemoteStorage extends RemoteStorage{
    
    public static final int CACHE_SIZE = 10;
    
    class Element implements Comparable<Element>{
        final String key;
        private Map<String, String> value;
        private int number_of_accesses;

        private Element(String key, Map<String, String> value) {
            this.key = key;
            this.value = value;
            this.number_of_accesses = 0;
        }
        public Map<String, String> getValue(){ // also used by Estimator
            number_of_accesses++;
            return value;
        }
        public int getAccesses(){
            return number_of_accesses;
        }
        
        
        /* Used by the removal_queue 
           Can be changed to another removal policy;
        */
        @Override
        public int compareTo(Element o) { 
            if (this.key.equals(o.key))
                return 0;
            return (this.getAccesses() > o.getAccesses() ? -1 : 1); // ascending order
        }
    }
   
    
    /* Index by Key */
    ConcurrentHashMap<String,Element> cache =
            new ConcurrentHashMap<>();

    /* Index by Algorithm and then Board (used by the Estimator) */
    ConcurrentHashMap<String,Map<String,Set<String>>> cachetree =
            new ConcurrentHashMap<>();
    
    /* This Priority Queue orders the Elements by the removal order */
    PriorityBlockingQueue<Element> removal_queue = new PriorityBlockingQueue<>();
    
    UpdatePolicy update_policies = new UpdatePolicy();


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
        cachetree.clear();
        update_policies.clear();
        removal_queue.clear();
    }
    
    public boolean isFull(){
        return cache.size() >= CACHE_SIZE;
    }

    
    /**
     * */
    @Override
    public Map<String, String> get(String key) {
        Element element = cache.get(key);
        //System.out.println("get : "+key+"\n"+cache.size()+" : "+removal_queue.size());
        //System.out.println(element==null);
        if (element==null){
            // miss
            // lets get the value from the remote storage
            Map<String, String> value = super.get(key);

            if(value==null) // remote storage also doesn't have it
                return null;
            
            this.cacheput(key,value); // add it to the cache
            update_policies.addPolicy(key); // add the update policy for this key
            return value;
        } else {
            Map<String, String> value = element.getValue();
            // hit
            
            // Policy for this key decides if we need to get a new version
            // from the remote storage
            if (update_policies.updatePolicy(value,key)){
                value = super.get(key);
                this.cacheUpdate(key,value);
            }
            
            return value;
        }
    }
    
    private void createSpace() {
        cache.remove(removal_queue.poll().key);
    }
    
    private void cacheput(String key, Map<String, String> value){
        // Update cache
        while(isFull()){
            createSpace();
        }
        //System.out.println("Put : "+key+"\n"+cache.size()+" : "+removal_queue.size());
        Element element = new Element(key,value);
        cache.put(key,element);
        removal_queue.put(element);
        
        
        // Update cachetree
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
    
    private void cacheUpdate(String key, Map<String, String> value) {
        //System.out.println("Update : "+key+"\n"+cache.size()+" : "+removal_queue.size());
        
        Element element = cache.get(key);
        element.value = value;
    }
    
    boolean cachecontains( String solver, String board ){

        //Logger.log(solver+"+"+board);

        if( cachetree.containsKey(solver) ){
            return cachetree.get(solver).containsKey(board);
        }

        return false;
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
