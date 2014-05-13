/*
 * Copyright. Tous droits réservés. WebServices pour l’Education.
 */

package org.entcore.session;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.hazelcast.core.HazelcastInstance;
import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.impl.hazelcast.HazelcastClusterManager;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public class AuthManager extends BusModBase implements Handler<Message<JsonObject>> {

	protected Map<String, String> sessions;
	protected Map<String, LoginInfo> logins;

	private static final long DEFAULT_SESSION_TIMEOUT = 30 * 60 * 1000;

	private long sessionTimeout;

	private static final class LoginInfo implements Serializable {
		final long timerId;
		final String sessionId;

		private LoginInfo(long timerId, String sessionId) {
			this.timerId = timerId;
			this.sessionId = sessionId;
		}
	}

	public void start() {
		super.start();

		if (getOptionalBooleanConfig("cluster", false)) {
			HazelcastClusterManager cm = new HazelcastClusterManager((VertxInternal) vertx);
			HazelcastInstance instance = cm.getInstance();
			logger.info(instance.getName());
			sessions = instance.getMap("sessions");
			logins = instance.getMap("logins");
		} else {
			sessions = new HashMap<>();
			logins = new HashMap<>();
		}
		final String address = getOptionalStringConfig("address", "wse.session");
		Number timeout = config.getNumber("session_timeout");
		if (timeout != null) {
			if (timeout instanceof Long) {
				this.sessionTimeout = (Long)timeout;
			} else if (timeout instanceof Integer) {
				this.sessionTimeout = (Integer)timeout;
			}
		} else {
			this.sessionTimeout = DEFAULT_SESSION_TIMEOUT;
		}

		eb.registerHandler(address, this);
	}

	@Override
	public void handle(Message<JsonObject> message) {
		String action = message.body().getString("action");

		if (action == null) {
			sendError(message, "action must be specified");
			return;
		}

		switch (action) {
		case "find":
			doFind(message);
			break;
		case "findByUserId":
			doFindByUserId(message);
			break;
		case "create":
			doCreate(message);
			break;
		case "drop":
			doDrop(message);
			break;
		case "addAttribute":
			doAddAttribute(message);
			break;
		case "removeAttribute":
			doRemoveAttribute(message);
			break;
		default:
			sendError(message, "Invalid action: " + action);
		}
	}

	private void doFindByUserId(final Message<JsonObject> message) {
		final String userId = message.body().getString("userId");
		if (userId == null || userId.trim().isEmpty()) {
			sendError(message, "Invalid userId.");
			return;
		}

		LoginInfo info = logins.get(userId);
		if (info == null && !message.body().getBoolean("allowDisconnectedUser", false)) {
			sendError(message, "Invalid userId.");
			return;
		} else if (info == null) {
			generateSessionInfos(userId, new Handler<JsonObject>() {

				@Override
				public void handle(JsonObject infos) {
					if (infos != null) {
						sendOK(message, new JsonObject().putString("status", "ok")
								.putObject("session", infos));
					} else {
						sendError(message, "Invalid userId : " + userId);
					}
				}
			});
			return;
		}
		JsonObject session = unmarshal(sessions.get(info.sessionId));
		if (session == null) {
			sendError(message, "Session not found.");
			return;
		}
		sendOK(message, new JsonObject().putString("status", "ok").putObject("session", session));
	}

	private JsonObject unmarshal(String s) {
		if (s != null) {
			return new JsonObject(s);
		}
		return null;
	}

	private void doFind(Message<JsonObject> message) {
		String sessionId = message.body().getString("sessionId");
		if (sessionId == null || sessionId.trim().isEmpty()) {
			sendError(message, "Invalid sessionId.");
			return;
		}

		JsonObject session =  unmarshal(sessions.get(sessionId));
		if (session == null) {
			sendError(message, "Session not found.");
			return;
		}
		sendOK(message, new JsonObject().putString("status", "ok").putObject("session", session));
	}

	private void doCreate(final Message<JsonObject> message) {
		final String userId = message.body().getString("userId");
		if (userId == null || userId.trim().isEmpty()) {
			sendError(message, "Invalid userId.");
			return;
		}

		generateSessionInfos(userId, new Handler<JsonObject>() {

			@Override
			public void handle(JsonObject infos) {
				if (infos != null) {
					final String sessionId = UUID.randomUUID().toString();
					long timerId = vertx.setTimer(sessionTimeout, new Handler<Long>() {
						public void handle(Long timerId) {
							sessions.remove(sessionId);
							logins.remove(userId);
						}
					});
					sessions.put(sessionId, infos.encode());
					logins.put(userId, new LoginInfo(timerId, sessionId));
					sendOK(message, new JsonObject()
					.putString("status", "ok")
					.putString("sessionId", sessionId));
				} else {
					sendError(message, "Invalid userId : " + userId);
				}
			}
		});
	}

	private void doDrop(Message<JsonObject> message) {
		String sessionId = message.body().getString("sessionId");
		if (sessionId == null || sessionId.trim().isEmpty()) {
			sendError(message, "Invalid sessionId.");
			return;
		}

		JsonObject session =  unmarshal(sessions.get(sessionId));
		if (session == null) {
			sendError(message, "Session not found.");
			return;
		}

		JsonObject s =  unmarshal(sessions.remove(sessionId));
		if (s != null) {
			LoginInfo info = logins.remove(s.getString("userId"));
			if (info != null) {
				vertx.cancelTimer(info.timerId);
			}
		}
		sendOK(message, new JsonObject().putString("status", "ok"));
	}


	private void doAddAttribute(Message<JsonObject> message) {
		JsonObject session = getSessionByUserId(message);
		if (session == null) {
			return;
		}

		String key = message.body().getString("key");
		if (key == null || key.trim().isEmpty()) {
			sendError(message, "Invalid key.");
			return;
		}

		Object value = message.body().getValue("value");
		if (value == null) {
			sendError(message, "Invalid value.");
			return;
		}

		session.getObject("cache").putValue(key, value);
		sendOK(message);
	}

	private JsonObject getSessionByUserId(Message<JsonObject> message) {
		final String userId = message.body().getString("userId");
		if (userId == null || userId.trim().isEmpty()) {
			sendError(message, "Invalid userId.");
			return null;
		}

		LoginInfo info = logins.get(userId);
		if (info == null) {
			sendError(message, "Invalid userId.");
			return null;
		}
		JsonObject session =  unmarshal(sessions.get(info.sessionId));
		if (session == null) {
			sendError(message, "Session not found.");
			return null;
		}
		return session;
	}

	private void doRemoveAttribute(Message<JsonObject> message) {
		JsonObject session = getSessionByUserId(message);
		if (session == null) {
			return;
		}

		String key = message.body().getString("key");
		if (key == null || key.trim().isEmpty()) {
			sendError(message, "Invalid key.");
			return;
		}

		session.getObject("cache").removeField(key);
		sendOK(message);
	}

	private void generateSessionInfos(final String userId, final Handler<JsonObject> handler) {
		String query =
				"MATCH (n:User) " +
				"WHERE n.id = {id} AND HAS(n.login) " +
				"OPTIONAL MATCH n-[:IN]->g-[:AUTHORIZED]->r-[:AUTHORIZE]->a<-[:PROVIDE]-app " +
				"WITH app, a, n " +
				"OPTIONAL MATCH n-[:IN]->(gp:ProfileGroup) " +
				"WITH app, a, n, gp " +
				"OPTIONAL MATCH n-[:IN]->(gpe:ProfileGroup)-[:DEPENDS]->(s:Structure) " +
				"OPTIONAL MATCH n-[:IN]->(gpc:ProfileGroup)-[:DEPENDS]->(c:Class) " +
				"OPTIONAL MATCH n-[:IN]->(fg:FunctionGroup)-[:HAS_FUNCTION]->(f:Function) " +
				"OPTIONAL MATCH gpe-[:HAS_PROFILE]->(p:Profile) " +
				"RETURN distinct COLLECT(distinct [a.name,a.displayName,a.type]) as authorizedActions, " +
				"HEAD(n.classes) as classId, n.level as level, n.login as login, COLLECT(distinct c.id) as classes, " +
				"n.lastName as lastName, n.firstName as firstName, " +
				"n.displayName as username, p.name as type, COLLECT(distinct f.externalId) as functionCodes, " +
				"COLLECT(distinct [app.name,app.address,app.icon,app.target,app.displayName]) as apps, " +
				"s.name as schoolName, s.UAI as uai, COLLECT(distinct gp.id) as profilGroupsIds";
		Map<String, Object> params = new HashMap<>();
		params.put("id", userId);
		sendNeo4j(query, params, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> message) {
				JsonArray result = message.body().getArray("result");
				if ("ok".equals(message.body().getString("status")) && result != null && result.size() > 0) {
					JsonObject j = result.get(0);
					j.putString("userId", userId);
					JsonArray actions = new JsonArray();
					JsonArray apps = new JsonArray();
					for (Object o : j.getArray("authorizedActions", new JsonArray())) {
						if (!(o instanceof JsonArray)) continue;
						JsonArray a = (JsonArray) o;
						actions.addObject(new JsonObject()
								.putString("name", (String) a.get(0))
								.putString("displayName", (String) a.get(1))
								.putString("type", (String) a.get(2)));
					}
					for (Object o : j.getArray("apps", new JsonArray())) {
						if (!(o instanceof JsonArray)) continue;
						JsonArray a = (JsonArray) o;
						apps.addObject(new JsonObject()
								.putString("name", (String) a.get(0))
								.putString("address", (String) a.get(1))
								.putString("icon", (String) a.get(2))
								.putString("target", (String) a.get(3))
								.putString("displayName", (String) a.get(4))
						);
					}
					j.putArray("authorizedActions", actions);
					j.putArray("apps", apps);
					j.putObject("cache", new JsonObject());
					handler.handle(j);
				} else {
					handler.handle(null);
				}
			}
		});
	}

	private void sendNeo4j(String query, Map<String, Object> params,
			Handler<Message<JsonObject>> handler) {
		JsonObject jo = new JsonObject();
		jo.putString("action", "execute");
		jo.putString("query", query);
		jo.putObject("params", new JsonObject(params));
		eb.send("wse.neo4j.persistor", jo, handler);
	}

}
