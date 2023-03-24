package de.dailab.jiacpp.plattform.containerclient;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import de.dailab.jiacpp.api.AgentContainerApi;
import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.plattform.PlatformConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.java.Log;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Container Client for running Agent Containers in Docker, possibly on a remote host.
 *
 * Some documentation:
 * - https://github.com/docker-java/docker-java/blob/master/docs/getting_started.md
 * - https://www.baeldung.com/docker-java-api
 */
@Log
public class DockerClient implements ContainerClient {

    private PlatformConfig config;

    /** Client for accessing (remote) Docker runtime */
    private com.github.dockerjava.api.DockerClient dockerClient;

    /** additional Docker-specific information on agent containers */
    private Map<String, DockerContainerInfo> dockerContainers;

    /** Available Docker Auth */
    private Map<String, AuthConfig> auth;

    /** Set of already used ports on target Docker host */
    private Set<Integer> usedPorts;

    @Data
    @AllArgsConstructor
    static class DockerContainerInfo {
        String containerId;
        String internalIp;
        AgentContainer.Connectivity connectivity;
    }

    @Override
    public void initialize(PlatformConfig config) {

        DockerClientConfig dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .build();

        DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerConfig.getDockerHost())
                .sslConfig(dockerConfig.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.config = config;
        this.auth = loadDockerAuth();
        this.dockerClient = DockerClientImpl.getInstance(dockerConfig, dockerHttpClient);
        this.dockerContainers = new HashMap<>();
        this.usedPorts = new HashSet<>();
    }

    @Override
    public AgentContainer.Connectivity startContainer(String containerId, AgentContainerImage image) throws IOException, NoSuchElementException {
        var imageName = image.getImageName();
        var extraPorts = image.getExtraPorts();
        try {
            // TODO move this to a separate method?
            // pull image if not present
            if (! isImagePresent(imageName)) {
                log.info("Pulling Image..." + imageName);
                try {
                    var registry = imageName.split("/")[0];
                    dockerClient.pullImageCmd(imageName)
                            .withAuthConfig(this.auth.get(registry))
                            .exec(new PullImageResultCallback())
                            .awaitCompletion();
                } catch (InterruptedException e) {
                    log.warning(e.getMessage());
                } catch (InternalServerErrorException e) {
                    log.severe("Pull Image failed: " + e.getMessage());
                    throw new NoSuchElementException("Failed to Pull image: " + e.getMessage());
                }
            }

            // port mappings
            Map<Integer, Integer> portMap = Stream.concat(Stream.of(image.getApiPort()), extraPorts.keySet().stream())
                    .collect(Collectors.toMap(p -> p, this::reserveNextFreePort));

            log.info("Creating Container...");
            CreateContainerResponse res = dockerClient.createContainerCmd(imageName)
                    .withEnv(
                            String.format("%s=%s", AgentContainerApi.ENV_CONTAINER_ID, containerId),
                            String.format("%s=%s", AgentContainerApi.ENV_PLATFORM_URL, config.getOwnBaseUrl()))
                    .withHostConfig(HostConfig.newHostConfig()
                            .withPortBindings(portMap.entrySet().stream().map(
                                    // TODO format, then parse is kind of silly... is there a better way?
                                    e -> PortBinding.parse(String.format("%s:%s", e.getValue(), e.getKey()))
                            ).collect(Collectors.toList()))
                    )
                    // TODO why do we need this? DO we need this??
                    .withExposedPorts(portMap.keySet().stream().map(ExposedPort::tcp).collect(Collectors.toList()))
                    .exec();
            log.info(String.format("Result: %s", res));

            log.info("Starting Container...");
            dockerClient.startContainerCmd(res.getId()).exec();

            // create connectivity object
            var connectivity = new AgentContainer.Connectivity(
                    config.getOwnBaseUrl().replaceAll(":\\d+$", ""),  // TODO change to remote docker host once implemented (#23)
                    portMap.get(image.getApiPort()),
                    extraPorts.keySet().stream().collect(Collectors.toMap(portMap::get, extraPorts::get))
            );

            // TODO get internal IP... why is this deprecated?
            // TODO internal IP is not really needed any longer, is it?
            InspectContainerResponse info = dockerClient.inspectContainerCmd(res.getId()).exec();
            dockerContainers.put(containerId, new DockerContainerInfo(res.getId(), info.getNetworkSettings().getIpAddress(), connectivity));

            return connectivity;

        } catch (NotFoundException e) {
            log.warning("Image not found: " + imageName);
            throw new NoSuchElementException("Image not found: " + imageName);
        }
        // TODO handle error trying to connect to Docker (only relevant when Remote Docker is supported, issue #23)
    }

    @Override
    public void stopContainer(String containerId) throws IOException {
        try {
            // remove container info, stop container
            var containerInfo = dockerContainers.remove(containerId);
            dockerClient.stopContainerCmd(containerInfo.containerId).exec();
            // free up ports used by this container
            // TODO do this first, or in finally?
            usedPorts.remove(containerInfo.connectivity.getApiPortMapping());
            usedPorts.removeAll(containerInfo.connectivity.getExtraPortMappings().keySet());
        } catch (NotModifiedException e) {
            var msg = "Could not stop Container " + containerId + "; already stopped?";
            log.warning(msg);
            throw new NoSuchElementException(msg);
        }
        // TODO possibly that the container refuses being stopped? call "kill" instead? how to test this?
        // TODO handle error trying to connect to Docker (only relevant when Remote Docker is supported, issue #23)
    }

    @Override
    public String getIP(String containerId) {
        return dockerContainers.get(containerId).getInternalIp();
    }

    /**
     * Starting from the given preferred port, get and reserve the next free port.
     */
    private int reserveNextFreePort(int port) {
        while (usedPorts.contains(port)) {
            // TODO how to handle ports blocked by other containers or applications? just ping ports?
            port++;
        }
        usedPorts.add(port);
        return port;
    }

    private boolean isImagePresent(String imageName) {
        try {
            this.dockerClient.inspectImageCmd(imageName).exec();
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    /**
     * Get Dict mapping Docker registries to auth credentials from settings.
     * Adapted from EMPAIA Job Execution Service
     */
    private Map<String, AuthConfig> loadDockerAuth() {
        if (config.registryNames.isEmpty()) {
            return Map.of();
        }
        var sep = config.registrySeparator;
        var registries = config.registryNames.split(sep);
        var logins = config.registryLogins.split(sep);
        var passwords = config.registryPasswords.split(sep);

        if (registries.length != logins.length || registries.length != passwords.length) {
            log.warning("Number of Registry Names does not match Login Usernames and Passwords");
            return Map.of();
        } else {
            return IntStream.range(0, registries.length)
                    .mapToObj(i -> new AuthConfig()
                            .withRegistryAddress(registries[i])
                            .withUsername(logins[i])
                            .withPassword(passwords[i]))
                    .collect(Collectors.toMap(AuthConfig::getRegistryAddress, Function.identity()));
        }
    }
}
