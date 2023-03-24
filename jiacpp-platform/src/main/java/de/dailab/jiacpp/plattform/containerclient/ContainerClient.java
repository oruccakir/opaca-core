package de.dailab.jiacpp.plattform.containerclient;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.plattform.PlatformConfig;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Abstract interface for different clients for starting Agent Containers, e.g. on Docker or Kubernetes.
 * This interface is used only by PlatformImpl and can be adapted if needed. Besides keeping PlatformImpl
 * somewhat clean, the main point of this is so that the same PlatformImpl can be used with different
 * container clients, e.g. via configuration.
 */
public interface ContainerClient {

    /**
     * Initialize the client using properties in the given configuration file. Different clients may
     * require different attributes.
     */
    void initialize(PlatformConfig config);

    /**
     * Start a container with the given container ID (for later reference) and image name. If all goes well,
     * return nothing, otherwise raise an appropriate exception.
     *
     * @return Port Mappings
     */
    AgentContainer.Connectivity startContainer(String containerId, AgentContainerImage image) throws IOException, NoSuchElementException;

    /**
     * Stop the agent container with the given ID.
     */
    void stopContainer(String containerId) throws IOException;

    /**
     * Get the IP address where the container can be reached for forwarding requests.
     */
    String getIP(String containerId);

}