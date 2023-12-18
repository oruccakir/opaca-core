package de.dailab.jiacpp.platform.tests;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.model.AgentContainerImage.ImageParameter;
import de.dailab.jiacpp.model.PostAgentContainer;
import de.dailab.jiacpp.util.RestHelper;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class TestUtils {

    /**
     * Agent-container image providing some nonsensical actions useful for unit testing
     * This is the docker image of `examples/sample-container`. When adding a new feature to
     * the sample-container for testing some new function of the Runtime Platform, increment
     * the version number and push the image to the DAI Gitlab Docker Registry
     *
     * > docker build -t test-image examples/sample-container/
     * (change to TEST_IMAGE="test-image" and test locally if it works)
     * > docker tag test-image registry.gitlab.dai-labor.de/pub/unit-tests/jiacpp-sample-container:vXYZ
     * > docker push registry.gitlab.dai-labor.de/pub/unit-tests/jiacpp-sample-container:vXYZ
     */
    static final String TEST_IMAGE = "registry.gitlab.dai-labor.de/pub/unit-tests/jiacpp-sample-container:v18";

    private static DockerClient dockerClient = null;
    private static String mongoContId = null;

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
        return new PostAgentContainer(image, Map.of());
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

    public static HttpURLConnection request(String host, String method, String path, Object payload) throws IOException {
        return requestWithToken(host, method, path, payload, null);
    }

    // this is NOT using RestHelper since we are also interested in the exact HTTP Return Code
    public static HttpURLConnection requestWithToken(String host, String method, String path, Object payload, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(host + path).openConnection();
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

    public static String error(HttpURLConnection connection) throws IOException {
        return new String(connection.getErrorStream().readAllBytes());
    }

    // Starts a local mongo db on port 27017 for testing purposes
    public static void startMongoDB() throws InterruptedException {
        DockerClientConfig dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(getLocalDockerHost())
                .build();

        DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerConfig.getDockerHost())
                .sslConfig(dockerConfig.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        dockerClient = DockerClientImpl.getInstance(dockerConfig, dockerHttpClient);

        // Use the default port 27017
        ExposedPort mongoPort = ExposedPort.tcp(27017);
        Ports portBindings = new Ports();
        portBindings.bind(mongoPort, Ports.Binding.bindPort(27017));

        // Use volumes to store the temporary data
        Volume dataVolume = new Volume("/data/db");
        Volume configVolume = new Volume("/data/configdb");

        CreateContainerResponse res = dockerClient.createContainerCmd("mongodb/mongodb-community-server:7.0.4-ubuntu2204")
                .withName("jiacpp-data-test")
                .withVolumes(dataVolume, configVolume)
                .withHostConfig(HostConfig.newHostConfig().withPortBindings(portBindings).withBinds(
                        new Bind("jiacpp-platform_mongodb_data_test", dataVolume),
                        new Bind("jiacpp-platform_mongodb_config_test", configVolume)))
                .withExposedPorts(mongoPort)
                .withEnv("MONGODB_INITDB_ROOT_USERNAME=user", "MONGODB_INITDB_ROOT_PASSWORD=pass")
                .exec();

        mongoContId = res.getId();

        dockerClient.startContainerCmd(res.getId()).exec();

        // Wait 5 seconds to let container start
        Thread.sleep(5000);
    }

    public static void stopMongoDB() throws InterruptedException {
        dockerClient.stopContainerCmd(mongoContId).exec();
        // Wait 5 seconds between stopping and removing the container
        Thread.sleep(5000);
        dockerClient.removeContainerCmd(mongoContId).exec();

        // Remove the temporary data volumes
        dockerClient.removeVolumeCmd("jiacpp-platform_mongodb_data_test").exec();
        dockerClient.removeVolumeCmd("jiacpp-platform_mongodb_config_test").exec();
    }


    private static String getLocalDockerHost() {
        return SystemUtils.IS_OS_WINDOWS
                ? "npipe:////./pipe/docker_engine"
                : "unix:///var/run/docker.sock";
    }

}
