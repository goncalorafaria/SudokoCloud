package supervisor.storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import supervisor.util.CloudStandart;
import supervisor.util.Logger;

import java.io.File;
import java.util.*;

public class RemoteStorage implements Storage<String> {

    public static AmazonDynamoDB dynamoDB;
    public static Table rTable;
    protected final String table;
    private final String key;

    public RemoteStorage(String table, String key) {
        this.table = table;
        this.key = key;
        // Create a table with a primary hash key named 'name', which holds a string
        CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(table)
                .withKeySchema(new KeySchemaElement().
                        withAttributeName(this.key).withKeyType(KeyType.HASH))
                .withAttributeDefinitions(new AttributeDefinition().
                        withAttributeName(this.key).withAttributeType(ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput().
                        withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

        // Create table if it does not exist yet
        TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);

    }

    public RemoteStorage(String table, String key, String aggregation, String range) {
        this.table = table;
        this.key = key;
        // Create a table with a primary hash key named 'name', which holds a string
        CreateTableRequest createTableRequest = new CreateTableRequest();
        createTableRequest.withTableName(table);
        createTableRequest.withKeySchema(new KeySchemaElement().
                withAttributeName(this.key).withKeyType(KeyType.HASH));

        LocalSecondaryIndex lsi = new LocalSecondaryIndex()
                .withIndexName(aggregation + "Index");

        List<KeySchemaElement> lks = new ArrayList<KeySchemaElement>(2);

        lks.add(new KeySchemaElement()
                .withAttributeName(aggregation)
                .withKeyType(KeyType.HASH));

        lks.add(new KeySchemaElement()
                 .withAttributeName(range)
                .withKeyType(KeyType.RANGE));

        lsi.withKeySchema(lks).withProjection(new Projection()
                        .withProjectionType(
                                ProjectionType.ALL));

        createTableRequest.withLocalSecondaryIndexes(lsi);

        createTableRequest
                    .withAttributeDefinitions(new AttributeDefinition().
                withAttributeName(this.key).withAttributeType(ScalarAttributeType.S))
                    .withAttributeDefinitions(new AttributeDefinition().
                withAttributeName(aggregation).withAttributeType(ScalarAttributeType.S))
                    .withAttributeDefinitions(new AttributeDefinition().
                withAttributeName(range).withAttributeType(ScalarAttributeType.S));

        createTableRequest.withProvisionedThroughput(new ProvisionedThroughput().
                withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

        // Create table if it does not exist yet
        TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);

    }

    public static void init(boolean instance) throws AmazonClientException {

        try {

            AWSCredentials credentials;
            if( instance ) {
                File fin = new File("cred.txt");
                Scanner s = new Scanner(fin);

                String aws_access_key_id = s.nextLine();
                String aws_secret_acess_key = s.nextLine();

                if(s.hasNext()){
                    credentials = new BasicSessionCredentials(
                            aws_access_key_id,aws_secret_acess_key,s.nextLine());
                }else{
                    credentials = new BasicAWSCredentials(
                            aws_access_key_id,aws_secret_acess_key);
                }
            }else {
                credentials = new ProfileCredentialsProvider().getCredentials();
            }

            dynamoDB = AmazonDynamoDBClientBuilder.standard()
                    .withRegion(CloudStandart.region)
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .build();
        } catch (Exception e) {
            //Logger.log(e.toString());
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
    }

    void setup() {
        boolean rd = false;

        while (!rd) {
            // wait for the table to move into ACTIVE state
            try {
                TableUtils.waitUntilActive(dynamoDB, table);
                rd = true;
            } catch (InterruptedException e) {
               // Logger.log(e.toString());
            }
        }

        //rTable = new Table( dynamoDB, table);
    }

    @Override
    public String describe() {
        setup();
        DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(table);
        TableDescription tableDescription = dynamoDB.
                describeTable(describeTableRequest).getTable();
        return tableDescription.toString();
    }

    @Override
    public void put(String key, Map<String, String> newItem) {
        setup();

        Map<String, AttributeValue> item = new HashMap<>();

        item.put(this.key, new AttributeValue().withS(key));

        for (Map.Entry<String, String> tp : newItem.entrySet())
            item.put(
                    tp.getKey(),
                    new AttributeValue().withS(tp.getValue())
            );

        PutItemResult r = dynamoDB.putItem(new PutItemRequest(table, item));
        //Logger.log("put result: " + r.toString());
        // Starting tmp

        /*
        HashMap<String, Condition> scanFilter = new HashMap<>();
        Condition condition = new Condition()
                .withComparisonOperator(ComparisonOperator.GT.toString())
                .withAttributeValueList(new AttributeValue().withN("2000"));

        scanFilter.put("year", condition);

        ScanRequest scanRequest = new ScanRequest(table).withScanFilter(scanFilter);
        ScanResult scanResult = dynamoDB.scan(scanRequest);
        System.out.println("Result: " + scanResult);

        */

    }

    @Override
    public void destroy() {
        setup();
        DeleteTableResult r = dynamoDB.
                deleteTable(new DeleteTableRequest(table));
        //Logger.log("delete result: " + r.toString());
    }

    @Override
    public Map<String, String> get(String key) {
        setup();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put(this.key, new AttributeValue().withS(key));
        GetItemResult r = dynamoDB.getItem(new GetItemRequest(table, item));

        Map<String, String> it = new HashMap<>();

        for (Map.Entry<String, AttributeValue> tp : r.getItem().entrySet())
            it.put(
                    tp.getKey(),
                    tp.getValue().getS()
            );

        if( it.size() == 0 )
            return null;
        else
            return it;
    }

    @Override
    public Set<String> keys() {
        setup();
        //dynamoDB.
        //Condition c = new Condition().withComparisonOperator();

        return new HashSet<String>();
    }

    @Override
    public boolean contains(String key) {
        // TODO

        Map<String, String> expressionAttributesNames = new HashMap<>();
        expressionAttributesNames.put("#key", this.key);

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":keyValue", new AttributeValue().withS(key));

        QueryResult r = dynamoDB.query(new QueryRequest(table).
                withKeyConditionExpression("#key = :keyValue")
                .withExpressionAttributeNames(expressionAttributesNames).
                        withExpressionAttributeValues(expressionAttributeValues));

        return (r.getItems().size() > 0);
    }
}