package org.wwscc.system.docker.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

/**
 * Manually generated as swagger didn't generate anything for there allOf stuff
 */
@SuppressWarnings("rawtypes")
public class CreateContainerConfig extends ContainerConfig {

  @JsonProperty("HostConfig")
  private HostConfig hostConfig = null;

  public HostConfig getHostConfig() { return hostConfig; }
  public void setHostConfig(HostConfig config) { this.hostConfig = config; }

  @JsonProperty("NetworkingConfig")
  private Map<String, Map<String, Map>> networks = null;  // {'EndpointsConfig': {'scnet': {}}}

  public void setNetwork(String name) {
      networks = new HashMap<String, Map<String, Map>>();
      Map<String, Map> endpoints = new HashMap<String, Map>();
      endpoints.put(name, new HashMap());
      networks.put("EndpointsConfig", endpoints);
  }
}

