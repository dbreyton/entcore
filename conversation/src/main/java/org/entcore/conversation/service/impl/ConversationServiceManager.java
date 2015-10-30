/* Copyright © WebServices pour l'Éducation, 2014
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 */

package org.entcore.conversation.service.impl;

import fr.wseduc.webutils.collections.Joiner;
import org.entcore.common.appregistry.AppRegistryEventsService;
import org.entcore.common.appregistry.ApplicationUtils;
import org.entcore.common.neo4j.Neo;
import org.entcore.common.neo4j.StatementsBuilder;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class ConversationServiceManager implements AppRegistryEventsService {

	private final EventBus eb;
	private final Neo neo;
	private final String applicationName;
	private static final Logger log = LoggerFactory.getLogger(ConversationServiceManager.class);

	public ConversationServiceManager(Vertx vertx, String applicationName) {
		eb = vertx.eventBus();
		neo = new Neo(vertx, eb, LoggerFactory.getLogger(Neo.class));
		this.applicationName = applicationName;
	}

	@Override
	public void authorizedActionsUpdated(final JsonArray groups) {
		usersInGroups(groups, new Handler<JsonArray>(){
			public void handle(final JsonArray groupUsers) {
				ApplicationUtils.applicationAllowedUsers(eb, applicationName, groupUsers, new Handler<JsonArray>() {
					public void handle(final JsonArray authUsers) {

						final Set<String> authUserIds = new HashSet<>();
						for (Object o: authUsers) {
							if (!(o instanceof JsonObject)) continue;
							authUserIds.add(((JsonObject) o).getString("id"));
						}

						manageConversationNodes(authUserIds, groupUsers);
					}
				});
			}
		});
	}

	@Override
	public void userGroupUpdated(final JsonArray users, final Message<JsonObject> message) {
		ApplicationUtils.applicationAllowedUsers(eb, applicationName, users, new Handler<JsonArray>() {
			@Override
			public void handle(JsonArray u) {
				Set<String> userIds = new HashSet<>();
				for (Object o: u) {
					if (!(o instanceof JsonObject)) continue;
					userIds.add(((JsonObject) o).getString("id"));
				}
				manageConversationNodes(userIds, users, message);
			}
		});
	}

	@Override
	public void importSucceeded() {
		final String query =
				"MATCH (:Application {name : {application}})-[:PROVIDE]->(:Action)<-[:AUTHORIZE]-(:Role)" +
				"<-[:AUTHORIZED]-(:Group)<-[:IN]-(u:User) " +
				"WHERE NOT(u-[:HAS_CONVERSATION]->()) " +
				"CREATE UNIQUE u-[:HAS_CONVERSATION]->(c:Conversation { userId : u.id, active : {true} }) ";
		final JsonObject params = new JsonObject().putBoolean("true", true).putString("application", applicationName);
		neo.execute(query, params, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				if ("ok".equals(event.body().getString("status"))) {
					final String q1 =
			"MATCH (c:Conversation) " +
			"WHERE NOT(c-[:HAS_CONVERSATION_FOLDER]->(:ConversationFolder {name :'INBOX'})) " +
			"CREATE UNIQUE c-[:HAS_CONVERSATION_FOLDER]->(fi:ConversationFolder:ConversationSystemFolder { name : 'INBOX'}) ";
					final JsonObject p = new JsonObject();
					neo.execute(q1, p, new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> event) {
							if ("ok".equals(event.body().getString("status"))) {
								final String q2 =
			"MATCH (c:Conversation) " +
			"WHERE NOT(c-[:HAS_CONVERSATION_FOLDER]->(:ConversationFolder {name :'OUTBOX'})) " +
			"CREATE UNIQUE c-[:HAS_CONVERSATION_FOLDER]->(fi:ConversationFolder:ConversationSystemFolder { name : 'OUTBOX'}) ";
								final JsonObject p = new JsonObject();
								neo.execute(q2, p, new Handler<Message<JsonObject>>() {
									@Override
									public void handle(Message<JsonObject> event) {
										if ("ok".equals(event.body().getString("status"))) {
											final String q3 =
			"MATCH (c:Conversation) " +
			"WHERE NOT(c-[:HAS_CONVERSATION_FOLDER]->(:ConversationFolder {name :'DRAFT'})) " +
			"CREATE UNIQUE c-[:HAS_CONVERSATION_FOLDER]->(fi:ConversationFolder:ConversationSystemFolder { name : 'DRAFT'}) ";
											final JsonObject p = new JsonObject();
											neo.execute(q3, p, new Handler<Message<JsonObject>>() {
												@Override
												public void handle(Message<JsonObject> event) {
													if ("ok".equals(event.body().getString("status"))) {
														final String q4 =
			"MATCH (c:Conversation) " +
			"WHERE NOT(c-[:HAS_CONVERSATION_FOLDER]->(:ConversationFolder {name :'TRASH'})) " +
			"CREATE UNIQUE c-[:HAS_CONVERSATION_FOLDER]->(fi:ConversationFolder:ConversationSystemFolder { name : 'TRASH'}) ";
														final JsonObject p = new JsonObject();
														neo.execute(q4, p, new Handler<Message<JsonObject>>() {
															@Override
															public void handle(Message<JsonObject> event) {
																if ("ok".equals(event.body().getString("status"))) {
																	log.error(event.body().getString("message"));
																}
															}
														});
													} else {
														log.error(event.body().getString("message"));
													}
												}
											});
										} else {
											log.error(event.body().getString("message"));
										}
									}
								});
							} else {
								log.error(event.body().getString("message"));
							}
						}
					});
				} else {
					log.error(event.body().getString("message"));
				}
			}
		});
	}

	private void manageConversationNodes(Set<String> userIds, JsonArray modifiedUsers) {
		manageConversationNodes(userIds, modifiedUsers, null);
	}

	private void manageConversationNodes(Set<String> userIds, JsonArray modifiedUsers,
			final Message<JsonObject> message) {
		String filter = "";
		JsonObject disableParams = new JsonObject().putBoolean("false", false).putString("application", applicationName);
		if (modifiedUsers != null) {
			filter = "AND c.userId IN {modifiedUsers} ";
			disableParams.putArray("modifiedUsers", modifiedUsers);
		}
		StatementsBuilder b = new StatementsBuilder().add(
				"MATCH (u:User) " +
				"WHERE u.id IN ['" + Joiner.on("','").join(userIds) + "'] " +
				"AND NOT(u-[:HAS_CONVERSATION]->()) " +
				"CREATE UNIQUE u-[:HAS_CONVERSATION]->(c:Conversation { userId : u.id, active : {true} }), " +
				"c-[:HAS_CONVERSATION_FOLDER]->(fi:ConversationFolder:ConversationSystemFolder { name : {inbox}}), " +
				"c-[:HAS_CONVERSATION_FOLDER]->(fo:ConversationFolder:ConversationSystemFolder { name : {outbox}}), " +
				"c-[:HAS_CONVERSATION_FOLDER]->(fd:ConversationFolder:ConversationSystemFolder { name : {draft}}), " +
				"c-[:HAS_CONVERSATION_FOLDER]->(ft:ConversationFolder:ConversationSystemFolder { name : {trash}}) ",
				new JsonObject().putString("inbox", "INBOX").putString("outbox", "OUTBOX")
						.putString("draft", "DRAFT").putString("trash", "TRASH").putBoolean("true", true))
				.add(
				"MATCH (c:Conversation) " +
				"WHERE c.userId IN ['" + Joiner.on("','").join(userIds) + "'] AND c.active <> {true} " +
				"SET c.active = {true} ", new JsonObject().putBoolean("true", true))
				.add(
				"MATCH (c:Conversation), (a:Application {name : {application}}) " +
				"WHERE NOT(c.userId IN ['" + Joiner.on("','").join(userIds) + "']) AND c.active <> {false} " + filter +
				"AND NOT((c)<-[:HAS_CONVERSATION]-(:User)-[:IN]->(:Group)-[:AUTHORIZED]->(:Role)-[:AUTHORIZE]-(:Action)" +
				"<-[:PROVIDE]-(a)) " +
				"SET c.active = {false} ", disableParams);
		Handler<Message<JsonObject>> h = null;
		if (message != null) {
			h = new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> m) {
					message.reply(m.body());
				}
			};
		}
		neo.executeTransaction(b.build(), null, true, h);
	}

	private void usersInGroups(JsonArray groups, final Handler<JsonArray> users) {
		if (users == null) return;
		if (groups == null) {
			users.handle(null);
		}
		String query =
				"MATCH (g:Group)<-[:IN]-(u:User) " +
				"WHERE g.id IN {groups} " +
				"RETURN COLLECT(distinct u.id) as users ";
		neo.execute(query, new JsonObject().putArray("groups", groups), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				JsonArray a = message.body().getArray("result");
				if ("ok".equals(message.body().getString("status")) && a != null &&
						a.size() == 1 && a.get(0) != null) {
					JsonObject j = a.get(0);
					users.handle(j.getArray("users"));
				} else {
					users.handle(null);
				}
			}
		});
	}

}
