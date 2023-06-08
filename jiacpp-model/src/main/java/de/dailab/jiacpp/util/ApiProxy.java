package de.dailab.jiacpp.util;

import com.fasterxml.jackson.databind.JsonNode;
import de.dailab.jiacpp.api.AgentContainerApi;
import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the API forwarding to the REST services at a specific base URL.
 * Can be used for e.g. calling routes of a connected Runtime Platform, or a container's
 * parent Runtime Platform, or just for testing.
 */
public class ApiProxy implements RuntimePlatformApi, AgentContainerApi {

    public final String baseUrl;
    private final RestHelper client;

    public ApiProxy(String baseUrl) {
        this(baseUrl, null);
    }

    public ApiProxy(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.client = new RestHelper(baseUrl, token);
    }

    // INFO ROUTES

    @Override
    public RuntimePlatform getPlatformInfo() throws IOException {
        return client.get("/info", RuntimePlatform.class);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public List<Event> getHistory() throws IOException {
        return client.get("/history", List.class);
    }

    @Override
    public AgentContainer getContainerInfo() throws IOException {
        return client.get("/info", AgentContainer.class);
    }

    // AGENT ROUTES

    @SuppressWarnings({"unchecked"})
    @Override
    public List<AgentDescription> getAgents() throws IOException {
        return client.get("/agents", List.class);
    }

    @Override
    public AgentDescription getAgent(String agentId) throws IOException {
        var path = String.format("/agents/%s", agentId);
        return client.get(path, AgentDescription.class);
    }

    @Override
    public void send(String agentId, Message message, String containerId, boolean forward) throws IOException {
        var path = String.format("/send/%s?%s", agentId, buildQuery(containerId, forward));
        client.post(path, message, null);
    }

    @Override
    public void broadcast(String channel, Message message, String containerId, boolean forward) throws IOException {
        var path = String.format("/broadcast/%s?%s", channel, buildQuery(containerId, forward));
        client.post(path, message, null);
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters, String containerId, boolean forward) throws IOException {
        var path = String.format("/invoke/%s?%s", action, buildQuery(containerId, forward));
        return client.post(path, parameters, JsonNode.class);
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters, String agentId, String containerId, boolean forward) throws IOException {
        var path = String.format("/invoke/%s/%s?%s", action, agentId, buildQuery(containerId, forward));
        return client.post(path, parameters, JsonNode.class);
    }

    // CONTAINER ROUTES

    @Override
    public String addContainer(AgentContainerImage container) throws IOException {
        return client.post("/containers", container, String.class);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public List<AgentContainer> getContainers() throws IOException {
        return client.get("/containers", List.class);
    }

    @Override
    public AgentContainer getContainer(String containerId) throws IOException {
        var path = String.format("/containers/%s", containerId);
        return client.get(path, AgentContainer.class);
    }

    @Override
    public boolean removeContainer(String containerId) throws IOException {
        var path = String.format("/containers/%s", containerId);
        return client.delete(path, null, Boolean.class);
    }

    // CONNECTING ROUTES

    @Override
    public boolean connectPlatform(String url) throws IOException {
        return client.post("/connections", url, Boolean.class);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public List<String> getConnections() throws IOException {
        return client.get("/connections", List.class);
    }

    @Override
    public boolean disconnectPlatform(String url) throws IOException {
        return client.delete("/connections", url, Boolean.class);
    }

    @Override
    public boolean notifyUpdateContainer(String containerId) throws IOException {
        return client.post("/containers/notify", containerId, Boolean.class);
    }

    @Override
    public boolean notifyUpdatePlatform(String platformUrl) throws IOException {
        return client.post("/connections/notify", platformUrl, Boolean.class);
    }

    /**
     * Helper method for building Query string (without initial ?); will be more useful when there are more.
     */
    private String buildQuery(String containerId, Boolean forward) {
        String result = "";
        if (containerId != null) {
            result += String.format("&containerId=%s", containerId);
        }
        if (forward != null) {
            result += String.format("&forward=%s", forward);
        }
        return result.replaceFirst("&", "");
    }

}
