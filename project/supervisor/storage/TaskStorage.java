package supervisor.storage;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import supervisor.server.Count;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskStorage extends RemoteStorage {

    public TaskStorage() {
        super("RequestTable", "task");
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

        Map<String, String> expressionAttributesNames = new HashMap<>();
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

        return l;
    }
}
