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

package org.entcore.feeder.aaf;

import org.entcore.feeder.dictionary.structures.Importer;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.Set;

public class UserImportProcessing extends BaseImportProcessing {

	private final Importer importer = Importer.getInstance();
	private final Set<String> resp;

	protected UserImportProcessing(String path, Vertx vertx, Set<String> resp) {
		super(path, vertx);
		this.resp = resp;
	}

	@Override
	public void start(final Handler<Message<JsonObject>> handler) {
			parse(handler, getNextImportProcessing());
	}

	@Override
	public String getMappingResource() {
		return "dictionary/mapping/aaf/PersRelEleve.json";
	}

	protected ImportProcessing getNextImportProcessing() {
		return new PersonnelImportProcessing(path, vertx);
	}

	@Override
	public void process(JsonObject object) {
		if (resp.contains(object.getString("externalId"))) {
			object.putArray("profiles", new JsonArray().add("Relative"));
			importer.createOrUpdateUser(object);
		}
	}

	@Override
	protected String getFileRegex() {
		return ".*?PersRelEleve_[0-9]{4}\\.xml";
	}

}
