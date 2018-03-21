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
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ImageSummary
 */
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaClientCodegen", date = "2018-03-20T16:57:44.859Z")
public class ImageSummary {
  @JsonProperty("Id")
  private String id = null;

  @JsonProperty("ParentId")
  private String parentId = null;

  @JsonProperty("RepoTags")
  private List<String> repoTags = new ArrayList<>();

  @JsonProperty("RepoDigests")
  private List<String> repoDigests = new ArrayList<>();

  @JsonProperty("Created")
  private Integer created = null;

  @JsonProperty("Size")
  private Integer size = null;

  @JsonProperty("SharedSize")
  private Integer sharedSize = null;

  @JsonProperty("VirtualSize")
  private Integer virtualSize = null;

  @JsonProperty("Labels")
  private Map<String, String> labels = new HashMap<>();

  @JsonProperty("Containers")
  private Integer containers = null;

  public ImageSummary id(String id) {
    this.id = id;
    return this;
  }

   /**
   * Get id
   * @return id
  **/
  @ApiModelProperty(required = true, value = "")
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public ImageSummary parentId(String parentId) {
    this.parentId = parentId;
    return this;
  }

   /**
   * Get parentId
   * @return parentId
  **/
  @ApiModelProperty(required = true, value = "")
  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public ImageSummary repoTags(List<String> repoTags) {
    this.repoTags = repoTags;
    return this;
  }

  public ImageSummary addRepoTagsItem(String repoTagsItem) {
    this.repoTags.add(repoTagsItem);
    return this;
  }

   /**
   * Get repoTags
   * @return repoTags
  **/
  @ApiModelProperty(required = true, value = "")
  public List<String> getRepoTags() {
    return repoTags;
  }

  public void setRepoTags(List<String> repoTags) {
    this.repoTags = repoTags;
  }

  public ImageSummary repoDigests(List<String> repoDigests) {
    this.repoDigests = repoDigests;
    return this;
  }

  public ImageSummary addRepoDigestsItem(String repoDigestsItem) {
    this.repoDigests.add(repoDigestsItem);
    return this;
  }

   /**
   * Get repoDigests
   * @return repoDigests
  **/
  @ApiModelProperty(required = true, value = "")
  public List<String> getRepoDigests() {
    return repoDigests;
  }

  public void setRepoDigests(List<String> repoDigests) {
    this.repoDigests = repoDigests;
  }

  public ImageSummary created(Integer created) {
    this.created = created;
    return this;
  }

   /**
   * Get created
   * @return created
  **/
  @ApiModelProperty(required = true, value = "")
  public Integer getCreated() {
    return created;
  }

  public void setCreated(Integer created) {
    this.created = created;
  }

  public ImageSummary size(Integer size) {
    this.size = size;
    return this;
  }

   /**
   * Get size
   * @return size
  **/
  @ApiModelProperty(required = true, value = "")
  public Integer getSize() {
    return size;
  }

  public void setSize(Integer size) {
    this.size = size;
  }

  public ImageSummary sharedSize(Integer sharedSize) {
    this.sharedSize = sharedSize;
    return this;
  }

   /**
   * Get sharedSize
   * @return sharedSize
  **/
  @ApiModelProperty(required = true, value = "")
  public Integer getSharedSize() {
    return sharedSize;
  }

  public void setSharedSize(Integer sharedSize) {
    this.sharedSize = sharedSize;
  }

  public ImageSummary virtualSize(Integer virtualSize) {
    this.virtualSize = virtualSize;
    return this;
  }

   /**
   * Get virtualSize
   * @return virtualSize
  **/
  @ApiModelProperty(required = true, value = "")
  public Integer getVirtualSize() {
    return virtualSize;
  }

  public void setVirtualSize(Integer virtualSize) {
    this.virtualSize = virtualSize;
  }

  public ImageSummary labels(Map<String, String> labels) {
    this.labels = labels;
    return this;
  }

  public ImageSummary putLabelsItem(String key, String labelsItem) {
    this.labels.put(key, labelsItem);
    return this;
  }

   /**
   * Get labels
   * @return labels
  **/
  @ApiModelProperty(required = true, value = "")
  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }

  public ImageSummary containers(Integer containers) {
    this.containers = containers;
    return this;
  }

   /**
   * Get containers
   * @return containers
  **/
  @ApiModelProperty(required = true, value = "")
  public Integer getContainers() {
    return containers;
  }

  public void setContainers(Integer containers) {
    this.containers = containers;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ImageSummary imageSummary = (ImageSummary) o;
    return Objects.equals(this.id, imageSummary.id) &&
        Objects.equals(this.parentId, imageSummary.parentId) &&
        Objects.equals(this.repoTags, imageSummary.repoTags) &&
        Objects.equals(this.repoDigests, imageSummary.repoDigests) &&
        Objects.equals(this.created, imageSummary.created) &&
        Objects.equals(this.size, imageSummary.size) &&
        Objects.equals(this.sharedSize, imageSummary.sharedSize) &&
        Objects.equals(this.virtualSize, imageSummary.virtualSize) &&
        Objects.equals(this.labels, imageSummary.labels) &&
        Objects.equals(this.containers, imageSummary.containers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, parentId, repoTags, repoDigests, created, size, sharedSize, virtualSize, labels, containers);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ImageSummary {\n");

    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    parentId: ").append(toIndentedString(parentId)).append("\n");
    sb.append("    repoTags: ").append(toIndentedString(repoTags)).append("\n");
    sb.append("    repoDigests: ").append(toIndentedString(repoDigests)).append("\n");
    sb.append("    created: ").append(toIndentedString(created)).append("\n");
    sb.append("    size: ").append(toIndentedString(size)).append("\n");
    sb.append("    sharedSize: ").append(toIndentedString(sharedSize)).append("\n");
    sb.append("    virtualSize: ").append(toIndentedString(virtualSize)).append("\n");
    sb.append("    labels: ").append(toIndentedString(labels)).append("\n");
    sb.append("    containers: ").append(toIndentedString(containers)).append("\n");
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
