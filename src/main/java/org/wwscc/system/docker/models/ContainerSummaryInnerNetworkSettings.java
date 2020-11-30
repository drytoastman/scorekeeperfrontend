/*
 * Docker Engine API
 * The Engine API is an HTTP API served by Docker Engine. It is the API the Docker client uses to communicate with the Engine, so everything the Docker client can do can be done with the API.  Most of the client's commands map directly to API endpoints (e.g. `docker ps` is `GET /containers/json`). The notable exception is running containers, which consists of several API calls.  # Errors  The API uses standard HTTP status codes to indicate the success or failure of the API call. The body of the response will be JSON in the following format:  ``` {   \"message\": \"page not found\" } ```  # Versioning  The API is usually changed in each release, so API calls are versioned to ensure that clients don't break. To lock to a specific version of the API, you prefix the URL with its version, for example, call `/v1.30/info` to use the v1.30 version of the `/info` endpoint. If the API version specified in the URL is not supported by the daemon, a HTTP `400 Bad Request` error message is returned.  If you omit the version-prefix, the current version of the API (v1.35) is used. For example, calling `/info` is the same as calling `/v1.35/info`. Using the API without a version-prefix is deprecated and will be removed in a future release.  Engine releases in the near future should support this version of the API, so your client will continue to work even if it is talking to a newer Engine.  The API uses an open schema model, which means server may add extra properties to responses. Likewise, the server will ignore any extra query parameters and request body properties. When you write clients, you need to ignore additional properties in responses to ensure they do not break when talking to newer daemons.   # Authentication  Authentication for registries is handled client side. The client has to send authentication details to various endpoints that need to communicate with registries, such as `POST /images/(name)/push`. These are sent as `X-Registry-Auth` header as a Base64 encoded (JSON) string with the following structure:  ``` {   \"username\": \"string\",   \"password\": \"string\",   \"email\": \"string\",   \"serveraddress\": \"string\" } ```  The `serveraddress` is a domain/IP without a protocol. Throughout this structure, double quotes are required.  If you have already got an identity token from the [`/auth` endpoint](#operation/SystemAuth), you can just pass this instead of credentials:  ``` {   \"identitytoken\": \"9cbaf023786cd7...\" } ```
 *
 * OpenAPI spec version: 1.35
 *
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package org.wwscc.system.docker.models;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.Map;

/**
 * A summary of the container&#39;s network settings
 */
@ApiModel(description = "A summary of the container's network settings")
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaClientCodegen", date = "2018-03-20T16:57:44.859Z")
public class ContainerSummaryInnerNetworkSettings {
  @JsonProperty("Networks")
  private Map<String, EndpointSettings> networks = null;

  public ContainerSummaryInnerNetworkSettings networks(Map<String, EndpointSettings> networks) {
    this.networks = networks;
    return this;
  }

  public ContainerSummaryInnerNetworkSettings putNetworksItem(String key, EndpointSettings networksItem) {
    if (this.networks == null) {
      this.networks = new HashMap<>();
    }
    this.networks.put(key, networksItem);
    return this;
  }

   /**
   * Get networks
   * @return networks
  **/
  @ApiModelProperty(value = "")
  public Map<String, EndpointSettings> getNetworks() {
    return networks;
  }

  public void setNetworks(Map<String, EndpointSettings> networks) {
    this.networks = networks;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ContainerSummaryInnerNetworkSettings containerSummaryInnerNetworkSettings = (ContainerSummaryInnerNetworkSettings) o;
    return Objects.equals(this.networks, containerSummaryInnerNetworkSettings.networks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(networks);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ContainerSummaryInnerNetworkSettings {\n");

    sb.append("    networks: ").append(toIndentedString(networks)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

