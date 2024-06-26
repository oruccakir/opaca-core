package de.gtarc.opaca.model;

import com.fasterxml.jackson.databind.JsonNode;
import de.gtarc.opaca.api.AgentContainerApi;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.List;
import java.util.Map;


/**
 * Description of an Agent Container Image to be started on a Runtime Platform.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class AgentContainerImage {

    // REQUIRED attributes for starting a container

    /** full path of the (Docker) Container, including repository and version */
    @NonNull
    String imageName;

    /** list of required features, e.g. available agents, actions, or platform features */
    @NonNull
    List<String> requires = List.of();

    /** special features provided by this container */
    @NonNull
    List<String> provides = List.of();

    // OPTIONAL attributes for description of the container (e.g. in a repository, or of the container itself)

    /** short readable name of this Agent Container */
    String name;

    /** version number of this image */
    String version;

    /** Optional longer description of what the container does */
    String description;

    /** provider of the container, e.g. institute or researcher */
    String provider;

    // OPTIONAL attributes for API port (if not default) and extra ports (if any)

    /** the port where the container provides the OPACA API; by default this is 8082 but another may be used */
    int apiPort = AgentContainerApi.DEFAULT_PORT;

    /** additional ports exposed by the container and the protocols and services those provide */
    @NonNull
    Map<Integer, PortDescription> extraPorts = Map.of();

    /** additional parameters that get handed down to the container as environment variables */
    @NonNull
    List<ImageParameter> parameters = List.of();

    /** custom type definitions using json schema, maps schema title to schema */
    @NonNull
    Map<String, JsonNode> definitions = Map.of();

    @NonNull
    Map<String, String> definitionsByUrl = Map.of();

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class PortDescription {

        /** the protocol that is served via this port */
        String protocol;

        /** human-readable description of the service */
        String description;

    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class ImageParameter {

        String name;

        String type;

        boolean required = false;

        boolean confidential = false;

        String defaultValue = null;

    }

}
