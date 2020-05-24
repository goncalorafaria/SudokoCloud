package supervisor.storage;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.*;
import supervisor.server.Count;
import supervisor.util.Logger;

import java.io.IOException;
import java.util.*;

public class TaskStorage extends RemoteStorage {

    public TaskStorage() {
        super("RequestTable",
                "key");//,"classe","un");
    }

    public Count put(String key, Count c) {

        if (c.valid()) {

            while (true) {
                Map<String, String> row;
                row = this.get(key);

                if (row == null) {
                    row = new HashMap<>();
                    row.put("Count", c.toBinary());
                    super.put(key, row);
                    Logger.log("putsucces");
                    return c;
                } else {

                    Count cold = Count.fromString(row.get("Count"));

                    String obin = cold.toBinary();
                    cold.aggregate(c);
                    Count cn = cold;
                    String nbin = cn.toBinary();

                    try {
                        updateIf(key,
                                nbin,
                                obin);
                        Logger.log("updatesucces");
                        return cn;
                    }catch (ConditionalCheckFailedException ccfe){
                        Logger.log("checkfailed");
                    }catch (AmazonServiceException ase){
                        Logger.log(ase.toString());
                        return null;
                    }
                }

            }
        }

        return null;
    }

    public void updateIf(String key,
                         String newbin,
                         String expectedbin)
            throws ConditionalCheckFailedException {

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":new_val", new AttributeValue(newbin));
        expressionAttributeValues.put(":old_val", new AttributeValue(expectedbin));

        Map<String, String> expressionAttributeNames= new HashMap<>();
        expressionAttributeNames.put("#ATTR", "Count");

        HashMap<String, AttributeValue> itemKey = new HashMap<>();
        itemKey.put("key", new AttributeValue(key));

        UpdateItemRequest updateItemRequest = new UpdateItemRequest()
                .withTableName(table)
                .withKey(itemKey)
                .withUpdateExpression("set #ATTR = :new_val")
                .withConditionExpression("#ATTR = :old_val")
                .withExpressionAttributeValues(expressionAttributeValues)
                .withExpressionAttributeNames(expressionAttributeNames)
                .withReturnValues(ReturnValue.ALL_NEW);

        // Execute the transaction and process the result.

        Logger.log(updateItemRequest.toString());
        UpdateItemResult result = dynamoDB.updateItem(updateItemRequest);

        System.out.println(result.getAttributes().get("Count"));

        if( !result.getAttributes().get("Count").getS().equals(newbin) ){
            throw new ConditionalCheckFailedException("same as before");
        }

        System.out.println("Transaction Successful");

    }
}


