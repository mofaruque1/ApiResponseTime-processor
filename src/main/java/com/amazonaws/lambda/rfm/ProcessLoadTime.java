package com.amazonaws.lambda.rfm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.time.LocalTime;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

public class ProcessLoadTime implements RequestStreamHandler {

	private static final String DYNAMODB_TABLE_NAME = "rfm-planA";
	private static final String DYNAMODB_TABLE_NAME_B = "rfm-planB";
	private static final String DYNAMODB_TABLE_AVG = "rfm-plan-average";
	private DynamoDB db;

	@SuppressWarnings("unchecked")
	@Override
	public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
		JSONParser parser = new JSONParser();
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		JSONObject responseJson = new JSONObject();
		this.initDynamoDbClient();

		try {
			JSONObject eventObj = (JSONObject) parser.parse(reader);
			if (eventObj.get("body") != null) {
				LoadTimeRequest loadTimeRequest = new LoadTimeRequest(eventObj.get("body").toString());
				Table table = this.db.getTable(loadTimeRequest.isPlanA() ? DYNAMODB_TABLE_NAME : DYNAMODB_TABLE_NAME_B);
				Item item = new Item().withPrimaryKey("id", LocalTime.now().toString())
						.withString("email", loadTimeRequest.getEmail())
						.withInt("loadTime", loadTimeRequest.getLoadTime());
				table.putItem(item);

				this.calculatNewAverage(loadTimeRequest.isPlanA() ? "A" : "B", (float) loadTimeRequest.getLoadTime(),
						context);

			}

			JSONObject responseBody = new JSONObject();
			responseBody.put("message", "New item created");
			responseBody.put("status", "success");

			responseJson.put("statusCode", 200);
			responseJson.put("body", responseBody.toString());

		} catch (ParseException e) {
			context.getLogger().log(e.getMessage());
		}
		OutputStreamWriter writer = new OutputStreamWriter(outputStream, "UTF-8");
		writer.write(responseJson.toString());
		writer.close();
	}

	public void calculatNewAverage(String contentId, float newValue, Context context) {
		Table table = this.db.getTable(DYNAMODB_TABLE_AVG);
		Item item = table.getItem("planID", contentId);
		float currentAvg = item.getFloat("average");
		float newAvg;

		if (currentAvg == 0f) {
			newAvg = newValue;
		} else {
			newAvg = (currentAvg + newValue) / 2;
		}

		try {
			UpdateItemSpec updateItemSpec = new UpdateItemSpec().withPrimaryKey("planID", contentId)
					.withUpdateExpression("SET #cntr = :newval").withConditionExpression("#cntr = :currval")
					.withNameMap(new NameMap().with("#cntr", "average"))
					.withValueMap(new ValueMap().withNumber(":newval", newAvg).withNumber(":currval", currentAvg))
					.withReturnValues(ReturnValue.ALL_NEW);
			table.updateItem(updateItemSpec);
		} catch (Exception e) {
			context.getLogger().log("couldnot update new average");
		}
	}


	@SuppressWarnings("unchecked")
	public void handleGetAvgTime(InputStream inputStream, OutputStream outputStream, Context context)
			throws IOException {
		this.initDynamoDbClient();
		Table table = this.db.getTable(DYNAMODB_TABLE_AVG);
		Item itemA = table.getItem("planID", "A");
		Item itemB = table.getItem("planID", "B");
		JSONObject responseJson = new JSONObject();
		JSONObject responseBody = new JSONObject();
		try {
			if (itemA !=null && itemB !=null) {
				responseBody.put("planA", itemA.getFloat("average"));
				responseBody.put("planB", itemB.getFloat("average"));
				responseJson.put("statusCode", 200);
				responseJson.put("body", responseBody.toString());
			}
			
		} catch (Exception e) {
			context.getLogger().log("***** ERROR Occured *****");
			context.getLogger().log(e.getMessage());
		}
		
		OutputStreamWriter writer = new OutputStreamWriter(outputStream);
		writer.write(responseJson.toString());
		writer.close();
		
	}

	private void initDynamoDbClient() {
		AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
		this.db = new DynamoDB(client);
	}

}
