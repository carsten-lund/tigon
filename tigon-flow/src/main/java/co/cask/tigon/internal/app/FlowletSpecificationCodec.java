/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.tigon.internal.app;

import co.cask.tigon.api.ResourceSpecification;
import co.cask.tigon.api.flow.flowlet.FailurePolicy;
import co.cask.tigon.api.flow.flowlet.FlowletSpecification;
import co.cask.tigon.internal.flowlet.DefaultFlowletSpecification;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

/**
 *
 */
final class FlowletSpecificationCodec extends AbstractSpecificationCodec<FlowletSpecification> {

  @Override
  public JsonElement serialize(FlowletSpecification src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject jsonObj = new JsonObject();

    jsonObj.add("className", new JsonPrimitive(src.getClassName()));
    jsonObj.add("name", new JsonPrimitive(src.getName()));
    jsonObj.add("description", new JsonPrimitive(src.getDescription()));
    jsonObj.add("failurePolicy", new JsonPrimitive(src.getFailurePolicy().name()));
    jsonObj.add("properties", serializeMap(src.getProperties(), context, String.class));
    jsonObj.add("resources", context.serialize(src.getResources(),
                                               new TypeToken<ResourceSpecification>() { }.getType()));
    jsonObj.add("maxInstances", new JsonPrimitive(src.getMaxInstances()));

    return jsonObj;
  }

  @Override
  public FlowletSpecification deserialize(JsonElement json, Type typeOfT,
                                          JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObj = json.getAsJsonObject();

    String className = jsonObj.get("className").getAsString();
    String name = jsonObj.get("name").getAsString();
    String description = jsonObj.get("description").getAsString();
    FailurePolicy policy = FailurePolicy.valueOf(jsonObj.get("failurePolicy").getAsString());
    Map<String, String> properties = deserializeMap(jsonObj.get("properties"), context, String.class);
    ResourceSpecification resources = context.deserialize(jsonObj.get("resources"),
                                                          new TypeToken<ResourceSpecification>() { }.getType());
    int maxInstances = jsonObj.get("maxInstances").getAsInt();

    return new DefaultFlowletSpecification(className, name, description, policy, properties, resources, maxInstances);
  }
}
