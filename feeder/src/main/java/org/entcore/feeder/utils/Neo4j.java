package org.entcore.feeder.utils;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public class Neo4j  {

	private EventBus eb;
	private String address;

	public Neo4j (EventBus eb, String address) {
		this.eb = eb;
		this.address = address;
	}

	public void execute(String query, JsonObject params, Handler<Message<JsonObject>> handler) {
		JsonObject jo = new JsonObject();
		jo.putString("action", "execute");
		jo.putString("query", query);
		if (params != null) {
			jo.putObject("params", params);
		}
		eb.send(address, jo, handler);
	}

	public void executeTransaction(JsonArray statements, Integer transactionId, boolean commit,
								   Handler<Message<JsonObject>> handler) {
		JsonObject jo = new JsonObject();
		jo.putString("action", "executeTransaction");
		jo.putArray("statements", statements);
		jo.putBoolean("commit", commit);
		if (transactionId != null) {
			jo.putNumber("transactionId", transactionId);
		}
		eb.send(address, jo, handler);
	}

	public void resetTransactionTimeout(int transactionId, Handler<Message<JsonObject>> handler) {
		JsonObject jo = new JsonObject();
		jo.putString("action", "resetTransactionTimeout");
		jo.putNumber("transactionId", transactionId);
		eb.send(address, jo, handler);
	}

	public void rollbackTransaction(int transactionId, Handler<Message<JsonObject>> handler) {
		JsonObject jo = new JsonObject();
		jo.putString("action", "rollbackTransaction");
		jo.putNumber("transactionId", transactionId);
		eb.send(address, jo, handler);
	}

}