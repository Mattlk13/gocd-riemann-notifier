package com.rsr5.gocd.riemann_notifier;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

import com.aphyr.riemann.client.RiemannClient;

import java.io.IOException;
import java.util.*;


@Extension
public class GoNotificationPlugin implements GoPlugin {

    private static Logger LOGGER = Logger.getLoggerFor(GoNotificationPlugin
            .class);

    public static final String EXTENSION_TYPE = "notification";
    private static final List<String> goSupportedVersions = Collections
            .singletonList("1.0");
    public static final String REQUEST_NOTIFICATIONS_INTERESTED_IN =
            "notifications-interested-in";
    public static final String REQUEST_STAGE_STATUS = "stage-status";
    public static final int SUCCESS_RESPONSE_CODE = 200;
    public static final int INTERNAL_ERROR_RESPONSE_CODE = 500;

    protected RiemannClient riemann = null;

    protected PipelineDetailsPopulator populator = null;

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor
                                                        goApplicationAccessor) {

        PluginConfig pluginConfig = new PluginConfig();
        int riemannPort = pluginConfig.getRiemannPort();
        String riemannHost = pluginConfig.getRiemannHost();

        if (riemann == null) {
            this.populator = new PipelineDetailsPopulator();
            try {
                riemann = RiemannClient.tcp(riemannHost, riemannPort);
                riemann.connect();
            } catch (IOException e) {
                LOGGER.warn("Unable to connect to Riemann at localhost");
            }
        }
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
        LOGGER.debug("received go plugin api request " + goPluginApiRequest
                .requestName());

        if (goPluginApiRequest.requestName().equals
                (REQUEST_NOTIFICATIONS_INTERESTED_IN))
            return handleNotificationsInterestedIn();
        if (goPluginApiRequest.requestName().equals(REQUEST_STAGE_STATUS)) {
            return handleStageNotification(goPluginApiRequest);
        }
        return null;
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        LOGGER.debug("received pluginIdentifier request");

        return new GoPluginIdentifier(EXTENSION_TYPE, goSupportedVersions);
    }

    private GoPluginApiResponse handleNotificationsInterestedIn() {
        Map<String, List<String>> response = new HashMap<>();
        response.put("notifications", Collections.singletonList
                (REQUEST_STAGE_STATUS));
        LOGGER.debug("requesting details of stage-status notifications");
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private String service(JsonObject json) {
        JsonObject pipelineObject, stageObject;
        pipelineObject = (JsonObject) json.get("pipeline");
        stageObject = (JsonObject) pipelineObject.get("stage");

        String group = pipelineObject.get("group").getAsString();
        String pipeline = pipelineObject.get("name").getAsString();
        String stage = stageObject.get("name").getAsString();

        return "gocd." + group + "." + pipeline + "." + stage;
    }

    private String state(JsonObject json) {
        JsonObject pipelineObject, stageObject;
        pipelineObject = (JsonObject) json.get("pipeline");
        stageObject = (JsonObject) pipelineObject.get("stage");

        return stageObject.get("state").getAsString();
    }

    protected GoPluginApiResponse handleStageNotification(
            GoPluginApiRequest goPluginApiRequest) {

        int responseCode = SUCCESS_RESPONSE_CODE;

        Map<String, Object> response = new HashMap<>();
        List<String> messages = new ArrayList<>();

        response.put("status", "success");

        JsonObject json = populator.extendMessage(goPluginApiRequest
                .requestBody());

        try {
            riemann.event().
                    service(this.service(json)).
                    state(this.state(json)).
                    send().
                    deref(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (IOException e) {
            LOGGER.error("failed to notify pipeline listener", e);
            responseCode = INTERNAL_ERROR_RESPONSE_CODE;
            response.put("status", "failure");
            messages.add(e.getMessage());
        }

        response.put("messages", messages);
        return renderJSON(responseCode, response);
    }

    private GoPluginApiResponse renderJSON(final int responseCode, Object
            response) {
        final String json = response == null ? null : new GsonBuilder()
                .create().toJson(response);
        return new GoPluginApiResponse() {
            public int responseCode() {
                return responseCode;
            }

            public Map<String, String> responseHeaders() {
                return null;
            }

            public String responseBody() {
                return json;
            }
        };
    }
}
