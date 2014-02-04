package org.entcore.directory.controllers;

import edu.one.core.infra.Controller;
import edu.one.core.infra.Either;
import edu.one.core.infra.Server;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import org.entcore.common.neo4j.Neo;
import org.entcore.directory.services.ClassService;
import org.entcore.directory.services.UserService;
import org.entcore.directory.services.impl.DefaultClassService;
import org.entcore.directory.services.impl.DefaultUserService;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerFileUpload;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Container;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static edu.one.core.infra.request.RequestUtils.bodyToJson;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;

public class ClassController extends Controller {

	private final ClassService classService;
	private final UserService userService;

	public ClassController(Vertx vertx, Container container, RouteMatcher rm,
			Map<String, edu.one.core.infra.security.SecuredAction> securedActions) {
		super(vertx, container, rm, securedActions);
		Neo neo = new Neo(eb,log);
		this.classService = new DefaultClassService(neo);
		this.userService = new DefaultUserService(neo);
	}

	@SecuredAction(value = "class.update", type = ActionType.RESOURCE)
	public void update(final HttpServerRequest request) {
		bodyToJson(request, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject body) {
				String classId = request.params().get("classId");
				classService.update(classId, body, defaultResponseHandler(request));
			}
		});
	}

	@SecuredAction(value = "class.user.create", type = ActionType.RESOURCE)
	public void createUser(final HttpServerRequest request) {
		bodyToJson(request, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject body) {
				String classId = request.params().get("classId");
				userService.createInClass(classId, body, notEmptyResponseHandler(request, 201));
			}
		});
	}

	@SecuredAction(value = "class.user.find", type = ActionType.RESOURCE)
	public void findUsers(final HttpServerRequest request) {
		final String classId = request.params().get("classId");
		List<UserService.UserType> types = new ArrayList<>();
		for (String t: request.params().getAll("type")) {
			try {
				types.add(UserService.UserType.valueOf(t));
			} catch (Exception e) {
				badRequest(request);
				return;
			}
		}
	 	Handler<Either<String, JsonArray>> handler;
		if ("csv".equals(request.params().get("format"))) {
			handler = new Handler<Either<String, JsonArray>>() {
				@Override
				public void handle(Either<String, JsonArray> r) {
					if (r.isRight()) {
						processTemplate(request, "text/export.txt",
								new JsonObject().putArray("list", r.right().getValue()), new Handler<String>() {
							@Override
							public void handle(final String export) {
								if (export != null) {
									classService.get(classId, new Handler<Either<String, JsonObject>>() {
										@Override
										public void handle(Either<String, JsonObject> c) {
											String name = classId;
											if (c.isRight()) {
												name = c.right().getValue().getString("name", name)
														.replaceAll("\\s+", "_");
											}
											request.response().putHeader("Content-Type", "application/csv");
											request.response().putHeader("Content-Disposition",
													"attachment; filename=" + name + ".csv");
											request.response().end(export);
										}
									});
								} else {
									renderError(request);
								}
							}
						});
					} else {
						renderJson(request, new JsonObject().putString("error", r.left().getValue()));
					}
				}
			};
		} else {
			handler = arrayResponseHandler(request);
		}
		classService.findUsers(classId, types.toArray(new UserService.UserType[types.size()]), handler);
	}

	@SecuredAction(value = "class.csv", type = ActionType.RESOURCE)
	public void csv(final HttpServerRequest request) {
		request.expectMultiPart(true);
		final String classId = request.params().get("classId");
		final String userType = request.params().get("userType");
		if (classId == null || classId.trim().isEmpty() ||
				(!"Student".equalsIgnoreCase(userType) && !"Relative".equalsIgnoreCase(userType))) {
			badRequest(request);
			return;
		}
		request.uploadHandler(new Handler<HttpServerFileUpload>() {
			@Override
			public void handle(final HttpServerFileUpload event) {
				final Buffer buff = new Buffer();
				event.dataHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer event) {
						buff.appendBuffer(event);
					}
				});
				event.endHandler(new Handler<Void>() {
					@Override
					public void handle(Void end) {
						String ut = Character.toUpperCase(userType.charAt(0)) +
								userType.substring(1).toLowerCase();
						JsonObject j = new JsonObject()
								.putString("action", "csvClass" + ut)
								.putString("classId", classId)
								.putString("csv", buff.toString("UTF-8"));
						Server.getEventBus(vertx).send(container.config().getString("feeder",
								"entcore.feeder"), j, new Handler<Message<JsonObject>>() {
							@Override
							public void handle(Message<JsonObject> message) {
								if ("ok".equals(message.body().getString("status"))) {
									request.response().end();
								} else {
									renderError(request, message.body());
								}
							}
						});
					}
				});
			}
		});
	}

	@SecuredAction(value = "class.update.user", type = ActionType.RESOURCE)
	public void updateUser(final HttpServerRequest request) {
		bodyToJson(request, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject body) {
				String classId = request.params().get("classId");
				classService.update(classId, body, defaultResponseHandler(request));
			}
		});
	}

}