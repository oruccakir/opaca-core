package de.gtarc.opaca.platform.tests;

import de.gtarc.opaca.model.*;
import de.gtarc.opaca.model.AgentContainerImage.ImageParameter;
import de.gtarc.opaca.util.RestHelper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Class providing util methods and constants used by the other Test classes.
 */
public class TestUtils {

    /**
     * Agent-container image providing some nonsensical actions useful for unit testing
     * This is the docker image of `examples/sample-container`. The image is build automatically
     * during CI. When running tests locally, make sure to build the image first, with this name.
     */
    static final String TEST_IMAGE = "sample-agent-container-image";

    /*
     * HELPER METHODS
     */

    public static PostAgentContainer getSampleContainerImage() {
        var image = new AgentContainerImage();
        image.setImageName(TEST_IMAGE);
        image.setExtraPorts(Map.of(
                8888, new AgentContainerImage.PortDescription("TCP", "TCP Test Port"),
                8889, new AgentContainerImage.PortDescription("UDP", "UDP Test Port")
        ));
        return new PostAgentContainer(image, Map.of(), null);
    }

    public static void addImageParameters(PostAgentContainer sampleRequest) {
        // parameters should match those defined in the sample-agent-container-image's own container.json!
        sampleRequest.getImage().setParameters(List.of(
                new ImageParameter("database", "string", false, false, "mongodb"),
                new ImageParameter("username", "string", true, false, null),
                new ImageParameter("password", "string", true, true, null)
        ));
    }

    public static String buildQuery(Map<String, Object> params) {
        if (params != null) {
            var query = new StringBuilder();
            for (String key : params.keySet()) {
                query.append(String.format("&%s=%s", key, params.get(key)));
            }
            return query.toString().replaceFirst("&", "?");
        } else {
            return "";
        }
    }

    public static HttpURLConnection request(String host, String method, String path, Object payload) throws Exception {
        return requestWithToken(host, method, path, payload, null);
    }

    // this is NOT using RestHelper since we are also interested in the exact HTTP Return Code
    public static int streamRequest(String baseUrl, String method, String path, byte[] payload) throws Exception {
        // TODO reduce code duplication a bit?
        HttpURLConnection connection = (HttpURLConnection) new URI(baseUrl + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        connection.setDoOutput(true);
        connection.connect();

        try (OutputStream os = connection.getOutputStream();
            InputStream inputStream = new ByteArrayInputStream(payload);
            BufferedInputStream bis = new BufferedInputStream(inputStream)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        } finally {
            connection.disconnect();
        }

        return connection.getResponseCode();
    }

    // this is NOT using RestHelper since we are also interested in the exact HTTP Return Code
    public static HttpURLConnection requestWithToken(String host, String method, String path, Object payload, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URI(host + path).toURL().openConnection();
        connection.setRequestMethod(method);

        if (token != null) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }

        if (payload != null) {
            String json = RestHelper.mapper.writeValueAsString(payload);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.connect();
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bytes);
            }
        } else {
            connection.connect();
        }
        return connection;
    }

    public static String result(HttpURLConnection connection) throws IOException {
        return new String(connection.getInputStream().readAllBytes());
    }

    public static <T> T result(HttpURLConnection connection, Class<T> type) throws IOException {
        return RestHelper.mapper.readValue(connection.getInputStream(), type);
    }

    public static ErrorResponse error(HttpURLConnection connection) throws IOException {
        var content = new String(connection.getErrorStream().readAllBytes());
        return RestHelper.readObject(content, ErrorResponse.class);
    }

    public static String getBaseUrl(String localUrl) throws Exception {
        var con = request(localUrl, "GET", "/info", null);
        return result(con, RuntimePlatform.class).getBaseUrl();
    }

    public static String postSampleContainer(String platformUrl) throws Exception {
        var postContainer = getSampleContainerImage();
        var con = request(platformUrl, "POST", "/containers", postContainer);
        if (con.getResponseCode() != 200) {
            var message = new String(con.getErrorStream().readAllBytes());
            throw new IOException("Failed to POST sample container: " + message);
        }
        return result(con);
    }

    public static void connectPlatforms(String platformUrl, String connectedUrl) throws Exception {
        var connectedBaseUrl = getBaseUrl(connectedUrl);
        var loginCon = new LoginConnection(null, null, connectedBaseUrl);
        var con = request(platformUrl, "POST", "/connections", loginCon);
        if (con.getResponseCode() != 200) {
            var message = new String(con.getErrorStream().readAllBytes());
            throw new IOException("Failed to connect platforms: " + message);
        }
    }

    public static User getUser(String username, String password, Role role, List<String> privileges) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setRole(role);
        user.setPrivileges(privileges);
        return user;
    }
}
