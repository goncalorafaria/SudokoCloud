package supervisor.storage;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;

import java.util.*;

public class TaskStorage extends CachedRemoteStorage {

    public TaskStorage() {
        super("RequestTable", "task");//,"classe","un");
    }

    @Override
    public void put(String key, Map<String, String> newItem) {

        String[] sv = key.split(":");
        String classe = sv[0] + ":" + sv[2] + ":" + sv[3];
        String un = sv[1];

        newItem.put("classe", classe);
        newItem.put("un", un);

        super.put(key, newItem);
    }


    public List<Map<String, String>> queryMetrics(String method,String n1,String n2){
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


