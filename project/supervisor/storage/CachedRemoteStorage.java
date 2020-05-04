
package supervisor.storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CachedRemoteStorage extends RemoteStorage{
    
    public static ConcurrentHashMap<String,Map<String, String>> cache = new ConcurrentHashMap<String,Map<String, String>>();
    
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
     * TODO: Requires a more sophisticated policy for reusing values.
     * - something similar with UCB maybe.
     * */
    @Override
    public Map<String, String> get(String key) {
        Map<String, String> value = cache.get(key);
        if (value==null){
            return super.get(key);
        } else {
            System.out.println("get "+key);
            return value;
        }
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
