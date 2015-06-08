package com.rsr5.gocd.riemann_notifier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

public class PipelineDetailsPopulator {

    protected RetrievePipelineInstance retrievePipelineInstance = new
            RetrievePipelineInstance();

    private JsonObject mergeInPipelineInstanceDetails(JsonElement notification,
                                          JsonElement pipelineInstance) {
        JsonObject json = notification.getAsJsonObject();
        json.add("x-pipeline-instance-details", pipelineInstance);
        return json;
    }

    protected JsonElement downloadPipelineDetails(String pipelineName) throws
            IOException {


        HttpURLConnection request = retrievePipelineInstance.download
                (pipelineName);

        JsonParser parser = new JsonParser();
        JsonElement rootElement = parser.parse(new InputStreamReader(
                (InputStream) request.getContent()));
        JsonObject json = rootElement.getAsJsonObject();
        JsonArray pipelines = json.get("pipelines").getAsJsonArray();
        return pipelines.get(0);
    }

    public JsonObject extendMessage(String requestBody) {
        JsonParser parser = new JsonParser();
        JsonObject json = parser.parse(requestBody).getAsJsonObject();

        System.out.println(json.toString());

        try {
            JsonObject pipeline;
            pipeline = (JsonObject) json.get("pipeline");
            String name = pipeline.get("name").getAsString();
            JsonElement extraDetails = downloadPipelineDetails(name);
            json = mergeInPipelineInstanceDetails(json, extraDetails);
        } catch (IOException e) {
            json.addProperty("x-pipeline-error", "Error connecting to GoCD " +
                    "API.");
        }
        return json;
    }
}
